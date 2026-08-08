package com.sgkrashi.cropdoctor.service;

import com.sgkrashi.cropdoctor.dto.response.AiPredictionResponse;
import org.springframework.web.multipart.MultipartFile;

public interface AiServiceClient {

    /**
     * Sends the image to the Python AI service and returns a validated
     * prediction.
     *
     * @throws com.sgkrashi.cropdoctor.exception.AiServiceUnavailableException
     *         if the service is unreachable, times out, returns a non-2xx
     *         response, or returns a response that doesn't match the
     *         expected shape.
     */
    AiPredictionResponse predict(MultipartFile file);
}
