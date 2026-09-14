package com.sgkrashi.advisory.rule;

/** One triggered advisory — general guidance, not personalized agronomic advice (see {@code AdvisoryRules}' Javadoc). */
public record Advisory(String title, String message) {
}
