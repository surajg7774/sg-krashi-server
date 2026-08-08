package com.sgkrashi.cropdoctor.mapper;

import com.sgkrashi.cropdoctor.dto.response.CropScanResponse;
import com.sgkrashi.cropdoctor.dto.response.CropScanSummaryResponse;
import com.sgkrashi.cropdoctor.entity.CropScan;
import org.springframework.stereotype.Component;

@Component
public class CropScanMapper {

    public CropScanResponse toResponse(CropScan scan) {
        return new CropScanResponse(
                scan.getId(),
                scan.getImageUrl(),
                scan.getCropName(),
                scan.getDiseaseName(),
                scan.getConfidenceScore(),
                scan.getSeverity(),
                scan.getRecommendation(),
                scan.getModelVersion(),
                scan.isUncertain(),
                scan.getCreatedAt()
        );
    }

    public CropScanSummaryResponse toSummaryResponse(CropScan scan) {
        return new CropScanSummaryResponse(
                scan.getId(),
                scan.getImageUrl(),
                scan.getCropName(),
                scan.getDiseaseName(),
                scan.getConfidenceScore(),
                scan.isUncertain(),
                scan.getCreatedAt()
        );
    }
}
