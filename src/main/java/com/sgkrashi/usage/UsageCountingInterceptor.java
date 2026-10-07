package com.sgkrashi.usage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Counts a use of a public feature once the server has answered successfully. It reads only the HTTP method,
 * the matched route pattern (never the real address, so no ids, slugs, coordinates or search text), the status
 * code, whether the caller is signed in, and, for a yes/no, whether the User-Agent is an automated client.
 * None of that is stored. Failed requests (any non-2xx) and automated clients are not counted.
 *
 * <p>Runs after the response is decided and swallows every error: it cannot change or break a response.
 */
@Component
public class UsageCountingInterceptor implements HandlerInterceptor {

    /** Request attribute the search controller sets: TRUE if the search found something, FALSE if it found nothing. */
    public static final String SEARCH_HAD_RESULTS = UsageCountingInterceptor.class.getName() + ".searchHadResults";

    private final Supplier<UsageCounter> counter;

    /**
     * The counter is looked up lazily and may be absent: a web-layer test slice loads this interceptor without the
     * counter bean, and then it simply counts nothing instead of failing to start.
     */
    @Autowired
    public UsageCountingInterceptor(ObjectProvider<UsageCounter> counterProvider) {
        this.counter = counterProvider::getIfAvailable;
    }

    UsageCountingInterceptor(UsageCounter counter) {
        this.counter = () -> counter;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            if (ex != null || response.getStatus() < 200 || response.getStatus() >= 300) return;
            if (AutomatedClientDetector.isAutomated(request.getHeader("User-Agent"))) return;
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (pattern == null) return;
            UsageCounter target = counter.get();
            if (target == null) return;
            featureFor(request.getMethod(), pattern.toString(), request.getAttribute(SEARCH_HAD_RESULTS), isGuest()).ifPresent(target::record);
        } catch (Throwable ignored) {
            // Counting must never affect a request.
        }
    }

    /** The decision, separated from the servlet objects so it can be tested directly. */
    static Optional<UsageFeature> featureFor(String method, String pattern, Object searchHadResults, boolean guest) {
        String route = UsageRoutes.routeKey(method, pattern);
        if (UsageRoutes.CROP_SCAN_ROUTE.equals(route)) {
            return guest ? Optional.of(UsageFeature.GUEST_CROP_SCAN) : Optional.empty();
        }
        if (UsageRoutes.SEARCH_ROUTE.equals(route)) {
            if (!(searchHadResults instanceof Boolean found)) return Optional.empty(); // a blank search is not a search
            return Optional.of(found ? UsageFeature.SEARCH_WITH_RESULTS : UsageFeature.SEARCH_NO_RESULTS);
        }
        return UsageRoutes.simpleFeature(method, pattern);
    }

    private static boolean isGuest() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated();
    }
}
