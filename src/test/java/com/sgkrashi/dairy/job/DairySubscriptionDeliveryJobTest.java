package com.sgkrashi.dairy.job;

import com.sgkrashi.dairy.config.DairyProperties;
import com.sgkrashi.dairy.config.DairyTime;
import com.sgkrashi.dairy.service.DairyDeliveryPreparationService;
import com.sgkrashi.dairy.service.DairyDeliveryPreparationService.RunSummary;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DairySubscriptionDeliveryJobTest {

    // 2026-10-12 19:00 UTC is 2026-10-13 00:30 in India: "today" must be the Indian day.
    private static final DairyTime TIME = new DairyTime(Clock.fixed(Instant.parse("2026-10-12T19:00:00Z"), ZoneOffset.UTC));

    @Test
    void theDefaultsAreAllOffSoAnUnconfiguredDeploymentDoesNothing() {
        DairyProperties defaults = DairyProperties.defaults();
        assertFalse(defaults.subscriptionsEnabled());
        assertFalse(defaults.subscriptionJobEnabled());
    }

    @Test
    void withTheFlagOffTheJobTouchesNothing() {
        DairyDeliveryPreparationService preparation = mock(DairyDeliveryPreparationService.class);
        DairySubscriptionDeliveryJob job = new DairySubscriptionDeliveryJob(DairyProperties.defaults(), TIME, preparation);

        assertNull(job.runOnce());
        job.prepareTomorrow();

        verifyNoInteractions(preparation);
    }

    @Test
    void withTheFlagOnItPreparesTomorrowInIndia() {
        DairyDeliveryPreparationService preparation = mock(DairyDeliveryPreparationService.class);
        LocalDate expected = LocalDate.of(2026, 10, 14);
        when(preparation.prepareFor(expected)).thenReturn(new RunSummary(expected, 3, 2, 1, 0, 0));
        DairyProperties on = new DairyProperties(false, true, 1, 7, 20);
        DairySubscriptionDeliveryJob job = new DairySubscriptionDeliveryJob(on, TIME, preparation);

        RunSummary summary = job.runOnce();

        assertNotNull(summary);
        assertEquals(expected, summary.date());
        verify(preparation).prepareFor(expected);
        verify(preparation, never()).prepareFor(LocalDate.of(2026, 10, 13));
    }

    @Test
    void customersCreatingSubscriptionsIsASeparateSwitchFromTheJob() {
        assertTrue(new DairyProperties(true, false, 1, 7, 20).subscriptionsEnabled());
        assertFalse(new DairyProperties(true, false, 1, 7, 20).subscriptionJobEnabled());
    }

    @Test
    void dairyTimeUsesTheIndianDay() {
        assertEquals(LocalDate.of(2026, 10, 13), TIME.today());
        assertEquals(LocalDate.of(2026, 10, 14), TIME.tomorrow());
    }
}
