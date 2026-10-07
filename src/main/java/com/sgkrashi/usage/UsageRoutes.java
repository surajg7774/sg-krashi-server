package com.sgkrashi.usage;

import java.util.Map;
import java.util.Optional;

/**
 * The complete list of routes that are counted, as "METHOD pattern" using the route pattern Spring matched
 * (so {@code /api/v1/products/7} and {@code /api/v1/products/organic-urea} both arrive as
 * {@code /api/v1/products/{idOrSlug}}, and an id or slug is never seen here). Anything not listed is not counted.
 *
 * <p>Two features cannot be decided from the route alone and are handled by {@link UsageCountingInterceptor}:
 * the crop scan counts only when the caller is a guest, and a search counts as "with" or "without" results
 * depending on what the search found.
 */
public final class UsageRoutes {

    public static final String CROP_SCAN_ROUTE = "POST /api/v1/ai/crop-doctor/analyze";
    public static final String SEARCH_ROUTE = "GET /api/v1/search";

    private static final Map<String, UsageFeature> ROUTES = Map.of(
            "GET /api/v1/mandi/prices", UsageFeature.MANDI_PRICES,
            "GET /api/v1/weather/current", UsageFeature.WEATHER,
            "GET /api/v1/schemes", UsageFeature.SCHEMES,
            "GET /api/v1/products/{idOrSlug}", UsageFeature.PRODUCT_DETAIL,
            "GET /api/v1/crop-listings/{idOrSlug}", UsageFeature.CROP_LISTING_DETAIL,
            "GET /api/v1/equipment/{idOrSlug}", UsageFeature.EQUIPMENT_DETAIL,
            "GET /api/v1/farm-stay/{idOrSlug}", UsageFeature.STAY_DETAIL,
            "POST /api/v1/cart/items", UsageFeature.ADD_TO_CART);

    private UsageRoutes() {
    }

    public static String routeKey(String method, String pattern) {
        return method + " " + pattern;
    }

    /** The feature for a route that is counted on the route alone, or empty. */
    public static Optional<UsageFeature> simpleFeature(String method, String pattern) {
        if (method == null || pattern == null) return Optional.empty();
        return Optional.ofNullable(ROUTES.get(routeKey(method, pattern)));
    }
}
