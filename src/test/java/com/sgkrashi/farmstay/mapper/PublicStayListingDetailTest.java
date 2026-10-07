package com.sgkrashi.farmstay.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.farmstay.dto.response.PublicStayListingDetailResponse;
import com.sgkrashi.farmstay.dto.response.StayListingDetailResponse;
import com.sgkrashi.farmstay.entity.StayListing;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public stay-listing response must not carry the street address or pincode, while
 * the admin response (used by the edit form) must keep them.
 */
class PublicStayListingDetailTest {

    private final StayListingMapper mapper = new StayListingMapper();
    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();

    private StayListing listing() {
        StayListing l = new StayListing();
        l.setId(5L);
        l.setName("Test Farm");
        l.setSlug("test-farm");
        l.setDescription("A farm");
        l.setMaxGuests(4);
        l.setNightlyRate(new BigDecimal("1500.00"));
        l.setAmenities("Wifi, Parking");
        l.setAddressLine1("12 Secret Lane");
        l.setAddressLine2("Behind the Mango Grove");
        l.setCity("Goa");
        l.setState("Goa");
        l.setPincode("403001");
        l.setAvailable(true);
        l.setAvgRating(new BigDecimal("4.5"));
        l.setReviewCount(2);
        return l;
    }

    @Test
    void publicDetailHasNoStreetAddressOrPincodeButKeepsCityStateAndAmenities() throws Exception {
        PublicStayListingDetailResponse publicView = mapper.toPublicDetail(listing(), List.of());

        JsonNode node = json.valueToTree(publicView);
        assertFalse(node.has("addressLine1"));
        assertFalse(node.has("addressLine2"));
        assertFalse(node.has("pincode"));
        assertEquals("Goa", node.get("city").asText());
        assertEquals("Goa", node.get("state").asText());
        assertEquals(2, node.get("amenities").size());

        String raw = json.writeValueAsString(publicView);
        assertFalse(raw.contains("Secret Lane"), "street text must not appear anywhere in the body");
        assertFalse(raw.contains("403001"), "pincode must not appear anywhere in the body");
    }

    @Test
    void adminDetailStillCarriesTheFullAddress() {
        StayListingDetailResponse adminView = mapper.toDetail(listing(), List.of());

        JsonNode node = json.valueToTree(adminView);
        assertTrue(node.has("addressLine1"));
        assertEquals("12 Secret Lane", node.get("addressLine1").asText());
        assertEquals("Behind the Mango Grove", node.get("addressLine2").asText());
        assertEquals("403001", node.get("pincode").asText());
    }
}
