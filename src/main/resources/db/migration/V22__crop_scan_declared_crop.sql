-- V22__crop_scan_declared_crop.sql
-- Crop cross-check honesty fix: a confirmed production finding showed the
-- model can confidently (99.71%) mispredict both crop AND health status on
-- out-of-distribution photos (e.g. a real diseased soybean leaf predicted as
-- "Cherry - Healthy") — confidence alone doesn't catch this failure mode.
-- Letting the user declare the crop they're photographing, and flagging a
-- mismatch independent of confidence, is the mitigation (see
-- CropDoctorServiceImpl).

ALTER TABLE crop_scans
    ADD COLUMN declared_crop VARCHAR(100) NULL AFTER user_id,
    ADD COLUMN crop_mismatch BOOLEAN NOT NULL DEFAULT FALSE AFTER is_uncertain;
