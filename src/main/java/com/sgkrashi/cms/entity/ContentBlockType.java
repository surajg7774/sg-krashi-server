package com.sgkrashi.cms.entity;

/**
 * What a {@link ContentBlock}'s {@code payloadJson} shape is. BANNER needs
 * {@code {title, subtitle, imageUrl, ctaText, ctaLink}}; TESTIMONIAL needs
 * {@code {authorName, quote, rating?}} — enforced as real TypeScript types on
 * the frontend, kept loosely-typed JSON here (Module 17 scope explicitly
 * avoids a separate table per type).
 */
public enum ContentBlockType {
    BANNER,
    TESTIMONIAL,
    PROMO
}
