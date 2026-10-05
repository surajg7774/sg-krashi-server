package com.sgkrashi.customer.service.impl;

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
import com.sgkrashi.media.storage.StorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** No database: the erasure repository is a mock, so these pin the rules around it (who may delete, when, and what is cleaned up after). */
class AccountDeletionServiceImplTest {

    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final GoogleAuthService google = mock(GoogleAuthService.class);
    private final AccountErasureRepository erasure = mock(AccountErasureRepository.class);
    private final StorageProvider storage = mock(StorageProvider.class);
    private final LoginRateLimiter limiter = mock(LoginRateLimiter.class);
    private AccountDeletionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountDeletionServiceImpl(currentUser, encoder, google, erasure, storage, limiter, new ObjectMapper());
        when(limiter.tryConsume(anyString())).thenReturn(true);
        when(erasure.openObligations(anyLong())).thenReturn(List.of());
        when(erasure.findScanImageColumns(anyLong())).thenReturn(List.of());
    }

    private static User user(long id, String role, String rawPassword, String googleId, PasswordEncoder enc) {
        Role r = new Role();
        r.setName(role);
        User u = new User();
        u.setId(id);
        u.setName("Asha");
        u.setEmail("asha@example.com");
        u.setRoles(Set.of(r));
        u.setPasswordHash(rawPassword == null ? null : enc.encode(rawPassword));
        u.setGoogleId(googleId);
        return u;
    }

    private void loggedInAs(User u) {
        when(currentUser.getCurrentUser()).thenReturn(u);
    }

    // ---- who may delete ----------------------------------------------------------

    @Test
    void adminAndSuperAdminAccountsCannotSelfDelete() {
        for (String role : List.of("ADMIN", "SUPER_ADMIN")) {
            loggedInAs(user(1, role, "pw", null, encoder));
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> service.deleteCurrentAccount(new DeleteAccountRequest("pw", null)));
            assertTrue(ex.getMessage().contains("super admin"), ex.getMessage());
        }
        verify(erasure, never()).erase(anyLong(), anyString(), anyString());
    }

    // ---- re-authentication -------------------------------------------------------

    @Test
    void aWrongPasswordIsRejectedAndNothingIsErased() {
        loggedInAs(user(1, "CUSTOMER", "right-pw", null, encoder));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.deleteCurrentAccount(new DeleteAccountRequest("wrong-pw", null)));

        assertEquals("Password is incorrect", ex.getMessage());
        verify(erasure, never()).erase(anyLong(), anyString(), anyString());
    }

    @Test
    void noCredentialsAtAllIsRejected() {
        loggedInAs(user(1, "CUSTOMER", "pw", null, encoder));

        assertThrows(ValidationException.class, () -> service.deleteCurrentAccount(null));
        assertThrows(ValidationException.class, () -> service.deleteCurrentAccount(new DeleteAccountRequest("  ", null)));
        verify(erasure, never()).erase(anyLong(), anyString(), anyString());
    }

    @Test
    void theCorrectPasswordErasesWithAnonymousPlaceholderEmailForThatUserOnly() {
        loggedInAs(user(42, "CUSTOMER", "right-pw", null, encoder));

        service.deleteCurrentAccount(new DeleteAccountRequest("right-pw", null));

        verify(erasure).erase(42L, "asha@example.com", "deleted-42@deleted.invalid");
    }

    @Test
    void aGoogleOnlyAccountMustConfirmWithAMatchingFreshGoogleToken() {
        loggedInAs(user(7, "CUSTOMER", null, "google-sub-7", encoder));
        when(google.verifyAndGetSubject("good-token")).thenReturn("google-sub-7");

        service.deleteCurrentAccount(new DeleteAccountRequest(null, "good-token"));

        verify(erasure).erase(7L, "asha@example.com", "deleted-7@deleted.invalid");
    }

    @Test
    void aGoogleTokenForADifferentGoogleAccountIsRejected() {
        loggedInAs(user(7, "CUSTOMER", null, "google-sub-7", encoder));
        when(google.verifyAndGetSubject("someone-elses")).thenReturn("google-sub-999");

        assertThrows(ValidationException.class, () -> service.deleteCurrentAccount(new DeleteAccountRequest(null, "someone-elses")));
        verify(erasure, never()).erase(anyLong(), anyString(), anyString());
    }

    @Test
    void anInvalidGoogleTokenIsA400NotA401SoClientsDoNotLogTheUserOut() {
        loggedInAs(user(7, "CUSTOMER", null, "google-sub-7", encoder));
        when(google.verifyAndGetSubject("bad")).thenThrow(new InvalidGoogleTokenException("expired"));

        assertThrows(ValidationException.class, () -> service.deleteCurrentAccount(new DeleteAccountRequest(null, "bad")));
    }

    @Test
    void aPasswordIsNotAcceptedForAGoogleOnlyAccountWithNoPassword() {
        loggedInAs(user(7, "CUSTOMER", null, "google-sub-7", encoder));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.deleteCurrentAccount(new DeleteAccountRequest("anything", null)));

        assertTrue(ex.getMessage().contains("Google"), ex.getMessage());
    }

    @Test
    void tooManyAttemptsAreRateLimited() {
        loggedInAs(user(1, "CUSTOMER", "pw", null, encoder));
        when(limiter.tryConsume("account-delete:1")).thenReturn(false);

        assertThrows(RateLimitExceededException.class, () -> service.deleteCurrentAccount(new DeleteAccountRequest("pw", null)));
        verify(erasure, never()).erase(anyLong(), anyString(), anyString());
    }

    // ---- open obligations --------------------------------------------------------

    @Test
    void anInFlightOrderBookingOrPayoutBlocksDeletionWithAClearReason() {
        loggedInAs(user(1, "CUSTOMER", "pw", null, encoder));
        when(erasure.openObligations(1L)).thenReturn(List.of("an order that hasn't been delivered yet", "an upcoming booking"));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.deleteCurrentAccount(new DeleteAccountRequest("pw", null)));

        assertTrue(ex.getMessage().contains("an order that hasn't been delivered yet, an upcoming booking"), ex.getMessage());
        verify(erasure, never()).erase(anyLong(), anyString(), anyString());
    }

    @Test
    void obligationsAreOnlyRevealedAfterTheUserHasProvenWhoTheyAre() {
        loggedInAs(user(1, "CUSTOMER", "pw", null, encoder));

        assertThrows(ValidationException.class, () -> service.deleteCurrentAccount(new DeleteAccountRequest("wrong", null)));

        verify(erasure, never()).openObligations(anyLong());
    }

    // ---- images ------------------------------------------------------------------

    @Test
    void scanImagesAreRemovedFromStorageOnlyAfterTheDatabaseErase() {
        loggedInAs(user(1, "CUSTOMER", "pw", null, encoder));
        when(erasure.findScanImageColumns(1L)).thenReturn(List.of(
                "https://img/a.jpg", "[\"https://img/a.jpg\",\"https://img/b.jpg\"]", "https://img/c.jpg"));

        service.deleteCurrentAccount(new DeleteAccountRequest("pw", null));

        var order = org.mockito.Mockito.inOrder(erasure, storage);
        order.verify(erasure).erase(1L, "asha@example.com", "deleted-1@deleted.invalid");
        order.verify(storage).delete("https://img/a.jpg");
        order.verify(storage).delete("https://img/b.jpg");
        order.verify(storage).delete("https://img/c.jpg");
        verify(storage, org.mockito.Mockito.times(3)).delete(anyString()); // a.jpg deduplicated
    }

    @Test
    void aStorageFailureDoesNotFailAnAlreadyCommittedDeletion() {
        loggedInAs(user(1, "CUSTOMER", "pw", null, encoder));
        when(erasure.findScanImageColumns(1L)).thenReturn(List.of("https://img/a.jpg", "https://img/b.jpg"));
        doThrow(new IllegalStateException("cloudinary down")).when(storage).delete("https://img/a.jpg");

        service.deleteCurrentAccount(new DeleteAccountRequest("pw", null));

        verify(erasure).erase(1L, "asha@example.com", "deleted-1@deleted.invalid");
        verify(storage).delete("https://img/b.jpg"); // keeps going after the first failure
    }

    @Test
    void anUnparseableImageListIsSkippedNotFatal() {
        assertEquals(List.of("https://img/ok.jpg"),
                service.scanImageUrls(List.of("https://img/ok.jpg", "[not json", "", "   ")));
    }
}
