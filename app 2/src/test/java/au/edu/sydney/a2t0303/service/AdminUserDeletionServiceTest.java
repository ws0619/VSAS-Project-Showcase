package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.storage.JsonScrollCatalog;
import au.edu.sydney.a2t0303.storage.LocalScrollFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AdminUserDeletionServiceTest {

    @TempDir
    Path directory;

    private final TestAccounts.MemoryUsers users = new TestAccounts.MemoryUsers();
    private final PasswordHasher hasher = new PasswordHasher(1_000);
    private final AuthenticationService authentication = new AuthenticationService(users, hasher);
    private JsonScrollCatalog scrolls;
    private LocalScrollFileStorage storage;
    private AdminUserDeletionService service;

    @BeforeEach
    void setUp() throws IOException {
        users.save(TestAccounts.user("A001", "admin", "Library Admin", Role.ADMIN, "adminSecret"));
        users.save(TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, "aliceSecret"));
        users.save(TestAccounts.user("U002", "bob", "Bob Example", Role.MEMBER, "bobSecret"));
        scrolls = new JsonScrollCatalog(directory.resolve("data/scrolls.json"));
        storage = new LocalScrollFileStorage(directory.resolve("data"));
        service = new AdminUserDeletionService(users, scrolls,
                new ScrollRemoveService(scrolls, storage), authentication);
    }

    /** Stores a real file and its catalog record; returns the stored file's path. */
    private Path upload(String scrollId, String uploaderId) throws IOException {
        Path source = directory.resolve(scrollId + ".txt");
        Files.writeString(source, "content of " + scrollId);
        var stored = storage.store(source);
        Instant now = Instant.parse("2026-10-20T00:00:00Z");
        scrolls.save(new Scroll(scrollId, "Scroll " + scrollId, uploaderId, now, now,
                stored.storagePath(), stored.originalFileName(), stored.sizeBytes(), stored.sha256()));
        return directory.resolve("data").resolve(stored.storagePath());
    }

    @Test
    void adminDeletesUserTogetherWithOnlyTheirScrolls() throws IOException {
        Path aliceFile1 = upload("S001", "U001");
        Path aliceFile2 = upload("S002", "U001");
        Path bobFile = upload("S003", "U002");
        authentication.login("admin", "adminSecret");

        var deleted = service.deleteUser("U001");

        assertEquals("alice", deleted.user().username());
        assertEquals(2, deleted.scrolls().size());
        assertTrue(users.findById("U001").isEmpty());
        assertTrue(scrolls.findById("S001").isEmpty());
        assertTrue(scrolls.findById("S002").isEmpty());
        assertFalse(Files.exists(aliceFile1));
        assertFalse(Files.exists(aliceFile2));
        assertTrue(scrolls.findById("S003").isPresent());
        assertTrue(Files.exists(bobFile));
        assertTrue(users.findById("U002").isPresent());
    }

    @Test
    void deletedUserCanNoLongerLogIn() throws IOException {
        authentication.login("admin", "adminSecret");
        service.deleteUser("U002");

        assertFalse(new AuthenticationService(users, hasher).login("bob", "bobSecret"));
    }

    @Test
    void userWithoutScrollsIsDeleted() throws IOException {
        authentication.login("admin", "adminSecret");

        assertEquals(0, service.deleteUser(" U002 ").scrolls().size());
        assertTrue(users.findById("U002").isEmpty());
    }

    @Test
    void adminCannotDeleteTheirOwnAccount() throws IOException {
        authentication.login("admin", "adminSecret");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.deleteUser("A001"));

        assertEquals("You cannot delete your own account.", e.getMessage());
        assertTrue(users.findById("A001").isPresent());
    }

    @Test
    void unknownOrBlankIdKeyIsRejected() throws IOException {
        authentication.login("admin", "adminSecret");

        assertThrows(IllegalArgumentException.class, () -> service.deleteUser("U999"));
        assertThrows(IllegalArgumentException.class, () -> service.deleteUser("  "));
        assertEquals(3, users.findAll().size());
    }

    @Test
    void membersAndGuestsCannotDeleteUsers() throws IOException {
        upload("S001", "U001");

        assertThrows(SecurityException.class, () -> service.deleteUser("U002"));
        authentication.enterGuest();
        assertThrows(SecurityException.class, () -> service.deleteUser("U002"));
        authentication.login("alice", "aliceSecret");
        assertThrows(SecurityException.class, () -> service.preview("U002"));

        assertEquals(3, users.findAll().size());
        assertEquals(1, scrolls.findAll().size());
    }

    @Test
    void previewListsScrollsWithoutDeletingAnything() throws IOException {
        upload("S001", "U001");
        authentication.login("admin", "adminSecret");

        var preview = service.preview("U001");

        assertEquals(1, preview.scrolls().size());
        assertTrue(users.findById("U001").isPresent());
        assertTrue(scrolls.findById("S001").isPresent());
    }
}
