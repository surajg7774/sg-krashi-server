package com.sgkrashi.usage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UsageCountingInterceptorTest {

    private static final String BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    private final UsageCounter counter = mock(UsageCounter.class);
    private final UsageCountingInterceptor interceptor = new UsageCountingInterceptor(counter);

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void call(String method, String pattern, int status, String userAgent, Object searchHadResults) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, pattern.replace("{idOrSlug}", "7"));
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
        if (userAgent != null) request.addHeader("User-Agent", userAgent);
        if (searchHadResults != null) request.setAttribute(UsageCountingInterceptor.SEARCH_HAD_RESULTS, searchHadResults);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        interceptor.afterCompletion(request, response, new Object(), null);
    }

    // ---- exactly which routes are counted -------------------------------------------------------------------

    @Test
    void everyListedRouteIsCountedAsItsFeature() {
        call("GET", "/api/v1/mandi/prices", 200, BROWSER, null);
        call("GET", "/api/v1/weather/current", 200, BROWSER, null);
        call("GET", "/api/v1/schemes", 200, BROWSER, null);
        call("GET", "/api/v1/products/{idOrSlug}", 200, BROWSER, null);
        call("GET", "/api/v1/crop-listings/{idOrSlug}", 200, BROWSER, null);
        call("GET", "/api/v1/equipment/{idOrSlug}", 200, BROWSER, null);
        call("GET", "/api/v1/farm-stay/{idOrSlug}", 200, BROWSER, null);
        call("POST", "/api/v1/cart/items", 201, BROWSER, null);
        for (UsageFeature f : List.of(UsageFeature.MANDI_PRICES, UsageFeature.WEATHER, UsageFeature.SCHEMES, UsageFeature.PRODUCT_DETAIL,
                UsageFeature.CROP_LISTING_DETAIL, UsageFeature.EQUIPMENT_DETAIL, UsageFeature.STAY_DETAIL, UsageFeature.ADD_TO_CART)) {
            verify(counter).record(f);
        }
    }

    @Test
    void otherRoutesAreNotCounted() {
        call("GET", "/api/v1/products", 200, BROWSER, null);                        // the catalogue list is not a "view"
        call("GET", "/api/v1/mandi/prices/trend", 200, BROWSER, null);
        call("GET", "/api/v1/mandi/prices/meta", 200, BROWSER, null);
        call("GET", "/api/v1/weather/geocode", 200, BROWSER, null);                 // would carry typed place names
        call("GET", "/api/v1/equipment/categories", 200, BROWSER, null);
        call("PUT", "/api/v1/cart/items/{itemId}", 200, BROWSER, null);
        call("GET", "/api/v1/cart", 200, BROWSER, null);
        call("GET", "/api/v1/admin/insights/signups", 200, BROWSER, null);
        call("GET", "/api/v1/orders/{id}", 200, BROWSER, null);
        call("POST", "/api/v1/products/{idOrSlug}", 200, BROWSER, null);           // wrong method for the detail view
        verify(counter, never()).record(any());
    }

    // ---- what is never counted ------------------------------------------------------------------------------

    @Test
    void failedRequestsAreNotCounted() {
        for (int status : new int[]{301, 400, 401, 404, 429, 500, 503}) {
            call("GET", "/api/v1/products/{idOrSlug}", status, BROWSER, null);
        }
        verify(counter, never()).record(any());
    }

    @Test
    void automatedClientsAreNotCounted() {
        call("GET", "/api/v1/products/{idOrSlug}", 200, "WhatsApp/2.23.20.0 A", null);
        call("GET", "/api/v1/products/{idOrSlug}", 200, AutomatedClientDetector.PREVIEW_USER_AGENT, null);
        call("GET", "/api/v1/products/{idOrSlug}", 200, "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", null);
        call("GET", "/api/v1/products/{idOrSlug}", 200, null, null);
        verify(counter, never()).record(any());
    }

    @Test
    void aRequestThatThrewIsNotCounted() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products/7");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/products/{idOrSlug}");
        request.addHeader("User-Agent", BROWSER);
        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), new RuntimeException("boom"));
        verify(counter, never()).record(any());
    }

    // ---- search: only "found anything or not", never the text -----------------------------------------------

    @Test
    void searchCountsWithOrWithoutResultsAndNeverWithoutAQuery() {
        call("GET", "/api/v1/search", 200, BROWSER, true);
        call("GET", "/api/v1/search", 200, BROWSER, false);
        verify(counter).record(UsageFeature.SEARCH_WITH_RESULTS);
        verify(counter).record(UsageFeature.SEARCH_NO_RESULTS);
        call("GET", "/api/v1/search", 200, BROWSER, null);   // a blank search sets no flag
        verify(counter, org.mockito.Mockito.times(2)).record(any());
    }

    // ---- crop scans: guests only ----------------------------------------------------------------------------

    @Test
    void aGuestCropScanIsCountedButASignedInOneIsNot() {
        call("POST", "/api/v1/ai/crop-doctor/analyze", 201, BROWSER, null); // no authentication at all
        verify(counter).record(UsageFeature.GUEST_CROP_SCAN);

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("k", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        call("POST", "/api/v1/ai/crop-doctor/analyze", 201, BROWSER, null);
        verify(counter, org.mockito.Mockito.times(2)).record(UsageFeature.GUEST_CROP_SCAN);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("someone@example.test", null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));
        call("POST", "/api/v1/ai/crop-doctor/analyze", 201, BROWSER, null);
        verify(counter, org.mockito.Mockito.times(2)).record(UsageFeature.GUEST_CROP_SCAN);
    }

    // ---- never breaks a request -----------------------------------------------------------------------------

    @Test
    void aFailingCounterCannotBreakTheRequest() {
        doThrow(new IllegalStateException("counter exploded")).when(counter).record(any());
        call("GET", "/api/v1/products/{idOrSlug}", 200, BROWSER, null); // must not throw
        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null); // no pattern at all: must not throw
    }

    @Test
    void decisionForSearchAndScanIsPure() {
        assertEquals(Optional.empty(), UsageCountingInterceptor.featureFor("GET", "/api/v1/search", null, true));
        assertEquals(Optional.of(UsageFeature.SEARCH_NO_RESULTS), UsageCountingInterceptor.featureFor("GET", "/api/v1/search", Boolean.FALSE, true));
        assertEquals(Optional.empty(), UsageCountingInterceptor.featureFor("POST", "/api/v1/ai/crop-doctor/analyze", null, false));
        assertTrue(UsageCountingInterceptor.featureFor("POST", "/api/v1/ai/crop-doctor/analyze", null, true).isPresent());
    }
}
