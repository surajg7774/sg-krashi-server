package com.sgkrashi.review.entity;

/**
 * What a {@link Review} is about. Mirrors {@code BookableType}/
 * {@code InquiryModuleType}'s enum-discriminator style rather than
 * {@code MediaAsset.ownerType}'s plain-string style — the type safety is
 * worth it here since, unlike media (any owner), a review's eligibility
 * logic branches by exactly these four values.
 */
public enum ReviewTargetType {
    PRODUCT,
    CROP_LISTING,
    EQUIPMENT,
    STAY
}
