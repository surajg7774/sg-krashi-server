package com.sgkrashi.dairy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sgkrashi.dairy.controller.AdminDairyController;
import com.sgkrashi.dairy.controller.DairyStoreController;
import com.sgkrashi.dairy.controller.DairySubscriptionController;
import com.sgkrashi.dairy.dto.DairyDtos.DairyDetailsResponse;
import com.sgkrashi.dairy.entity.DairyUnit;
import com.sgkrashi.order.dto.request.CheckoutRequest;
import com.sgkrashi.order.dto.response.OrderResponse;
import com.sgkrashi.order.entity.OrderStatus;
import com.sgkrashi.productstore.dto.response.ProductDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The installed mobile app talks to this backend: what it already receives and sends must not change. */
class DairyApiContractTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ProductDetailResponse product(DairyDetailsResponse dairy) {
        return new ProductDetailResponse(1L, "Rice", "rice", "d", new BigDecimal("10.00"), false, 5, null,
                List.of(), List.of(), null, 0, true, Instant.parse("2026-01-01T00:00:00Z"), dairy);
    }

    @Test
    void aNonDairyProductHasExactlyTheFieldsItAlwaysHad() {
        JsonNode json = mapper.valueToTree(product(null));
        List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        assertEquals(List.of("id", "name", "slug", "description", "price", "isOrganicCertified", "stockQty", "category",
                "media", "relatedProducts", "avgRating", "reviewCount", "isActive", "createdAt"), names);
        assertFalse(json.has("dairy"));
    }

    @Test
    void aDairyProductGainsOnlyTheDairyField() {
        JsonNode json = mapper.valueToTree(product(new DairyDetailsResponse(DairyUnit.LITRE, new BigDecimal("0.5"), 3, true, "Keep chilled")));
        assertTrue(json.has("dairy"));
        assertEquals("LITRE", json.get("dairy").get("unit").asText());
    }

    @Test
    void anOrderWithoutDairyHasNoDeliveryField() {
        OrderResponse order = new OrderResponse(1L, "SGK-1", OrderStatus.PENDING_PAYMENT, new BigDecimal("10.00"), "a", null, "c", "s", "482001",
                List.of(), List.of(), Instant.parse("2026-01-01T00:00:00Z"), null);
        JsonNode json = mapper.valueToTree(order);
        List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        assertEquals(List.of("id", "orderNumber", "status", "totalAmount", "shippingLine1", "shippingLine2", "shippingCity",
                "shippingState", "shippingPincode", "items", "statusHistory", "createdAt"), names);
    }

    @Test
    void anOldCheckoutBodyWithOnlyAnAddressStillParses() throws Exception {
        CheckoutRequest request = mapper.readValue("{\"addressId\": 5}", CheckoutRequest.class);
        assertEquals(5L, request.addressId());
        assertNull(request.deliverySlotId());
        assertNull(request.deliveryDate());
        CheckoutRequest dairy = mapper.readValue("{\"addressId\": 5, \"deliverySlotId\": 2, \"deliveryDate\": \"2026-10-13\"}", CheckoutRequest.class);
        assertEquals(2L, dairy.deliverySlotId());
        assertEquals("2026-10-13", dairy.deliveryDate().toString());
    }

    @Test
    void adminEndpointsRequireAnAdminAndOthersAreOpenOnlyWhereIntended() {
        PreAuthorize guard = AdminDairyController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(guard, "AdminDairyController must carry a class-level @PreAuthorize");
        assertTrue(guard.value().contains("ADMIN") && guard.value().contains("SUPER_ADMIN"));
        assertTrue(AdminDairyController.class.getAnnotation(RequestMapping.class).value()[0].startsWith("/api/v1/admin/"));
        // The customer and public controllers must not be mapped under a path the public allow-list opens by mistake.
        assertEquals("/api/v1/dairy/subscriptions", DairySubscriptionController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertEquals("/api/v1/dairy-store", DairyStoreController.class.getAnnotation(RequestMapping.class).value()[0]);
    }
}
