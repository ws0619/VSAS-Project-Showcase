package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.storage.JsonScrollCatalog;
import au.edu.sydney.a2t0303.storage.LocalScrollFileStorage;
import au.edu.sydney.a2t0303.storage.ScrollRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuestUploadPermissionTest {
    @TempDir Path directory;
    private final TestAccounts.MemoryUsers users = new TestAccounts.MemoryUsers();
    private final AuthenticationService authentication =
            new AuthenticationService(users, new PasswordHasher(1_000));

    private ScrollUploadService service(ScrollRepository repository) {
        return new ScrollUploadService(new ScrollUploadValidator(),
                new LocalScrollFileStorage(directory), repository, Clock.systemUTC(), authentication);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"U001", "invented-user"})
    void guestCannotBypassUploadBySupplyingAnUploaderId(String claimedId) throws IOException {
        users.save(TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, "secret123"));
        authentication.enterGuest();
        Path source = directory.resolve("notes.txt");
        Files.writeString(source, "Keep these bytes");
        var catalog = new JsonScrollCatalog(directory.resolve("scrolls.json"));
        String original = Files.readString(directory.resolve("scrolls.json"));
        var upload = service(catalog);
        var error = assertThrows(SecurityException.class,
                () -> upload.upload("S001", "Notes", source.toString(), claimedId));
        assertEquals("Please log in to upload a scroll.", error.getMessage());
        assertTrue(catalog.findAll().isEmpty());
        assertEquals(original, Files.readString(directory.resolve("scrolls.json")));
        assertFalse(Files.exists(directory.resolve("scroll-files")));
        assertEquals("Keep these bytes", Files.readString(source));
    }

    @Test
    void permissionIsCheckedBeforeRepositoryReadsOrFileValidation() {
        authentication.enterGuest();
        ScrollRepository forbidden = new ScrollRepository() {
            public List<Scroll> findAll() { fail("Guest upload must not read metadata"); return List.of(); }
            public void save(Scroll scroll) { fail("Guest upload must not save metadata"); }
        };
        assertThrows(SecurityException.class,
                () -> service(forbidden).upload(null, null, null, "U001"));
        assertFalse(Files.exists(directory.resolve("scroll-files")));
    }

    @Test
    void returningToStartMenuDoesNotPermitDirectServiceUpload() throws IOException {
        var catalog = new JsonScrollCatalog(directory.resolve("scrolls.json"));
        authentication.enterGuest();
        authentication.logout();
        assertThrows(SecurityException.class,
                () -> service(catalog).upload("S001", "Notes", "missing.txt", "U001"));
        assertTrue(catalog.findAll().isEmpty());
    }

    @Test
    void authenticatedUploadStillWorksButGuestTransitionRevokesIt() throws IOException {
        users.save(TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, "secret123"));
        Path source = directory.resolve("notes.txt");
        Files.writeString(source, "Original binary contents");
        var catalog = new JsonScrollCatalog(directory.resolve("scrolls.json"));
        var upload = service(catalog);
        assertTrue(authentication.login("alice", "secret123"));
        Scroll stored = upload.upload("S001", "Notes", source.toString(), "U001");
        assertEquals("U001", stored.uploaderId());
        assertEquals("Original binary contents", au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.readString(directory.resolve(stored.storagePath())));
        authentication.enterGuest();
        assertThrows(SecurityException.class,
                () -> upload.upload("S002", "More notes", source.toString(), "U001"));
        assertEquals(1, catalog.findAll().size());
        try (var files = Files.list(directory.resolve("scroll-files"))) {
            assertEquals(1, files.count());
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"U002"})
    void authenticatedCallerCannotUploadAsAnotherUser(String claimedId) throws IOException {
        users.save(TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, "secret123"));
        authentication.login("alice", "secret123");
        var catalog = new JsonScrollCatalog(directory.resolve("scrolls.json"));
        assertEquals("The uploader ID must match the logged-in user.",
                assertThrows(SecurityException.class,
                        () -> service(catalog).upload("S001", "Notes", "missing.txt", claimedId)).getMessage());
        assertTrue(catalog.findAll().isEmpty());
        assertFalse(Files.exists(directory.resolve("scroll-files")));
    }
}
