package com.sgkrashi.cropdoctor.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.cropdoctor.dto.response.CropScanReport;
import com.sgkrashi.cropdoctor.dto.response.CropScanResponse;
import com.sgkrashi.cropdoctor.dto.response.CropScanSummaryResponse;
import com.sgkrashi.cropdoctor.service.CropDoctorService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai/crop-doctor")
public class CropDoctorController {

    private final CropDoctorService cropDoctorService;

    public CropDoctorController(CropDoctorService cropDoctorService) {
        this.cropDoctorService = cropDoctorService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CropScanResponse>> analyze(@RequestParam("file") MultipartFile file) {
        CropScanResponse response = cropDoctorService.analyze(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Scan complete"));
    }

    @GetMapping("/scans")
    public ResponseEntity<ApiResponse<PaginatedResponse<CropScanSummaryResponse>>> listMyScans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(cropDoctorService.listMyScans(page, size), "Scans retrieved"));
    }

    @GetMapping("/scans/{id}")
    public ResponseEntity<ApiResponse<CropScanResponse>> getScanDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(cropDoctorService.getScanDetail(id), "Scan retrieved"));
    }

    @DeleteMapping("/scans/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteScan(@PathVariable Long id) {
        cropDoctorService.deleteScan(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Scan deleted"));
    }

    @GetMapping("/scans/{id}/report")
    public ResponseEntity<byte[]> getReport(@PathVariable Long id) {
        CropScanReport report = cropDoctorService.getReport(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(report.filename())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(report.bytes());
    }
}
