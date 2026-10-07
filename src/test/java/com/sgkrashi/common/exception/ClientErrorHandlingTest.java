package com.sgkrashi.common.exception;

import com.sgkrashi.cropdoctor.controller.CropDoctorController;
import com.sgkrashi.cropdoctor.service.CropDoctorService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Framework "the client sent something wrong" exceptions used to fall through to
 * the catch-all 500 (and an ERROR stack trace). Each must now be a clean 4xx in
 * the standard error shape, through the real controller + the real advice.
 */
class ClientErrorHandlingTest {

    private final CropDoctorService cropDoctorService = mock(CropDoctorService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new CropDoctorController(cropDoctorService), new TypedProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            // Spring's own ObjectMapper builder registers the java.time module the error body's Instant needs.
            .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build()))
            .build();

    @Test
    void cropDoctorAnalyzeWithNoFilesIsA400NotA500() throws Exception {
        mvc.perform(multipart("/api/v1/ai/crop-doctor/analyze").param("declaredCrop", "Tomato"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Missing required part: files"));
        verifyNoInteractions(cropDoctorService);
    }

    @Test
    void cropDoctorAnalyzeWithNoContentTypeIsA415() throws Exception {
        mvc.perform(post("/api/v1/ai/crop-doctor/analyze"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void aWrongContentTypeOnAJsonEndpointIsA415() throws Exception {
        mvc.perform(post("/probe/json").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void aWrongHttpMethodIsA405WithAnAllowHeader() throws Exception {
        mvc.perform(put("/probe/json"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void aNonNumericPathValueIsA400AndTheValueIsNotEchoed() throws Exception {
        mvc.perform(get("/probe/items/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Invalid value for parameter: id"));
    }

    @Test
    void aRealMultipartRequestStillReachesTheService() throws Exception {
        // Guards against the new handlers swallowing a valid request: with a file present, the
        // (mocked) service is called and nothing here turns into a 4xx.
        MockMultipartFile file = new MockMultipartFile("files", "leaf.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/v1/ai/crop-doctor/analyze").file(file).param("declaredCrop", "Tomato"))
                .andExpect(status().isCreated());
    }

    /** Minimal endpoints standing in for "a JSON POST endpoint" and "a typed path variable". */
    @RestController
    @RequestMapping("/probe")
    static class TypedProbeController {
        @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
        public String json() {
            return "ok";
        }

        @GetMapping("/items/{id}")
        public String item(@PathVariable Long id) {
            return "ok";
        }
    }
}
