package com.sgkrashi.common.entity;

/**
 * Discriminates what a cart line or order line actually refers to — a
 * catalog {@code Product} (Module 5) or a {@code CropListing} (Module 7).
 * Exactly one of the corresponding FK columns is populated based on this
 * value; enforced in the service layer (see {@code CartServiceImpl},
 * {@code OrderServiceImpl}).
 */
public enum ItemType {
    PRODUCT,
    CROP_LISTING
}
