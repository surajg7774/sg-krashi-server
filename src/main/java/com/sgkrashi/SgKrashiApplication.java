package com.sgkrashi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableScheduling} activates {@code BookingCompletionJob} — the first {@code @Scheduled} job in this codebase. */
@SpringBootApplication
@EnableScheduling
public class SgKrashiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SgKrashiApplication.class, args);
    }

}
