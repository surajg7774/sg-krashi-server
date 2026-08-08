package com.sgkrashi.cropdoctor.service;

import com.sgkrashi.cropdoctor.entity.CropScan;

public interface CropScanReportService {

    /**
     * Renders a one-page PDF report for the given scan, generated fresh at
     * request time (never pre-generated/stored).
     */
    byte[] generateReport(CropScan scan);
}
