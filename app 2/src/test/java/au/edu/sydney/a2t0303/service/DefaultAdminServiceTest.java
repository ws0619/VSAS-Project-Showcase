package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.storage.JsonUserRepository;
import au.edu.sydney.a2t0303.storage.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAdminServiceTest {
    @TempDir Path directory;
    private final PasswordHasher hasher = new PasswordHasher(1_000);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);

    private DefaultAdminService service(UserRepository users) {
        return new DefaultAdminService(users, hasher, clock);
    }

    @Test
    void freshStorageHasOneAdminWhoCanLogInUsingDocumentedCredentials() throws IOException {
        var users = new JsonUserRepository(directory.resolve("users.json"));
        assertTrue(service(users).createIfMissing());
        assertEquals(1, users.findAll().size());
        var admin = users.findAll().getFirst();
        assertTrue(admin.isAdmin());
        assertEquals(DefaultAdminService.DEFAULT_ID, admin.id());
        assertEquals(clock.instant(), admin.createdAt());
        assertEquals(clock.instant(), admin.updatedAt());
        assertEquals(PasswordHasher.ALGORITHM, admin.passwordAlgorithm());
        assertEquals(hasher.getIterations(), admin.passwordIterations());
        assertNotEquals(DefaultAdminService.DEFAULT_PASSWORD, admin.passwordHash());
        assertFalse(Files.readString(directory.resolve("users.json"))
                .contains(DefaultAdminService.DEFAULT_PASSWORD));
        assertTrue(new AuthenticationService(users, hasher).login(
                DefaultAdminService.DEFAULT_USERNAME, DefaultAdminService.DEFAULT_PASSWORD));
    }

    @Test
    void restartingDoesNotResetAnExistingAdminsChangedCredentialsOrProfile() throws IOException {
        Path file = directory.resolve("users.json");
        var users = new JsonUserRepository(file);
        service(users).createIfMissing();
        var admin = users.findAll().getFirst();
        var updated = TestAccounts.user(admin.id(), "renamed-admin", "Updated Administrator",
                Role.ADMIN, "changedPassword");
        // The repository keeps creation time and role immutable.
        users.update(new au.edu.sydney.a2t0303.model.User(updated.id(), updated.username(),
                updated.fullName(), updated.email(), updated.phone(), updated.role(),
                updated.passwordHash(), updated.passwordSalt(), updated.passwordAlgorithm(),
                updated.passwordIterations(), admin.createdAt(), admin.updatedAt()));
        String before = Files.readString(file);

        var restarted = new JsonUserRepository(file);
        assertFalse(service(restarted).createIfMissing());
        assertEquals(before, Files.readString(file));
        assertTrue(new AuthenticationService(restarted, hasher)
                .login("renamed-admin", "changedPassword"));
        assertFalse(new AuthenticationService(restarted, hasher).login(
                DefaultAdminService.DEFAULT_USERNAME, DefaultAdminService.DEFAULT_PASSWORD));
    }

    @Test
    void memberOnlyStorageKeepsMembersAndGainsOneAdminExactlyOnce() throws IOException {
        var users = new TestAccounts.MemoryUsers();
        var alice = TestAccounts.user("U001", "alice", "Alice", Role.MEMBER, "memberSecret");
        users.save(alice);
        assertTrue(service(users).createIfMissing());
        assertFalse(service(users).createIfMissing());
        assertEquals(2, users.findAll().size());
        assertEquals(alice, users.findById("U001").orElseThrow());
    }

    @Test
    void anyExistingAdministratorPreventsAnAdditionalDefaultAccount() throws IOException {
        var users = new TestAccounts.MemoryUsers();
        var admin = TestAccounts.user("A900", "librarian", "Existing Admin", Role.ADMIN, "keepMe");
        users.save(admin);
        assertFalse(service(users).createIfMissing());
        assertEquals(List.of(admin), users.findAll());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void conflictingMemberUsernameOrIdIsNeverPromotedOrOverwritten(boolean usernameConflict)
            throws IOException {
        var users = new TestAccounts.MemoryUsers();
        var member = TestAccounts.user(usernameConflict ? "U001" : DefaultAdminService.DEFAULT_ID,
                usernameConflict ? DefaultAdminService.DEFAULT_USERNAME : "alice",
                "Existing Member", Role.MEMBER, "keepMe");
        users.save(member);
        assertThrows(IOException.class, () -> service(users).createIfMissing());
        assertEquals(List.of(member), users.findAll());
        assertFalse(users.findAll().getFirst().isAdmin());
    }

    @Test
    void corruptStorageIsReportedAndNeverReset() throws IOException {
        Path file = directory.resolve("users.json");
        Files.writeString(file, "{broken");
        var users = new JsonUserRepository(file);
        assertThrows(IOException.class, () -> service(users).createIfMissing());
        assertEquals("{broken", Files.readString(file));
    }

    @Test
    void writeFailureIsReportedRatherThanClaimingAdminWasCreated() {
        UserRepository users = new UserRepository() {
            public List<au.edu.sydney.a2t0303.model.User> findAll() { return List.of(); }
            public void save(au.edu.sydney.a2t0303.model.User user) throws IOException {
                throw new IOException("unavailable");
            }
            public void deleteById(String id) {}
        };
        assertThrows(IOException.class, () -> service(users).createIfMissing());
    }
}
