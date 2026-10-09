package com.sgkrashi.dairy.entity;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which dates a subscription delivers on: the one rule the nightly job and the "upcoming" list share. */
class DairySubscriptionScheduleTest {

    // 2026-10-12 is a Monday.
    private static final LocalDate MON = LocalDate.of(2026, 10, 12);

    private static DairySubscription daily(LocalDate start) {
        DairySubscription s = new DairySubscription();
        s.setFrequency(SubscriptionFrequency.DAILY);
        s.setStartDate(start);
        s.setStatus(SubscriptionStatus.ACTIVE);
        return s;
    }

    private static List<LocalDate> deliveryDays(DairySubscription s, LocalDate from, int days) {
        return Stream.iterate(from, d -> d.plusDays(1)).limit(days).filter(s::deliversOn).collect(Collectors.toList());
    }

    @Test
    void dailyDeliversEveryDayFromTheStartDate() {
        DairySubscription s = daily(MON);
        assertFalse(s.deliversOn(MON.minusDays(1)));
        assertEquals(7, deliveryDays(s, MON, 7).size());
    }

    @Test
    void selectedWeekdaysOnlyDeliverOnThoseDays() {
        DairySubscription s = daily(MON);
        s.setFrequency(SubscriptionFrequency.DAYS);
        s.setWeekdaySet(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY));
        assertEquals(List.of(MON, MON.plusDays(3), MON.plusDays(7), MON.plusDays(10)), deliveryDays(s, MON, 14));
        assertEquals("MON,THU", s.getWeekdays());
    }

    @Test
    void aPauseWithAnEndSkipsOnlyThoseDates() {
        DairySubscription s = daily(MON);
        s.setStatus(SubscriptionStatus.PAUSED);
        s.setPauseFrom(MON.plusDays(2));
        s.setPauseTo(MON.plusDays(4));
        assertTrue(s.deliversOn(MON.plusDays(1)));
        assertFalse(s.deliversOn(MON.plusDays(2)));
        assertFalse(s.deliversOn(MON.plusDays(4)));
        assertTrue(s.deliversOn(MON.plusDays(5)));
    }

    @Test
    void anOpenEndedPauseSkipsEverythingFromItsStart() {
        DairySubscription s = daily(MON);
        s.setStatus(SubscriptionStatus.PAUSED);
        s.setPauseFrom(MON.plusDays(2));
        assertTrue(s.deliversOn(MON.plusDays(1)));
        assertFalse(s.deliversOn(MON.plusDays(2)));
        assertFalse(s.deliversOn(MON.plusDays(60)));
    }

    @Test
    void skippedDatesAreNotDeliveryDays() {
        DairySubscription s = daily(MON);
        s.getSkipDates().add(MON.plusDays(1));
        assertFalse(s.deliversOn(MON.plusDays(1)));
        assertTrue(s.deliversOn(MON.plusDays(2)));
    }

    @Test
    void aCancelledSubscriptionNeverDelivers() {
        DairySubscription s = daily(MON);
        s.setStatus(SubscriptionStatus.CANCELLED);
        assertTrue(deliveryDays(s, MON, 30).isEmpty());
    }

    @Test
    void aPauseThatHasEndedReadsAsActiveAgain() {
        DairySubscription s = daily(MON);
        s.setStatus(SubscriptionStatus.PAUSED);
        s.setPauseFrom(MON.plusDays(1));
        s.setPauseTo(MON.plusDays(3));
        assertEquals(SubscriptionStatus.PAUSED, s.effectiveStatus(MON.plusDays(2)));
        assertEquals(SubscriptionStatus.ACTIVE, s.effectiveStatus(MON.plusDays(4)));
        s.setPauseTo(null);
        assertEquals(SubscriptionStatus.PAUSED, s.effectiveStatus(MON.plusDays(400)));
    }

    @Test
    void weekdayCodesRoundTrip() {
        assertEquals("MON,TUE,SUN", DeliverySlot.WeekdayCodes.format(EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY)));
        assertEquals(EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), DeliverySlot.WeekdayCodes.parse(" fri, SAT ,bogus"));
        assertTrue(DeliverySlot.WeekdayCodes.parse(null).isEmpty());
    }
}
