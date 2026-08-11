-- V24__crop_scan_disease_name_text.sql
-- disease_name was VARCHAR(150), sized for the old fixed-class classifier's
-- short labels (e.g. "Late blight"). Gemini's problem descriptions vary
-- widely in length and aren't bounded by a fixed label set — one real
-- response during Guest Access testing already exceeded 150 chars and
-- failed the insert with a truncation error. Widened to TEXT rather than a
-- larger VARCHAR since there's no principled cap to guess for free-form
-- generated text.

ALTER TABLE crop_scans
    MODIFY COLUMN disease_name TEXT NULL;
