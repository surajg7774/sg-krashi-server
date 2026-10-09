package com.sgkrashi.dairy.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** A delivery window on certain weekdays. The days are stored as a comma list such as MON,TUE. */
@Entity
@Table(name = "delivery_slots")
public class DeliverySlot extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "days_of_week", nullable = false, length = 40)
    private String daysOfWeek;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public String getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(String daysOfWeek) { this.daysOfWeek = daysOfWeek; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public Set<DayOfWeek> days() {
        return WeekdayCodes.parse(daysOfWeek);
    }

    public boolean deliversOn(DayOfWeek day) {
        return days().contains(day);
    }

    public void setDays(Set<DayOfWeek> days) {
        this.daysOfWeek = WeekdayCodes.format(days);
    }

    /** Converts between sets of weekdays and the stored text (MON,TUE,...). */
    public static final class WeekdayCodes {
        private WeekdayCodes() {
        }

        public static String code(DayOfWeek day) {
            return day.name().substring(0, 3);
        }

        public static Set<DayOfWeek> parse(String text) {
            Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            if (text == null || text.isBlank()) {
                return days;
            }
            for (String part : text.split(",")) {
                String trimmed = part.trim().toUpperCase();
                for (DayOfWeek day : DayOfWeek.values()) {
                    if (code(day).equals(trimmed)) {
                        days.add(day);
                    }
                }
            }
            return days;
        }

        public static String format(Set<DayOfWeek> days) {
            return days.stream().sorted().map(WeekdayCodes::code).collect(Collectors.joining(","));
        }
    }
}
