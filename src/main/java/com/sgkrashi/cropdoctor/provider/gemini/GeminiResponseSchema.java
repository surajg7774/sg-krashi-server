package com.sgkrashi.cropdoctor.provider.gemini;

/**
 * The strict JSON schema passed as Gemini's {@code generationConfig.responseSchema}
 * (Section 3.3 of the feature spec) — this is what keeps the report "structured
 * cards," not "raw AI text": Gemini is constrained to return exactly this
 * shape, never free-form prose to parse and hope matches.
 *
 * <p>Gemini's schema format is a restricted subset of the OpenAPI Schema
 * Object (uppercase type names: OBJECT/STRING/ARRAY/BOOLEAN).
 */
final class GeminiResponseSchema {

    private GeminiResponseSchema() {
    }

    static final String JSON = """
            {
              "type": "OBJECT",
              "properties": {
                "identifiedCrop": { "type": "STRING" },
                "cropMatchesDeclared": { "type": "BOOLEAN" },
                "healthStatus": { "type": "STRING", "enum": ["HEALTHY", "DISEASED", "UNCERTAIN"] },
                "problem": { "type": "STRING", "nullable": true },
                "pathogenScientificName": { "type": "STRING", "nullable": true },
                "confidenceBand": { "type": "STRING", "enum": ["HIGH", "MODERATE", "LOW"] },
                "severity": { "type": "STRING", "enum": ["MILD", "MODERATE", "SEVERE"], "nullable": true },
                "symptoms": { "type": "ARRAY", "items": { "type": "STRING" } },
                "possibleCauses": { "type": "ARRAY", "items": { "type": "STRING" } },
                "environmentalFactors": { "type": "ARRAY", "items": { "type": "STRING" } },
                "actionsNow": { "type": "ARRAY", "items": { "type": "STRING" } },
                "prevention": { "type": "ARRAY", "items": { "type": "STRING" } },
                "monitoringGuidance": { "type": "STRING" },
                "warningSignsToEscalate": { "type": "ARRAY", "items": { "type": "STRING" } },
                "limitations": { "type": "STRING" }
              },
              "required": [
                "identifiedCrop", "cropMatchesDeclared", "healthStatus", "confidenceBand",
                "symptoms", "possibleCauses", "environmentalFactors", "actionsNow",
                "prevention", "monitoringGuidance", "warningSignsToEscalate", "limitations"
              ]
            }
            """;
}
