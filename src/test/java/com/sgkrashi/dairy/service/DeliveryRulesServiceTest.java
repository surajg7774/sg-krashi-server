package com.sgkrashi.dairy.service;

import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.dairy.config.DairyProperties;
import com.sgkrashi.dairy.config.DairyTime;
import com.sgkrashi.dairy.dto.DairyDtos.DeliveryOptionsResponse;
import com.sgkrashi.dairy.entity.DeliverySlot;
import com.sgkrashi.dairy.repository.DeliveryAreaRepository;
import com.sgkrashi.dairy.repository.DeliverySlotRepository;
import com.sgkrashi.dairy.service.DeliveryRulesService.ChosenDelivery;
import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.entity.ProductCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeliveryRulesServiceTest {

    // Monday 2026-10-12 in India.
    private static final DairyTime TIME = new DairyTime(Clock.fixed(Instant.parse("2026-10-12T06:00:00Z"), ZoneOffset.UTC));

    private DeliveryAreaRepository areas;
    private DeliverySlotRepository slots;
    private DeliveryRulesService rules;

    @BeforeEach
    void setUp() {
        areas = mock(DeliveryAreaRepository.class);
        slots = mock(DeliverySlotRepository.class);
        rules = new DeliveryRulesService(areas, slots, DairyProperties.defaults(), TIME);
    }

    private static DeliverySlot slot(long id, String name, DayOfWeek... days) {
        DeliverySlot s = new DeliverySlot();
        s.setId(id);
        s.setName(name);
        s.setStartTime(LocalTime.of(6, 0));
        s.setEndTime(LocalTime.of(8, 0));
        s.setDays(EnumSet.copyOf(List.of(days)));
        return s;
    }

    @Test
    void withNoAreasListedThereIsNoRestriction() {
        when(areas.countByIsActiveTrue()).thenReturn(0L);
        assertFalse(rules.isRestricted());
        assertTrue(rules.serves("anything"));
        assertTrue(rules.serves(null));
    }

    @Test
    void withAreasListedOnlyThosePincodesAreServed() {
        when(areas.countByIsActiveTrue()).thenReturn(2L);
        when(areas.existsByIsActiveTrueAndPincode("482001")).thenReturn(true);
        assertTrue(rules.serves(" 482001 "));
        assertFalse(rules.serves("999999"));
        assertFalse(rules.serves(null));
    }

    @Test
    void anUnservedPincodeStopsADairyCheckout() {
        when(areas.countByIsActiveTrue()).thenReturn(1L);
        assertThrows(BusinessRuleException.class, () -> rules.validateForCheckout("999999", 1L, LocalDate.of(2026, 10, 13)));
    }

    @Test
    void withNoSlotsConfiguredNothingIsRequiredOrSaved() {
        when(areas.countByIsActiveTrue()).thenReturn(0L);
        when(slots.findByIsActiveTrueOrderBySortOrderAscStartTimeAsc()).thenReturn(List.of());
        assertNull(rules.validateForCheckout("482001", null, null));
    }

    @Test
    void withSlotsConfiguredASlotAndDateAreRequired() {
        when(areas.countByIsActiveTrue()).thenReturn(0L);
        when(slots.findByIsActiveTrueOrderBySortOrderAscStartTimeAsc()).thenReturn(List.of(slot(1, "Morning", DayOfWeek.values())));
        assertThrows(BusinessRuleException.class, () -> rules.validateForCheckout("482001", null, null));
        assertThrows(BusinessRuleException.class, () -> rules.validateForCheckout("482001", 1L, null));
        assertThrows(BusinessRuleException.class, () -> rules.validateForCheckout("482001", 99L, LocalDate.of(2026, 10, 13)));
    }

    @Test
    void theEarliestDateIsTomorrowAndOnlyTheSlotsDaysAreOffered() {
        DeliverySlot weekdays = slot(1, "Morning", DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        // Today is Mon 12th; the window is the 7 days from tomorrow: Tue 13th .. Mon 19th. Only Wed 14th and Mon 19th qualify.
        assertEquals(List.of(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 19)), rules.availableDates(weekdays));
    }

    @Test
    void aValidChoiceIsReturned() {
        when(areas.countByIsActiveTrue()).thenReturn(0L);
        DeliverySlot all = slot(1, "Morning", DayOfWeek.values());
        when(slots.findByIsActiveTrueOrderBySortOrderAscStartTimeAsc()).thenReturn(List.of(all));
        ChosenDelivery chosen = rules.validateForCheckout("482001", 1L, LocalDate.of(2026, 10, 13));
        assertEquals(LocalDate.of(2026, 10, 13), chosen.date());
        assertEquals("Morning", chosen.slot().getName());
        // Today and the day after the window are not offered.
        assertThrows(BusinessRuleException.class, () -> rules.validateForCheckout("482001", 1L, LocalDate.of(2026, 10, 12)));
        assertThrows(BusinessRuleException.class, () -> rules.validateForCheckout("482001", 1L, LocalDate.of(2026, 10, 20)));
    }

    @Test
    void optionsReportRestrictionAndServiceability() {
        when(areas.countByIsActiveTrue()).thenReturn(1L);
        when(areas.existsByIsActiveTrueAndPincode("482001")).thenReturn(true);
        when(slots.findByIsActiveTrueOrderBySortOrderAscStartTimeAsc()).thenReturn(List.of());
        DeliveryOptionsResponse yes = rules.options("482001");
        assertTrue(yes.restricted());
        assertEquals(Boolean.TRUE, yes.serviceable());
        assertEquals(Boolean.FALSE, rules.options("999999").serviceable());
        assertNull(rules.options(null).serviceable());
        assertTrue(yes.slots().isEmpty());
    }

    @Test
    void aCartWithoutDairyNeverReachesTheRules() {
        DairyCatalogService catalog = mock(DairyCatalogService.class);
        DeliveryRulesService untouched = mock(DeliveryRulesService.class);
        when(catalog.dairyCategoryIds()).thenReturn(Set.of(6L));
        DairyCheckoutService checkout = new DairyCheckoutService(catalog, untouched, null);

        Product grains = new Product();
        ProductCategory category = new ProductCategory();
        category.setId(1L);
        grains.setCategory(category);

        assertNull(checkout.validate(List.of(grains), "999999", null, null));
        verifyNoInteractions(untouched);
    }

    @Test
    void aCartWithNoDairyCategoryAtAllNeverReachesTheRules() {
        DairyCatalogService catalog = mock(DairyCatalogService.class);
        DeliveryRulesService untouched = mock(DeliveryRulesService.class);
        when(catalog.dairyCategoryIds()).thenReturn(Set.of());
        DairyCheckoutService checkout = new DairyCheckoutService(catalog, untouched, null);

        assertNull(checkout.validate(List.of(new Product()), "999999", 5L, LocalDate.of(2026, 10, 13)));
        verifyNoInteractions(untouched);
    }

    @Test
    void theDairyCategoryTreeIncludesChildrenAndGrandchildren() {
        ProductCategory dairy = category(6L, "dairy", null);
        ProductCategory milk = category(7L, "milk", dairy);
        ProductCategory cow = category(8L, "cow-milk", milk);
        ProductCategory grains = category(1L, "grains", null);
        assertEquals(Set.of(6L, 7L, 8L), DairyCatalogService.descendantsOfDairy(List.of(grains, cow, milk, dairy)));
        assertTrue(DairyCatalogService.descendantsOfDairy(List.of(grains)).isEmpty());
    }

    private static ProductCategory category(long id, String slug, ProductCategory parent) {
        ProductCategory c = new ProductCategory();
        c.setId(id);
        c.setSlug(slug);
        c.setParent(parent);
        return c;
    }
}
