package com.sgkrashi.insights.util;

import com.sgkrashi.common.exception.ValidationException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Day, week (Monday start) or month buckets, always in India time. The SQL expression for each
 * is a fixed literal chosen here, never built from request text, so {@code groupBy} is not an
 * injection surface; {@link #parse} rejects anything else.
 */
public enum Granularity {
    DAY("day"),
    WEEK("week"),
    MONTH("month");

    private final String label;

    Granularity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Granularity parse(String value) {
        if (value == null) return DAY;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "day" -> DAY;
            case "week" -> WEEK;
            case "month" -> MONTH;
            default -> throw new ValidationException("groupBy must be one of: day, week, month");
        };
    }

    /** The first day of the bucket that contains {@code date}. */
    public LocalDate bucketStart(LocalDate date) {
        return switch (this) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private LocalDate next(LocalDate bucketStart) {
        return switch (this) {
            case DAY -> bucketStart.plusDays(1);
            case WEEK -> bucketStart.plusWeeks(1);
            case MONTH -> bucketStart.plusMonths(1);
        };
    }

    /** Every bucket start from the one holding {@code from} to the one holding {@code to}, so charts show real zeros instead of gaps. */
    public List<LocalDate> bucketsBetween(LocalDate from, LocalDate to) {
        List<LocalDate> buckets = new ArrayList<>();
        for (LocalDate b = bucketStart(from); !b.isAfter(to); b = next(b)) {
            buckets.add(b);
        }
        return buckets;
    }
}
