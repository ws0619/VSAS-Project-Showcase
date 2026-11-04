package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationServiceTest {
    private final TestAccounts.MemoryUsers users = new TestAccounts.MemoryUsers();
    // Deliberately differs from the saved work factor to prove saved parameters are used.
    private final AuthenticationService authentication =
            new AuthenticationService(users, new PasswordHasher(2_000));

    private User addMember(String password) {
        User user = TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, password);
        users.save(user);
        return user;
    }

    @Test
    void validCredentialsCreateSessionUsingSavedHashParameters() throws IOException {
        User user = addMember("secret123");
        assertTrue(authentication.currentUser().isEmpty());
        assertTrue(authentication.login("alice", "secret123"));
        assertEquals(user, authentication.currentUser().orElseThrow());
    }

    @Test
    void registrationAndAuthenticationUseTheSamePasswordFormat() throws IOException {
        User user = new RegistrationService(users, new PasswordHasher(1_000), Clock.systemUTC())
                .register(new RegistrationRequest("alice", "secret123", "Alice Example",
                        "alice@example.org", "0400000000", "U001"));
        assertNotEquals("secret123", user.passwordHash());
        assertFalse(user.passwordSalt().isBlank());
        assertTrue(authentication.login("alice", "secret123"));
    }

    @Test
    void wrongPasswordAndUnknownUserCannotCreateSession() throws IOException {
        addMember("secret123");
        assertFalse(authentication.login("alice", "wrong"));
        assertTrue(authentication.currentUser().isEmpty());
        assertFalse(authentication.login("unknown", "secret123"));
        assertTrue(authentication.currentUser().isEmpty());
    }

    @Test
    void failedReloginClearsPreviousSessionAndOnlyUpdatesFailureState() throws IOException {
        User user = addMember("secret123");
        assertTrue(authentication.login("alice", "secret123"));
        assertFalse(authentication.login("alice", "wrong"));
        assertTrue(authentication.currentUser().isEmpty());
        assertEquals(List.of(user.withLoginState(1, false)), users.findAll());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingUsernameIsRejected(String username) throws IOException {
        addMember("secret123");
        assertFalse(authentication.login(username, "secret123"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingPasswordIsRejected(String password) throws IOException {
        addMember("secret123");
        assertFalse(authentication.login("alice", password));
    }

    @Test
    void usernameWhitespaceMatchesRegistrationButPasswordWhitespaceIsSignificant() throws IOException {
        addMember(" secret123 ");
        assertTrue(authentication.login(" alice ", " secret123 "));
        assertFalse(authentication.login("alice", "secret123"));
    }

    @Test
    void administratorAccountAuthenticatesWithoutLosingItsRole() throws IOException {
        users.save(TestAccounts.user("A001", "admin", "Library Admin", Role.ADMIN, "admin123"));
        assertTrue(authentication.login("admin", "admin123"));
        assertTrue(authentication.currentUser().orElseThrow().isAdmin());
    }

    @Test
    void logoutIsIdempotentAndAllowsAnotherUserToLogIn() throws IOException {
        addMember("secret123");
        User bob = TestAccounts.user("U002", "bob", "Bob Example", Role.MEMBER, "other123");
        users.save(bob);
        authentication.login("alice", "secret123");
        authentication.logout();
        authentication.logout();
        assertTrue(authentication.currentUser().isEmpty());
        assertTrue(authentication.login("bob", "other123"));
        assertEquals(bob, authentication.currentUser().orElseThrow());
    }

    @Test
    void corruptOrUnsupportedPasswordMetadataFailsClosed() throws IOException {
        User good = addMember("secret123");
        for (String algorithm : new String[] {"unsupported", null}) {
            var repository = new TestAccounts.MemoryUsers();
            repository.save(new User(good.id(), good.username(), good.fullName(), good.email(),
                    good.phone(), good.role(), good.passwordHash(), good.passwordSalt(), algorithm,
                    good.passwordIterations(), good.createdAt(), good.updatedAt()));
            assertFalse(new AuthenticationService(repository, new PasswordHasher(1_000))
                    .login("alice", "secret123"));
        }
        var repository = new TestAccounts.MemoryUsers();
        repository.save(new User(good.id(), good.username(), good.fullName(), good.email(), good.phone(),
                good.role(), "invalid-base64!", good.passwordSalt(), good.passwordAlgorithm(),
                good.passwordIterations(), good.createdAt(), good.updatedAt()));
        assertFalse(new AuthenticationService(repository, new PasswordHasher(1_000))
                .login("alice", "secret123"));
    }

    @Test
    void storageFailureDoesNotLeaveUserAuthenticated() throws IOException {
        User user = addMember("secret123");
        UserRepository failing = new UserRepository() {
            private int reads;
            public List<User> findAll() throws IOException {
                if (reads++ < 2) return List.of(user);
                throw new IOException("unavailable storage");
            }
            public void save(User value) {}
            public void deleteById(String id) {}
        };
        var service = new AuthenticationService(failing, new PasswordHasher(1_000));
        assertTrue(service.login("alice", "secret123"));
        assertThrows(IOException.class, () -> service.login("alice", "secret123"));
        assertTrue(service.currentUser().isEmpty());
    }
}
