package com.sgkrashi.dairy.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** "Today" for dairy rules is always the farm's day in India (IST), whatever zone the server runs in. */
@Component
public class DairyTime {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final Clock clock;

    @Autowired
    public DairyTime() {
        this(Clock.system(IST));
    }

    public DairyTime(Clock clock) {
        this.clock = clock.withZone(IST);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDate tomorrow() {
        return today().plusDays(1);
    }
}
