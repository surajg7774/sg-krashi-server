package com.sgkrashi.cropdoctor.provider.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sgkrashi.cropdoctor.exception.AiQuotaExceededException;
import com.sgkrashi.cropdoctor.exception.AiServiceUnavailableException;
import com.sgkrashi.cropdoctor.provider.ConfidenceBand;
import com.sgkrashi.cropdoctor.provider.CropAnalysisProvider;
import com.sgkrashi.cropdoctor.provider.CropAnalysisResult;
import com.sgkrashi.cropdoctor.provider.HealthStatus;
import com.sgkrashi.cropdoctor.provider.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The active V1 {@link CropAnalysisProvider}. Replaces the fixed-class
 * PlantVillage classifier — validated in Phase 0 against real photos
 * including the confirmed soybean-rust failure case (old model: "Cherry -
 * Healthy" at 99.71%; Gemini: correctly identified Soybean Rust with the
 * specific pathogen and genuinely useful agronomic detail).
 */
@Component
@ConditionalOnProperty(prefix = "crop-doctor", name = "provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiAnalysisProvider implements CropAnalysisProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiAnalysisProvider.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    private static final String PROVIDER_NAME = "gemini";
    // Plenty for disease/pest detection on a leaf photo — meaningfully cuts
    // upload size and Gemini processing time for a full-resolution phone
    // photo (often 3000px+ on the long side) without losing diagnostic detail.
    private static final int MAX_DIMENSION_PX = 1536;
    private static final float JPEG_QUALITY = 0.85f;

    private static final Map<String, String> LANGUAGE_NAMES = Map.of(
            "en", "English",
            "hi", "Hindi",
            "mr", "Marathi",
            "gu", "Gujarati"
    );

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final JsonNode responseSchema;

    public GeminiAnalysisProvider(
            @Value("${app.gemini.api-key}") String apiKey,
            @Value("${app.gemini.model:gemini-3.6-flash}") String model,
            @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        try {
            this.responseSchema = objectMapper.readTree(GeminiResponseSchema.JSON);
        } catch (IOException ex) {
            // The schema is a compile-time constant, not user/config input — a
            // parse failure here means the constant itself is broken and every
            // request would fail identically, so fail at startup, not per-request.
            throw new IllegalStateException("Failed to parse the built-in Gemini response schema", ex);
        }
    }

    @Override
    public CropAnalysisResult analyze(List<MultipartFile> images, String declaredCrop, String language) {
        ObjectNode payload = buildPayload(images, declaredCrop, language);

        String responseBody;
        try {
            responseBody = webClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatusCode.valueOf(429)) {
                log.warn("Gemini quota exhausted: {}", ex.getResponseBodyAsString());
                throw new AiQuotaExceededException(
                        "AI Crop Doctor has reached its daily analysis limit — please try again tomorrow.", ex);
            }
            log.warn("Gemini API returned {} {}: {}", ex.getStatusCode(), ex.getStatusText(),
                    ex.getResponseBodyAsString());
            throw new AiServiceUnavailableException("AI service returned an error response", ex);
        } catch (WebClientRequestException ex) {
            log.warn("Gemini API unreachable: {}", ex.getMessage());
            throw new AiServiceUnavailableException("AI service is unreachable", ex);
        } catch (Exception ex) {
            log.warn("Gemini API call failed", ex);
            throw new AiServiceUnavailableException("AI service call failed", ex);
        }

        return parseAndValidate(responseBody, declaredCrop);
    }

    private ObjectNode buildPayload(List<MultipartFile> images, String declaredCrop, String language) {
        ArrayNode parts = objectMapper.createArrayNode();
        parts.add(objectMapper.createObjectNode().put("text", buildPrompt(declaredCrop, language, images.size())));

        for (MultipartFile image : images) {
            ObjectNode inlineData = objectMapper.createObjectNode();
            try {
                ResizedImage resized = resizeForAnalysis(image);
                inlineData.put("mime_type", resized.mimeType());
                inlineData.put("data", Base64.getEncoder().encodeToString(resized.bytes()));
            } catch (IOException ex) {
                throw new AiServiceUnavailableException("Could not read uploaded image for analysis", ex);
            }
            ObjectNode imagePart = objectMapper.createObjectNode();
            imagePart.set("inline_data", inlineData);
            parts.add(imagePart);
        }

        ObjectNode content = objectMapper.createObjectNode();
        content.set("parts", parts);

        ObjectNode generationConfig = objectMapper.createObjectNode();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.set("responseSchema", responseSchema.deepCopy());

        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("contents", objectMapper.createArrayNode().add(content));
        payload.set("generationConfig", generationConfig);
        return payload;
    }

    /**
     * Downscales/re-encodes only the copy sent to Gemini — never touches what
     * {@code CropDoctorServiceImpl} stores separately for an authenticated
     * user's history. A full-resolution phone photo (often several MB,
     * 3000px+ on the long side) adds real upload/processing latency for no
     * disease-detection benefit past {@link #MAX_DIMENSION_PX}; images
     * already at or under that size are sent unchanged to avoid a pointless
     * re-encode.
     */
    private ResizedImage resizeForAnalysis(MultipartFile image) throws IOException {
        byte[] originalBytes = image.getBytes();
        BufferedImage source;
        try (ByteArrayInputStream in = new ByteArrayInputStream(originalBytes)) {
            source = ImageIO.read(in);
        }
        if (source == null || Math.max(source.getWidth(), source.getHeight()) <= MAX_DIMENSION_PX) {
            // Not a decodable image (let Gemini itself reject it), or already
            // small enough — pass the original bytes/mime-type through as-is.
            return new ResizedImage(originalBytes, image.getContentType());
        }

        double scale = (double) MAX_DIMENSION_PX / Math.max(source.getWidth(), source.getHeight());
        int scaledWidth = (int) Math.round(source.getWidth() * scale);
        int scaledHeight = (int) Math.round(source.getHeight() * scale);

        // TYPE_INT_RGB (no alpha) + a white fill under the scaled draw, so a
        // transparent PNG flattens to white instead of black when re-encoded
        // as JPEG below.
        BufferedImage scaled = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, scaledWidth, scaledHeight);
        g.drawImage(source, 0, 0, scaledWidth, scaledHeight, null);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        writeParam.setCompressionQuality(JPEG_QUALITY);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(scaled, null, null), writeParam);
        } finally {
            writer.dispose();
        }

        byte[] resizedBytes = out.toByteArray();
        log.debug("Resized image for Gemini: {}x{} ({} bytes) -> {}x{} ({} bytes)",
                source.getWidth(), source.getHeight(), originalBytes.length,
                scaledWidth, scaledHeight, resizedBytes.length);
        return new ResizedImage(resizedBytes, MediaType.IMAGE_JPEG_VALUE);
    }

    private record ResizedImage(byte[] bytes, String mimeType) {
    }

    private String buildPrompt(String declaredCrop, String languageCode, int imageCount) {
        String multiImageNote = imageCount > 1
                ? "You have been given " + imageCount + " photos of the same plant, taken from different "
                + "angles/distances — use all of them together as evidence for one diagnosis, not separate ones.\n\n"
                : "";

        return """
                You are an agricultural assistant helping identify plant health issues from photos taken by \
                farmers in real field conditions (not lab photos). %s\
                The user has indicated this photo is of: %s. If your own visual identification disagrees with \
                this, set cropMatchesDeclared to false and explain the disagreement in your problem/limitations \
                text, rather than silently overriding the user's declaration or silently agreeing with it.

                %s

                Be honest about uncertainty. If the image is not a plant, or you cannot make a reliable diagnosis, \
                say so clearly via healthStatus=UNCERTAIN and confidenceBand=LOW rather than guessing or inventing \
                a plausible-sounding diagnosis. Do not fabricate specific causes/actions/prevention if you are not \
                actually confident in the diagnosis — say so honestly in limitations instead. limitations must \
                always be populated, even when the diagnosis is confident (state what a photo alone still can't \
                confirm).

                Return your analysis as JSON matching the required schema exactly.""".formatted(
                multiImageNote, declaredCrop, buildLanguageInstruction(languageCode));
    }

    /**
     * Hinglish is a genuinely distinct style, not a shortcut version of
     * Hindi — it needs its own explicit instruction rather than falling
     * through to "write in [language name]", or Gemini tends to produce
     * either formal Hindi transliterated into Roman script or English with
     * a few Hindi words sprinkled in, neither of which is what was asked for.
     */
    private String buildLanguageInstruction(String languageCode) {
        if ("hinglish".equals(languageCode)) {
            return """
                    Write your entire response in natural, colloquial Hinglish — Hindi-English code-mixed \
                    language written in Roman/English script, exactly the way many Indian farmers and everyday \
                    users actually text and read (for example: "Yeh disease aapke plant ko damage kar sakti \
                    hai, isliye turant fungicide spray karein"). This is NOT formal Hindi transliterated into \
                    Roman script, and NOT English with occasional Hindi words sprinkled in — it should read \
                    like natural spoken Hinglish throughout. For any disease or pathogen name, still include \
                    the scientific/English name alongside the everyday name (in pathogenScientificName) so the \
                    farmer can show it to an agri-dealer or extension officer unambiguously.""";
        }
        String languageName = LANGUAGE_NAMES.getOrDefault(languageCode, "English");
        return """
                Write your entire response in %s. Naturally, as it would be written for a farmer who speaks \
                that language — not a machine translation. For any disease or pathogen name, include the \
                scientific/English name alongside the localized name (in pathogenScientificName) so the \
                farmer can show it to an agri-dealer or extension officer unambiguously.""".formatted(languageName);
    }

    private CropAnalysisResult parseAndValidate(String responseBody, String declaredCrop) {
        GeminiReportJson report;
        try {
            GeminiApiResponse apiResponse = objectMapper.readValue(responseBody, GeminiApiResponse.class);
            String text = apiResponse.candidates().get(0).content().parts().get(0).text();
            report = objectMapper.readValue(text, GeminiReportJson.class);
        } catch (Exception ex) {
            log.warn("Could not parse Gemini response: {}", responseBody, ex);
            throw new AiServiceUnavailableException("AI service returned an invalid response", ex);
        }

        if (report.identifiedCrop() == null || report.identifiedCrop().isBlank()
                || report.healthStatus() == null
                || report.confidenceBand() == null
                || report.limitations() == null || report.limitations().isBlank()) {
            log.warn("Gemini response failed shape validation: {}", report);
            throw new AiServiceUnavailableException("AI service returned an incomplete response");
        }

        HealthStatus healthStatus = parseEnumSafely(HealthStatus.class, report.healthStatus(), HealthStatus.UNCERTAIN);
        ConfidenceBand confidenceBand = parseEnumSafely(ConfidenceBand.class, report.confidenceBand(), ConfidenceBand.LOW);
        Severity severity = report.severity() == null ? null : parseEnumSafely(Severity.class, report.severity(), null);

        boolean cropMatchesDeclared = report.cropMatchesDeclared() != null
                ? report.cropMatchesDeclared()
                : declaredCrop.equalsIgnoreCase(report.identifiedCrop());

        return new CropAnalysisResult(
                report.identifiedCrop(),
                cropMatchesDeclared,
                healthStatus,
                report.problem(),
                report.pathogenScientificName(),
                confidenceBand,
                severity,
                emptyIfNull(report.symptoms()),
                emptyIfNull(report.possibleCauses()),
                emptyIfNull(report.environmentalFactors()),
                emptyIfNull(report.actionsNow()),
                emptyIfNull(report.prevention()),
                report.monitoringGuidance(),
                emptyIfNull(report.warningSignsToEscalate()),
                report.limitations(),
                PROVIDER_NAME,
                model
        );
    }

    private <E extends Enum<E>> E parseEnumSafely(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("Gemini returned unrecognized {} value '{}', defaulting to {}", type.getSimpleName(), value, fallback);
            return fallback;
        }
    }

    private List<String> emptyIfNull(List<String> list) {
        return list == null ? List.of() : list;
    }
}
