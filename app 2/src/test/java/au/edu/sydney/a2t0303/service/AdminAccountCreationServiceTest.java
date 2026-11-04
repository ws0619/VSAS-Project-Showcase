package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.JsonUserRepository;
import au.edu.sydney.a2t0303.storage.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminAccountCreationServiceTest {
    @TempDir Path directory;
    private JsonUserRepository users;
    private final PasswordHasher hasher = new PasswordHasher(1_000);
    private final Instant now = Instant.parse("2026-10-22T00:00:00Z");
    private AuthenticationService authentication;
    private RegistrationService registration;
    private AdminAccountCreationService service;

    @BeforeEach
    void setup() throws IOException {
        users = new JsonUserRepository(directory.resolve("users.json"));
        users.save(TestAccounts.user("A001", "admin", "Existing Admin", Role.ADMIN, "adminSecret"));
        users.save(TestAccounts.user("U001", "alice", "Existing Member", Role.MEMBER, "memberSecret"));
        authentication = new AuthenticationService(users, hasher);
        registration = new RegistrationService(users, hasher, Clock.fixed(now, ZoneOffset.UTC));
        service = new AdminAccountCreationService(registration, authentication);
        assertTrue(authentication.login("admin", "adminSecret"));
    }

    private RegistrationRequest request(String username, String id) {
        return new RegistrationRequest(username, " new secret ", "New Person",
                "new@example.org", "+61 400-000-001", id);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void adminCreatesChosenTypeWithRegistrationRulesAndImmediatePersistentLogin(Role role)
            throws IOException {
        User creator = authentication.currentUser().orElseThrow();
        User created = service.createAccount(request("  newuser  ", " U002 "), role);

        assertEquals("newuser", created.username());
        assertEquals("New Person", created.fullName());
        assertEquals("new@example.org", created.email());
        assertEquals("+61400000001", created.phone());
        assertEquals("U002", created.id());
        assertEquals(role, created.role());
        assertEquals(now, created.createdAt());
        assertEquals(now, created.updatedAt());
        assertEquals(PasswordHasher.ALGORITHM, created.passwordAlgorithm());
        assertTrue(hasher.verify(" new secret ", created.passwordHash(), created.passwordSalt(),
                created.passwordIterations()));
        assertFalse(Files.readString(directory.resolve("users.json")).contains(" new secret "));
        assertEquals(creator, authentication.currentUser().orElseThrow());
        assertEquals(3, users.findAll().size());

        var newSession = new AuthenticationService(
                new JsonUserRepository(directory.resolve("users.json")), hasher);
        assertTrue(newSession.login("newuser", " new secret "));
        assertEquals(role, newSession.currentUser().orElseThrow().role());
    }

    @ParameterizedTest
    @ValueSource(strings = {"anonymous", "guest", "member", "logged-out"})
    void nonAdminsCannotCreateEitherRoleOrProbeFields(String mode) throws IOException {
        switch (mode) {
            case "guest" -> authentication.enterGuest();
            case "member" -> authentication.login("alice", "memberSecret");
            default -> authentication.logout();
        }
        UserRepository inaccessible = new UserRepository() {
            public List<User> findAll() { fail("Unauthorized requests must not query users"); return List.of(); }
            public void save(User user) { fail("Unauthorized requests must not save users"); }
            public void deleteById(String id) { fail("Creation must not delete users"); }
        };
        var guarded = new AdminAccountCreationService(new RegistrationService(inaccessible,
                hasher, Clock.systemUTC()), authentication);
        for (Role role : Role.values()) {
            assertThrows(SecurityException.class, () -> guarded.createAccount(request("bob", "U002"), role));
        }
        assertAll(
                () -> assertThrows(SecurityException.class, () -> guarded.checkUsername("admin")),
                () -> assertThrows(SecurityException.class, () -> guarded.checkPassword("secret")),
                () -> assertThrows(SecurityException.class, () -> guarded.checkFullName("Name")),
                () -> assertThrows(SecurityException.class, () -> guarded.checkEmail("a@example.org")),
                () -> assertThrows(SecurityException.class, () -> guarded.checkPhone("0400000000")),
                () -> assertThrows(SecurityException.class, () -> guarded.checkIdKey("A001")));
        assertEquals(2, users.findAll().size());
    }

    @ParameterizedTest
    @CsvSource({"alice,U002", "newuser,U001", "admin,U003", "newadmin,A001"})
    void identifiersMustBeUniqueAcrossMembersAndAdministrators(String username, String id)
            throws IOException {
        String before = Files.readString(directory.resolve("users.json"));
        assertThrows(IllegalArgumentException.class,
                () -> service.createAccount(request(username, id), Role.ADMIN));
        assertEquals(before, Files.readString(directory.resolve("users.json")));
        assertEquals("admin", authentication.currentUser().orElseThrow().username());
    }

    @Test
    void invalidFieldsOrMissingTypeNeverCreatePartialAccounts() throws IOException {
        var valid = request("bob", "U002");
        List<RegistrationRequest> bad = List.of(
                new RegistrationRequest("bob", " ", "Bob", "bob@example.org", "0400000000", "U002"),
                new RegistrationRequest("bob", "secret", " ", "bob@example.org", "0400000000", "U002"),
                new RegistrationRequest("bob", "secret", "Bob", "@example.org", "0400000000", "U002"),
                new RegistrationRequest("bob", "secret", "Bob", "bob@example.org", "123", "U002"),
                new RegistrationRequest("bob", "secret", "Bob", "bob@example.org", "0400000000", "U 2"));
        for (var request : bad) {
            assertThrows(IllegalArgumentException.class, () -> service.createAccount(request, Role.ADMIN));
        }
        assertThrows(IllegalArgumentException.class, () -> service.createAccount(valid, null));
        assertEquals(2, users.findAll().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void finalSaveRechecksIdentifiersEvenIfFieldWasPreviouslyAvailable(boolean usernameConflict)
            throws IOException {
        service.checkUsername("bob");
        service.checkIdKey("U002");
        users.save(TestAccounts.user(usernameConflict ? "U003" : "U002",
                usernameConflict ? "bob" : "other", "Other User", Role.MEMBER, "otherSecret"));
        assertThrows(IllegalArgumentException.class,
                () -> service.createAccount(request("bob", "U002"), Role.ADMIN));
        assertEquals(3, users.findAll().size());
    }

    @Test
    void selfRegistrationRemainsMemberOnlyAfterAdminCreationWasAdded() throws IOException {
        service.createAccount(request("secondadmin", "A002"), Role.ADMIN);
        User selfRegistered = registration.register(request("selfregistered", "U002"));
        assertEquals(Role.MEMBER, selfRegistered.role());
        assertEquals("admin", authentication.currentUser().orElseThrow().username());
    }

    @Test
    void logoutAfterFieldChecksRevokesPermissionBeforeTheFinalSave() throws IOException {
        service.checkUsername("bob");
        service.checkIdKey("U002");
        authentication.logout();
        assertThrows(SecurityException.class,
                () -> service.createAccount(request("bob", "U002"), Role.ADMIN));
        assertEquals(2, users.findAll().size());
    }
}
