package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T08:00:00Z");

    private final InMemoryUserRepository repository = new InMemoryUserRepository();
    private final PasswordHasher hasher = new PasswordHasher(1_000);
    private final RegistrationService service =
            new RegistrationService(repository, hasher, Clock.fixed(NOW, ZoneOffset.UTC));

    private RegistrationRequest valid() {
        return new RegistrationRequest("alice", "secret123", "Alice Example",
                "alice@example.org", "0400000000", "U001");
    }

    @Test
    void validRegistrationCreatesMemberAccount() throws Exception {
        User user = service.register(valid());

        assertEquals("U001", user.id());
        assertEquals("alice", user.username());
        assertEquals("Alice Example", user.fullName());
        assertEquals("alice@example.org", user.email());
        assertEquals("0400000000", user.phone());
        assertEquals(Role.MEMBER, user.role());
        assertEquals(NOW, user.createdAt());
        assertEquals(NOW, user.updatedAt());
        assertEquals(PasswordHasher.ALGORITHM, user.passwordAlgorithm());
        assertEquals(1_000, user.passwordIterations());
    }

    @Test
    void newUserIsSaved() throws Exception {
        service.register(valid());
        assertTrue(repository.findByUsername("alice").isPresent());
    }

    @Test
    void passwordIsStoredOnlyAsHash() throws Exception {
        User user = service.register(valid());

        assertNotEquals("secret123", user.passwordHash());
        assertTrue(hasher.verify("secret123", user.passwordHash(), user.passwordSalt()));
    }

    @Test
    void everyFieldIsRequired() {
        RegistrationRequest[] missingOne = {
                new RegistrationRequest(null, "secret123", "Alice", "a@e.org", "0400", "U001"),
                new RegistrationRequest("alice", null, "Alice", "a@e.org", "0400", "U001"),
                new RegistrationRequest("alice", "secret123", null, "a@e.org", "0400", "U001"),
                new RegistrationRequest("alice", "secret123", "Alice", null, "0400", "U001"),
                new RegistrationRequest("alice", "secret123", "Alice", "a@e.org", null, "U001"),
                new RegistrationRequest("alice", "secret123", "Alice", "a@e.org", "0400", null),
        };
        for (RegistrationRequest request : missingOne) {
            assertThrows(IllegalArgumentException.class, () -> service.register(request));
        }
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void blankFieldsAreRejected() {
        RegistrationRequest blankUsername = new RegistrationRequest(
                "   ", "secret123", "Alice", "a@e.org", "0400", "U001");
        RegistrationRequest blankPassword = new RegistrationRequest(
                "alice", "   ", "Alice", "a@e.org", "0400", "U001");

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> service.register(blankUsername));
        assertEquals("Username is required.", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.register(blankPassword));
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        service.register(valid());
        RegistrationRequest sameUsername = new RegistrationRequest(
                "alice", "other456", "Another Alice", "alice2@example.org", "0411111111", "U002");

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> service.register(sameUsername));
        assertTrue(e.getMessage().contains("Username"));
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void duplicateIdKeyIsRejected() throws Exception {
        service.register(valid());
        RegistrationRequest sameIdKey = new RegistrationRequest(
                "bob", "other456", "Bob Example", "bob@example.org", "0411111111", "U001");

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> service.register(sameIdKey));
        assertTrue(e.getMessage().contains("ID key"));
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void surroundingSpacesAreRemovedFromDetails() throws Exception {
        User user = service.register(new RegistrationRequest(
                "  alice  ", "secret123", " Alice Example ", " alice@example.org ",
                " 0400000000 ", " U001 "));

        assertEquals("alice", user.username());
        assertEquals("Alice Example", user.fullName());
        assertEquals("U001", user.id());
    }

    @Test
    void nullRequestIsRejected() {
        assertThrows(NullPointerException.class, () -> service.register(null));
    }

    // ----- US21: format checks -----

    @Test
    void badlyFormattedDetailsAreRejectedAndNothingIsSaved() {
        RegistrationRequest badEmail = new RegistrationRequest(
                "alice", "secret123", "Alice", "alice-at-example", "0400000000", "U001");
        RegistrationRequest badPhone = new RegistrationRequest(
                "alice", "secret123", "Alice", "alice@example.org", "0400", "U001");
        RegistrationRequest badIdKey = new RegistrationRequest(
                "alice", "secret123", "Alice", "alice@example.org", "0400000000", "U 1");

        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.register(badEmail))
                .getMessage().startsWith("Email"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.register(badPhone))
                .getMessage().startsWith("Phone number"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.register(badIdKey))
                .getMessage().startsWith("ID key"));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void phoneNumberIsSavedWithoutSpacesOrHyphens() throws Exception {
        User user = service.register(new RegistrationRequest(
                "alice", "secret123", "Alice Example", "alice@example.org", "0400 000-000", "U001"));

        assertEquals("0400000000", user.phone());
    }

    @Test
    void singleFieldChecksMatchRegistrationRules() throws Exception {
        service.register(valid());

        assertThrows(IllegalArgumentException.class, () -> service.checkUsername("alice"));
        assertThrows(IllegalArgumentException.class, () -> service.checkIdKey("U001"));
        assertEquals("bob", service.checkUsername(" bob "));
        assertEquals("U002", service.checkIdKey("U002"));
        assertEquals(" pass word ", service.checkPassword(" pass word "));
    }
}
