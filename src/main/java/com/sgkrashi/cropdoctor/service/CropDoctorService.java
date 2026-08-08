package com.sgkrashi.cropdoctor.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.cropdoctor.dto.response.CropScanReport;
import com.sgkrashi.cropdoctor.dto.response.CropScanResponse;
import com.sgkrashi.cropdoctor.dto.response.CropScanSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

public interface CropDoctorService {

    CropScanResponse analyze(MultipartFile file);

    PaginatedResponse<CropScanSummaryResponse> listMyScans(int page, int size);

    CropScanResponse getScanDetail(Long scanId);

    void deleteScan(Long scanId);

    CropScanReport getReport(Long scanId);
}
