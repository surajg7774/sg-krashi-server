package com.sgkrashi.customer.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.auth.entity.Role;
import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.exception.InvalidGoogleTokenException;
import com.sgkrashi.auth.ratelimit.LoginRateLimiter;
import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.auth.service.GoogleAuthService;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.RateLimitExceededException;
import com.sgkrashi.common.exception.ValidationException;
import com.sgkrashi.customer.dto.request.DeleteAccountRequest;
import com.sgkrashi.customer.repository.AccountErasureRepository;
import com.sgkrashi.customer.service.AccountDeletionService;
import com.sgkrashi.media.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Not itself {@code @Transactional}: the all-or-nothing database part lives in
 * {@link AccountErasureRepository#erase}, and the image-storage deletions run
 * only after it has committed — deleting images first would leave scans
 * pointing at nothing if the database step then failed.
 */
@Service
public class AccountDeletionServiceImpl implements AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionServiceImpl.class);
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "SUPER_ADMIN");

    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;
    private final GoogleAuthService googleAuthService;
    private final AccountErasureRepository erasureRepository;
    private final StorageProvider storageProvider;
    private final LoginRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public AccountDeletionServiceImpl(
            CurrentUserProvider currentUserProvider,
            PasswordEncoder passwordEncoder,
            GoogleAuthService googleAuthService,
            AccountErasureRepository erasureRepository,
            StorageProvider storageProvider,
            LoginRateLimiter rateLimiter,
            ObjectMapper objectMapper
    ) {
        this.currentUserProvider = currentUserProvider;
        this.passwordEncoder = passwordEncoder;
        this.googleAuthService = googleAuthService;
        this.erasureRepository = erasureRepository;
        this.storageProvider = storageProvider;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void deleteCurrentAccount(DeleteAccountRequest request) {
        User user = currentUserProvider.getCurrentUser();

        if (user.getRoles().stream().map(Role::getName).anyMatch(ADMIN_ROLES::contains)) {
            throw new BusinessRuleException(
                    "Admin accounts can't be deleted from the app. Please ask a super admin to remove this account.");
        }

        // A stolen access token must not become a free password-guessing oracle.
        if (!rateLimiter.tryConsume("account-delete:" + user.getId())) {
            throw new RateLimitExceededException("Too many attempts. Please try again in a few minutes.");
        }
        confirmIdentity(user, request);

        List<String> blockers = erasureRepository.openObligations(user.getId());
        if (!blockers.isEmpty()) {
            throw new BusinessRuleException("You can't delete your account yet because you still have "
                    + String.join(", ", blockers) + ". Please try again once that is complete.");
        }

        List<String> imageUrls = scanImageUrls(erasureRepository.findScanImageColumns(user.getId()));
        erasureRepository.erase(user.getId(), user.getEmail(), "deleted-" + user.getId() + "@deleted.invalid");
        // No PII in this line: an id only.
        log.info("Account deleted: userId={}", user.getId());

        deleteImagesBestEffort(imageUrls);
    }

    private void confirmIdentity(User user, DeleteAccountRequest request) {
        boolean hasPassword = request != null && request.password() != null && !request.password().isBlank();
        boolean hasGoogleToken = request != null && request.googleIdToken() != null && !request.googleIdToken().isBlank();

        if (hasPassword && user.getPasswordHash() != null) {
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new ValidationException("Password is incorrect");
            }
            return;
        }
        if (hasGoogleToken && user.getGoogleId() != null) {
            String subject;
            try {
                subject = googleAuthService.verifyAndGetSubject(request.googleIdToken());
            } catch (InvalidGoogleTokenException ex) {
                // Not a 401: clients treat a 401 as "session expired" and would log the user out mid-dialog.
                throw new ValidationException("Google confirmation failed. Please try again.");
            }
            if (!user.getGoogleId().equals(subject)) {
                throw new ValidationException("That Google account doesn't match this account.");
            }
            return;
        }
        throw new ValidationException(user.getPasswordHash() != null
                ? "Please enter your password to confirm."
                : "Please confirm with Google to delete this account.");
    }

    /** Both columns may hold a URL ({@code image_url}) or a JSON array of URLs ({@code image_urls}); collect every distinct URL. */
    List<String> scanImageUrls(List<String> rawColumns) {
        Set<String> urls = new LinkedHashSet<>();
        for (String raw : rawColumns) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.startsWith("[")) {
                try {
                    urls.addAll(objectMapper.readValue(trimmed, new TypeReference<List<String>>() { }));
                } catch (Exception ex) {
                    log.warn("Skipping an unparseable scan image list during account deletion");
                }
            } else {
                urls.add(trimmed);
            }
        }
        return List.copyOf(urls);
    }

    private void deleteImagesBestEffort(List<String> urls) {
        int failed = 0;
        for (String url : urls) {
            try {
                storageProvider.delete(url);
            } catch (RuntimeException ex) {
                failed++;
            }
        }
        if (failed > 0) {
            // The account is already erased; leftover files are an ops clean-up, not a user-facing failure.
            log.warn("Account deletion: {} of {} scan image(s) could not be removed from storage", failed, urls.size());
        }
    }
}
