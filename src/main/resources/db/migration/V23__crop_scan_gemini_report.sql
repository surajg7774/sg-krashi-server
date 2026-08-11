-- V23__crop_scan_gemini_report.sql
-- Phase 1: Gemini replaces the fixed-class classifier as the active
-- analysis provider. The old flat crop_name/disease_name/confidence_score/
-- severity/recommendation columns stay exactly as they are (nullable now,
-- were not-null before) so existing scan history keeps rendering — new
-- scans store their full structured report in report_json instead, which
-- CropScanMapper reads from when present. See CropDoctorServiceImpl and
-- CropScanMapper for how old vs. new rows are normalized to one response
-- shape.

ALTER TABLE crop_scans
    MODIFY COLUMN disease_name VARCHAR(150) NULL,
    MODIFY COLUMN confidence_score DECIMAL(5,4) NULL,
    MODIFY COLUMN recommendation TEXT NULL;

ALTER TABLE crop_scans
    ADD COLUMN image_urls TEXT NULL AFTER image_url,
    ADD COLUMN language VARCHAR(10) NULL AFTER declared_crop,
    ADD COLUMN confidence_band VARCHAR(20) NULL AFTER confidence_score,
    ADD COLUMN provider_name VARCHAR(50) NULL AFTER model_version,
    ADD COLUMN report_json LONGTEXT NULL AFTER recommendation;
