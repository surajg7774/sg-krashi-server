package com.sgkrashi.cropdoctor.provider;

/**
 * Deliberately not a percentage. A generative provider's self-reported
 * certainty is not a calibrated probability the way the old classifier's
 * softmax output was — rendering it as a fake decimal ("94.2%") would be
 * more dishonest than useful. See sg-krashi-ai-service's Phase 0 findings.
 */
public enum ConfidenceBand {
    HIGH, MODERATE, LOW
}
