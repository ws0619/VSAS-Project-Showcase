package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuestSessionTest {
    private final TestAccounts.MemoryUsers users = new TestAccounts.MemoryUsers();
    private final AuthenticationService authentication =
            new AuthenticationService(users, new PasswordHasher(1_000));

    @Test
    void enteringGuestDoesNotCreateAnAccountOrAuthenticate() {
        authentication.enterGuest();
        assertTrue(authentication.isGuest());
        assertTrue(authentication.currentUser().isEmpty());
        assertTrue(users.findAll().isEmpty());
    }

    @Test
    void enteringGuestClearsPreviousAuthenticatedIdentity() throws IOException {
        users.save(TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, "secret123"));
        assertTrue(authentication.login("alice", "secret123"));
        authentication.enterGuest();
        assertTrue(authentication.isGuest());
        assertTrue(authentication.currentUser().isEmpty());
        assertThrows(SecurityException.class, authentication::requireUploadPermission);
        assertThrows(SecurityException.class, authentication::requireDownloadPermission);
        assertEquals(1, users.findAll().size());
    }

    @Test
    void guestEntryNeverReadsAccountStorage() {
        UserRepository unavailable = new UserRepository() {
            public List<User> findAll() throws IOException { throw new IOException("unavailable"); }
            public void save(User user) { fail("Guest entry must not save an account"); }
            public void deleteById(String id) { fail("Guest entry must not delete accounts"); }
        };
        var service = new AuthenticationService(unavailable, new PasswordHasher(1_000));
        assertDoesNotThrow(service::enterGuest);
        assertTrue(service.isGuest());
        assertThrows(SecurityException.class, service::requireUploadPermission);
        assertThrows(SecurityException.class, service::requireDownloadPermission);
    }

    @Test
    void guestPermissionErrorsAskForLoginInEnglish() {
        authentication.enterGuest();
        assertEquals("Please log in to upload a scroll.",
                assertThrows(SecurityException.class, authentication::requireUploadPermission).getMessage());
        assertEquals("Please log in to download a scroll.",
                assertThrows(SecurityException.class, authentication::requireDownloadPermission).getMessage());
        assertTrue(authentication.isGuest());
    }

    @Test
    void startMenuSessionAlsoCannotPerformRestrictedOperations() {
        assertFalse(authentication.isGuest());
        assertThrows(SecurityException.class, authentication::requireUploadPermission);
        assertThrows(SecurityException.class, authentication::requireDownloadPermission);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void successfulLoginLeavesGuestModeAndAllowsRegisteredRoles(Role role) throws IOException {
        User user = TestAccounts.user("U001", "alice", "Alice Example", role, "secret123");
        users.save(user);
        authentication.enterGuest();
        assertTrue(authentication.login("alice", "secret123"));
        assertFalse(authentication.isGuest());
        assertSame(user, authentication.requireUploadPermission());
        assertSame(user, authentication.requireDownloadPermission());
    }

    @Test
    void failedLoginLeavesNoGuestOrRegisteredSession() throws IOException {
        authentication.enterGuest();
        assertFalse(authentication.login("missing", "wrong"));
        assertFalse(authentication.isGuest());
        assertTrue(authentication.currentUser().isEmpty());
        assertThrows(SecurityException.class, authentication::requireDownloadPermission);
    }

    @Test
    void loginStorageFailureClearsGuestState() {
        UserRepository unavailable = new UserRepository() {
            public List<User> findAll() throws IOException { throw new IOException("unavailable"); }
            public void save(User user) {}
            public void deleteById(String id) {}
        };
        var service = new AuthenticationService(unavailable, new PasswordHasher(1_000));
        service.enterGuest();
        assertThrows(IOException.class, () -> service.login("alice", "secret123"));
        assertFalse(service.isGuest());
        assertTrue(service.currentUser().isEmpty());
    }

    @Test
    void leavingGuestIsIdempotentAndARepeatedVisitHasNoAccountEffects() {
        authentication.enterGuest();
        authentication.logout();
        authentication.logout();
        assertFalse(authentication.isGuest());
        assertTrue(authentication.currentUser().isEmpty());
        authentication.enterGuest();
        assertTrue(authentication.isGuest());
        assertTrue(users.findAll().isEmpty());
    }

    @Test
    void loggedOutUserLosesBothRestrictedPermissions() throws IOException {
        users.save(TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, "secret123"));
        authentication.login("alice", "secret123");
        authentication.logout();
        assertThrows(SecurityException.class, authentication::requireUploadPermission);
        assertThrows(SecurityException.class, authentication::requireDownloadPermission);
    }
}
