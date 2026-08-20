package com.sgkrashi.chatassistant.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sgkrashi.chatassistant.exception.ChatAssistantUnavailableException;
import com.sgkrashi.chatassistant.exception.ChatQuotaExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * The only {@link ChatAssistantProvider} implementation for V1 — mirrors
 * {@code com.sgkrashi.cropdoctor.provider.gemini.GeminiAnalysisProvider}'s
 * WebClient/error-handling pattern, but calls plain {@code generateContent}
 * for free-text output rather than the structured-JSON-schema variant crop
 * analysis uses (a chat reply has no fixed shape to validate against).
 *
 * <p><b>Security note, since this is the class that actually sees whatever
 * personal data was fetched:</b> this class never fetches anything itself
 * and never receives a user identifier — {@code groundingContext}/{@code
 * personalDataContext} arrive as plain, already-scoped strings built by
 * {@code ChatServiceImpl} before this is ever called. There is no code path
 * here, or anywhere in this class, that could turn a phrase in {@code
 * newUserMessage} into a database lookup — this class doesn't have a
 * database connection at all.
 */
@Component
public class GeminiChatProvider implements ChatAssistantProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiChatProvider.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final String SYSTEM_INSTRUCTION = """
            You are the SG Krashi support assistant, helping users of an agricultural platform with six \
            business lines: Product Store, Crop Marketplace, Equipment Rental, Farm Stay, Organic Farming, \
            and Dairy Farm. You answer questions about how the platform works, and, when the user is logged \
            in, questions about their own account activity.

            Be honest, concise, and conversational. This is customer support, not a creative task: if you \
            don't have enough information below to answer confidently — especially specific numbers like \
            prices, cancellation windows, or policy details — say so plainly and suggest contacting support \
            at sgkrashi@gmail.com, rather than guessing or inventing specifics. Never claim to have access to \
            information about any user other than the one you're currently talking to.""";

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public GeminiChatProvider(
            @Value("${app.gemini.api-key}") String apiKey,
            @Value("${app.gemini.chat-model:gemini-3.6-flash}") String model,
            @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String reply(
            List<ChatTurn> history,
            String newUserMessage,
            String groundingContext,
            String personalDataContext,
            boolean guestAskedPersonalData
    ) {
        ObjectNode payload = buildPayload(history, newUserMessage, groundingContext, personalDataContext, guestAskedPersonalData);

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
                log.warn("Gemini chat quota exhausted: {}", ex.getResponseBodyAsString());
                throw new ChatQuotaExceededException(
                        "The chat assistant has reached its usage limit for now — please try again later.", ex);
            }
            log.warn("Gemini chat API returned {} {}: {}", ex.getStatusCode(), ex.getStatusText(), ex.getResponseBodyAsString());
            throw new ChatAssistantUnavailableException("Chat service returned an error response", ex);
        } catch (WebClientRequestException ex) {
            log.warn("Gemini chat API unreachable: {}", ex.getMessage());
            throw new ChatAssistantUnavailableException("Chat service is unreachable", ex);
        } catch (Exception ex) {
            log.warn("Gemini chat API call failed", ex);
            throw new ChatAssistantUnavailableException("Chat service call failed", ex);
        }

        return parseReply(responseBody);
    }

    private ObjectNode buildPayload(
            List<ChatTurn> history,
            String newUserMessage,
            String groundingContext,
            String personalDataContext,
            boolean guestAskedPersonalData
    ) {
        ObjectNode systemInstruction = objectMapper.createObjectNode();
        systemInstruction.set("parts", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("text", buildSystemInstructionText(
                        groundingContext, personalDataContext, guestAskedPersonalData))));

        ArrayNode contents = objectMapper.createArrayNode();
        for (ChatTurn turn : history) {
            contents.add(turnNode(turn.role(), turn.content()));
        }
        contents.add(turnNode("user", newUserMessage));

        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("systemInstruction", systemInstruction);
        payload.set("contents", contents);
        return payload;
    }

    private ObjectNode turnNode(String role, String text) {
        // Gemini's multi-turn convention: "user" and "model" (NOT
        // "assistant", unlike OpenAI/Anthropic-style APIs) — ChatTurn.role
        // is stored/passed through as "assistant" everywhere else in this
        // feature (matching the ChatMessageRole entity enum's naming), so
        // it's translated here, at the one place that actually talks to
        // Gemini's wire format.
        String geminiRole = "assistant".equals(role) ? "model" : "user";
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", geminiRole);
        node.set("parts", objectMapper.createArrayNode().add(objectMapper.createObjectNode().put("text", text)));
        return node;
    }

    private String buildSystemInstructionText(String groundingContext, String personalDataContext, boolean guestAskedPersonalData) {
        StringBuilder text = new StringBuilder(SYSTEM_INSTRUCTION);

        if (groundingContext != null && !groundingContext.isBlank()) {
            text.append("\n\nReference information from platform documentation (prefer this where it's relevant "
                    + "to the question; do not invent specific numbers or policy details beyond what's here):\n\n")
                    .append(groundingContext);
        }

        if (personalDataContext != null && !personalDataContext.isBlank()) {
            text.append("\n\nThe following is this specific logged-in user's OWN real account data, already "
                    + "looked up for them by the backend. Answer using only this data — never claim details "
                    + "about any other user, and never state specific order/booking facts not shown here:\n\n")
                    .append(personalDataContext);
        }

        if (guestAskedPersonalData) {
            text.append("\n\nThis user is not logged in. Their message appears to ask about their own orders, "
                    + "bookings, or account — that requires being logged in. Explain clearly that they need to "
                    + "log in or create an account to see that information; do not guess or answer as if you "
                    + "had access to any personal data for them.");
        }

        return text.toString();
    }

    private String parseReply(String responseBody) {
        try {
            GeminiChatApiResponse response = objectMapper.readValue(responseBody, GeminiChatApiResponse.class);
            String text = response.candidates().get(0).content().parts().get(0).text();
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("Empty reply text");
            }
            return text;
        } catch (Exception ex) {
            log.warn("Could not parse Gemini chat response: {}", responseBody, ex);
            throw new ChatAssistantUnavailableException("Chat service returned an invalid response", ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiChatApiResponse(List<Candidate> candidates) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Candidate(Content content) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Content(List<Part> parts) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Part(String text) {
        }
    }
}
