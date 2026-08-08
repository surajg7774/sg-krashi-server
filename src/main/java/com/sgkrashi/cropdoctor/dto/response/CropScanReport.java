package com.sgkrashi.cropdoctor.dto.response;

/** Rendered PDF bytes plus the filename the controller should serve them as. */
public record CropScanReport(byte[] bytes, String filename) {
}
