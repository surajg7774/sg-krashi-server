package com.sgkrashi.usage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

/** The one write to {@code usage_daily}: add to a day's running total. Additive, so several servers (or a retry) can never overwrite each other. */
@Repository
public class UsageDailyRepository {

    private final JdbcTemplate jdbc;

    public UsageDailyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Adds {@code amount} to (day, feature), creating the row if it is the first count of the day. */
    public void add(LocalDate day, String feature, long amount) {
        jdbc.update("INSERT INTO usage_daily (day, feature, `count`) VALUES (?, ?, ?) AS new_row "
                + "ON DUPLICATE KEY UPDATE `count` = usage_daily.`count` + new_row.`count`", java.sql.Date.valueOf(day), feature, amount);
    }
}
