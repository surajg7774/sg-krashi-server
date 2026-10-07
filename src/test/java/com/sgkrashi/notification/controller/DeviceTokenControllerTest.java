package com.sgkrashi.notification.controller;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.exception.GlobalExceptionHandler;
import com.sgkrashi.notification.service.DeviceTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeviceTokenControllerTest {

    private final DeviceTokenService service = mock(DeviceTokenService.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new DeviceTokenController(service, currentUser))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build()))
            .build();

    @Test
    void unregisterByBodyRemovesOnlyTheCallersOwnToken() throws Exception {
        when(currentUser.getCurrentUserId()).thenReturn(42L);

        mvc.perform(post("/api/v1/notifications/device-tokens/unregister")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).unregisterForUser(42L, "abc123");
    }

    @Test
    void aBlankOrMissingTokenIsA400AndNothingIsDeleted() throws Exception {
        mvc.perform(post("/api/v1/notifications/device-tokens/unregister")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/notifications/device-tokens/unregister")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/notifications/device-tokens/unregister"))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(service);
    }

    @Test
    void theLegacyDeleteEndpointStillWorksUnchangedForInstalledApps() throws Exception {
        mvc.perform(delete("/api/v1/notifications/device-tokens").param("token", "legacy-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Device token unregistered"));

        verify(service).unregister("legacy-token");
    }
}
