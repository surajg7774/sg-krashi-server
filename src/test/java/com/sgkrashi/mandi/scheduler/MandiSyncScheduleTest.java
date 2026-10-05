package com.sgkrashi.mandi.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pins the actual schedule (not just the strings): when the cron expressions really fire, in IST. */
class MandiSyncScheduleTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static Scheduled scheduledOn(String method) throws NoSuchMethodException {
        return MandiPriceSyncJob.class.getMethod(method).getAnnotation(Scheduled.class);
    }

    private static List<String> firesOn(String cron, ZonedDateTime from, int count) {
        CronExpression expression = CronExpression.parse(cron);
        List<String> times = new ArrayList<>();
        ZonedDateTime cursor = from;
        for (int i = 0; i < count; i++) {
            cursor = expression.next(cursor);
            times.add(cursor.toLocalDate() + " " + cursor.toLocalTime());
        }
        return times;
    }

    @Test
    void theDailyJobIsStill0630Ist() throws Exception {
        Scheduled daily = scheduledOn("syncDaily");
        assertEquals("0 30 6 * * *", daily.cron());
        assertEquals("Asia/Kolkata", daily.zone());
        assertEquals(List.of("2026-10-05 06:30", "2026-10-06 06:30"),
                firesOn(daily.cron(), ZonedDateTime.of(2026, 10, 5, 0, 0, 0, 0, IST), 2));
    }

    @Test
    void theCatchUpFiresAt1230And1830IstEveryDay() throws Exception {
        Scheduled catchUp = scheduledOn("syncCatchUp");
        assertEquals("0 30 12,18 * * *", catchUp.cron());
        assertEquals("Asia/Kolkata", catchUp.zone());
        assertEquals(List.of("2026-10-05 12:30", "2026-10-05 18:30", "2026-10-06 12:30", "2026-10-06 18:30"),
                firesOn(catchUp.cron(), ZonedDateTime.of(2026, 10, 5, 0, 0, 0, 0, IST), 4));
    }
}
