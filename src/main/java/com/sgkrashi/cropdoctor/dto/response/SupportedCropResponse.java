package com.sgkrashi.cropdoctor.dto.response;

public record SupportedCropResponse(String cropName, boolean hasLimitedCoverage, String coverageNote) {
}
