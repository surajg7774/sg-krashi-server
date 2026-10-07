package com.sgkrashi.usage;

/**
 * The only things the server counts. Each is a fixed key; nothing about who used it, what they typed or where
 * they were is ever attached. A "view" here means a request answered successfully by the server (the website
 * and the mobile app are counted together, and a page that is served from the browser's own cache is not seen).
 */
public enum UsageFeature {
    MANDI_PRICES("mandi_prices"),
    WEATHER("weather"),
    SCHEMES("schemes"),
    PRODUCT_DETAIL("product_detail"),
    CROP_LISTING_DETAIL("crop_listing_detail"),
    EQUIPMENT_DETAIL("equipment_detail"),
    STAY_DETAIL("stay_detail"),
    ADD_TO_CART("add_to_cart"),
    SEARCH_WITH_RESULTS("search_with_results"),
    SEARCH_NO_RESULTS("search_no_results"),
    GUEST_CROP_SCAN("guest_crop_scan");

    private final String key;

    UsageFeature(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
