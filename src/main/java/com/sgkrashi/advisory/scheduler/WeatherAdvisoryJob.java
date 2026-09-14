package com.sgkrashi.advisory.scheduler;

import com.sgkrashi.advisory.entity.FarmerProfile;
import com.sgkrashi.advisory.repository.FarmerProfileRepository;
import com.sgkrashi.advisory.rule.Advisory;
import com.sgkrashi.advisory.rule.AdvisoryRules;
import com.sgkrashi.ai.weather.dto.WeatherSnapshot;
import com.sgkrashi.ai.weather.service.WeatherService;
import com.sgkrashi.notification.entity.NotificationRelatedType;
import com.sgkrashi.notification.entity.NotificationType;
import com.sgkrashi.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Daily, per-opted-in-farmer weather advisory check — same {@code
 * @Component}/{@code runOnce()}-trampoline shape as {@code
 * BookingCompletionJob}/{@code PayoutBatchJob}/{@code MandiPriceSyncJob}.
 *
 * <p>Calls {@code NotificationService.notify(...)} directly rather than via
 * the usual publish-an-event-and-let-an-{@code AFTER_COMMIT}-listener-react
 * indirection ({@code NotificationEventListener}'s pattern): that
 * indirection exists specifically because a request-scoped transaction
 * can't safely persist a notification until it has actually committed. A
 * scheduled job has no such outer transaction to wait for, so calling the
 * already-{@code @Transactional(REQUIRES_NEW)} service straight from here
 * is correct and simpler, not a shortcut around the real constraint.
 *
 * <p>No advisory triggers for a farmer that day → no call to {@code notify}
 * at all — deliberately no "all clear" message, per the explicit
 * requirement that this must not become a daily spam notification.
 */
@Component
public class WeatherAdvisoryJob {

    private static final Logger log = LoggerFactory.getLogger(WeatherAdvisoryJob.class);

    private final FarmerProfileRepository farmerProfileRepository;
    private final WeatherService weatherService;
    private final NotificationService notificationService;

    public WeatherAdvisoryJob(
            FarmerProfileRepository farmerProfileRepository,
            WeatherService weatherService,
            NotificationService notificationService
    ) {
        this.farmerProfileRepository = farmerProfileRepository;
        this.weatherService = weatherService;
        this.notificationService = notificationService;
    }

    /** Runs daily at 6:00 AM IST — before a typical farmer's working day starts. */
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Kolkata")
    public void checkDaily() {
        runOnce();
    }

    /** @return how many farmers were sent an advisory notification this run (0 is a perfectly normal result — calm weather everywhere) */
    public int runOnce() {
        List<FarmerProfile> optedIn = farmerProfileRepository.findByWeatherAdvisoryOptInTrue();
        int notified = 0;
        for (FarmerProfile profile : optedIn) {
            if (!profile.hasLocation()) {
                continue; // opted in but never set a location — nothing to check
            }
            notified += checkOneFarmer(profile) ? 1 : 0;
        }
        log.info("WeatherAdvisoryJob ran: checked {} opted-in farmer(s), notified {}", optedIn.size(), notified);
        return notified;
    }

    private boolean checkOneFarmer(FarmerProfile profile) {
        double lat = profile.getLatitude().doubleValue();
        double lon = profile.getLongitude().doubleValue();
        Optional<WeatherSnapshot> snapshot = weatherService.fetchWeather(lat, lon);
        if (snapshot.isEmpty()) {
            return false; // weather unavailable this run — never treated as a fault, just nothing to report
        }

        List<Advisory> triggered = AdvisoryRules.evaluateAll(snapshot.get());
        if (triggered.isEmpty()) {
            return false;
        }

        String title = triggered.size() == 1 ? triggered.get(0).title() : "Weather advisory for your farm today";
        String message = triggered.stream().map(Advisory::message).reduce((a, b) -> a + "\n\n" + b).orElse("");
        notificationService.notify(profile.getUserId(), NotificationType.WEATHER_ADVISORY, title, message,
                NotificationRelatedType.FARMER_PROFILE, profile.getId());
        return true;
    }
}
