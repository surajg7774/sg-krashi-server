package com.sgkrashi.inquiry.entity;

/**
 * What an {@link Inquiry} is about. Mirrors the {@code BookableType} pattern
 * (Module 8/9): adding a new module's inquiry support is meant to be exactly
 * one new constant here, with no other structural change — see Module 11
 * (Dairy Farm), which should only need {@code DAIRY_FARM} added.
 */
public enum InquiryModuleType {
    GENERAL,
    ORGANIC_FARMING
}
