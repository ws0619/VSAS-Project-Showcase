package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.JsonUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ProfileServiceTest {

    private static final Instant CREATED = Instant.parse("2026-10-14T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    @TempDir
    Path directory;

    private final PasswordHasher hasher = new PasswordHasher(1_000);
    private final InMemoryUserRepository repository = new InMemoryUserRepository();
    private final ProfileService service = serviceAt(NOW);

    private ProfileService serviceAt(Instant instant) {
        return new ProfileService(repository, hasher, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private User alice() {
        PasswordHasher.HashedPassword hashed = hasher.hash("secret123");
        return new User("U001", "alice", "Alice Example", "alice@example.org", "0400000000",
                Role.MEMBER, hashed.hash(), hashed.salt(), PasswordHasher.ALGORITHM,
                hasher.getIterations(), CREATED, CREATED);
    }

    private User saveAlice() {
        User alice = alice();
        repository.save(alice);
        return alice;
    }

    @Test
    void updateDetailsChangesOnlyTheGivenFields() throws IOException {
        User original = saveAlice();

        User updated = service.updateDetails("U001", "Alice Updated", null, " 0411111111 ");

        assertEquals("Alice Updated", updated.fullName());
        assertEquals(original.email(), updated.email());
        assertEquals("0411111111", updated.phone());
        assertEquals(NOW, updated.updatedAt());
        assertEquals(updated, repository.findById("U001").orElseThrow());
    }

    @Test
    void updateDetailsKeepsIdUsernameRoleAndPassword() throws IOException {
        User original = saveAlice();

        User updated = service.updateDetails("U001", "Alice Updated", "new@example.org", null);

        assertEquals(original.id(), updated.id());
        assertEquals(original.username(), updated.username());
        assertEquals(original.role(), updated.role());
        assertEquals(original.passwordHash(), updated.passwordHash());
        assertEquals(original.passwordSalt(), updated.passwordSalt());
        assertEquals(original.createdAt(), updated.createdAt());
    }

    @Test
    void blankDetailIsRejected() {
        saveAlice();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.updateDetails("U001", "   ", null, null));

        assertEquals("Full name cannot be empty.", e.getMessage());
    }

    @Test
    void unchangedDetailsAreNotSaved() throws IOException {
        User original = saveAlice();

        User result = service.updateDetails("U001", "Alice Example", null, null);

        assertEquals(original, result);
        assertEquals(CREATED, repository.findById("U001").orElseThrow().updatedAt());
    }

    @Test
    void unknownUserIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateDetails("U999", "Someone", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("U999", "secret123", "newSecret456"));
    }

    @Test
    void wrongCurrentPasswordIsRejectedAndPasswordUnchanged() throws IOException {
        User original = saveAlice();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("U001", "wrong", "newSecret456"));

        assertEquals("Current password is incorrect.", e.getMessage());
        assertEquals(original.passwordHash(), repository.findById("U001").orElseThrow().passwordHash());
    }

    @Test
    void changedPasswordIsStoredAsANewSaltedHash() throws IOException {
        User original = saveAlice();

        User updated = service.changePassword("U001", "secret123", "newSecret456");

        assertNotEquals("newSecret456", updated.passwordHash());
        assertNotEquals(original.passwordSalt(), updated.passwordSalt());
        assertEquals(PasswordHasher.ALGORITHM, updated.passwordAlgorithm());
        assertTrue(hasher.verify("newSecret456", updated.passwordHash(),
                updated.passwordSalt(), updated.passwordIterations()));
        assertFalse(hasher.verify("secret123", updated.passwordHash(),
                updated.passwordSalt(), updated.passwordIterations()));
    }

    @Test
    void newPasswordMustBePresentAndDifferent() {
        saveAlice();

        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("U001", "secret123", "   "));
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("U001", "secret123", "secret123"));
    }

    @Test
    void changingNameAfterPasswordKeepsTheNewPassword() throws IOException {
        saveAlice();

        service.changePassword("U001", "secret123", "newSecret456");
        User afterName = service.updateDetails("U001", "Alice Updated", null, null);

        assertTrue(hasher.verify("newSecret456", afterName.passwordHash(),
                afterName.passwordSalt(), afterName.passwordIterations()));
        assertEquals("Alice Updated", repository.findById("U001").orElseThrow().fullName());
    }

    @Test
    void updatedTimeNeverGoesBackwards() throws IOException {
        saveAlice();

        User updated = serviceAt(CREATED.minusSeconds(3600))
                .updateDetails("U001", "Alice Updated", null, null);

        assertEquals(CREATED, updated.updatedAt());
    }

    @Test
    void changesSurviveRestart() throws IOException {
        Path file = directory.resolve("users.json");
        var json = new JsonUserRepository(file);
        json.save(alice());
        var jsonService = new ProfileService(json, hasher, Clock.fixed(NOW, ZoneOffset.UTC));

        jsonService.updateDetails("U001", "Alice Updated", "new@example.org", null);
        jsonService.changePassword("U001", "secret123", "newSecret456");

        var restarted = new JsonUserRepository(file);
        assertEquals("Alice Updated", restarted.findById("U001").orElseThrow().fullName());
        assertTrue(new AuthenticationService(restarted, hasher).login("alice", "newSecret456"));
        assertFalse(new AuthenticationService(restarted, hasher).login("alice", "secret123"));
    }

    @Test
    void changedEmailAndPhoneMustBeValid() throws IOException {
        User original = saveAlice();

        assertThrows(IllegalArgumentException.class,
                () -> service.updateDetails("U001", null, "not-an-email", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateDetails("U001", null, null, "12345"));
        assertEquals(original, repository.findById("U001").orElseThrow());

        User updated = service.updateDetails("U001", null, null, "0411 111-111");
        assertEquals("0411111111", updated.phone());
    }
}
