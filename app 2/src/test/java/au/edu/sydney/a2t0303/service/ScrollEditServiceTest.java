package au.edu.sydney.a2t0303.service;


import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.User;
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

class ScrollEditServiceTest {

    @TempDir
    Path tempDir;

    private Path dataDir;
    private JsonScrollCatalog repository;
    private LocalScrollFileStorage fileStorage;
    private ScrollEditService service;

    @BeforeEach
    void setUp() throws IOException {
        dataDir = tempDir.resolve("data");

        repository = new JsonScrollCatalog(
                dataDir.resolve("scrolls.json"));

        fileStorage = new LocalScrollFileStorage(dataDir);

        service = new ScrollEditService(
                repository, fileStorage);
    }

    private User createUser(String id, Role role) {
        Instant now = Instant.parse(
                "2026-10-19T08:00:00Z");

        return new User(
                id,
                "user-" + id,
                "Test User",
                id + "@example.com",
                "0400000000",
                role,
                "hash",
                "salt",
                "PBKDF2WithHmacSHA256",
                10000,
                now,
                now
        );
    }

    private Scroll createScroll(
            String id,
            String name,
            String ownerId
    ) throws IOException {

        Path source = tempDir.resolve(id + ".txt");
        Files.writeString(source, "Original content");

        LocalScrollFileStorage.StoredFile stored =
                fileStorage.store(source);

        Instant now = Instant.parse(
                "2026-10-18T08:00:00Z");

        Scroll scroll = new Scroll(
                id,
                name,
                ownerId,
                now,
                now,
                stored.storagePath(),
                stored.originalFileName(),
                stored.sizeBytes(),
                stored.sha256(),
                0L
        );

        repository.save(scroll);
        return scroll;
    }

    @Test
    void ownerCanRenameOwnScroll() throws IOException {
        User owner = createUser("U001", Role.MEMBER);
        Scroll original = createScroll(
                "S001", "Old Notes", owner.id());

        Scroll updated = service.edit(
                owner, "S001", "New Notes", null);

        assertEquals("New Notes", updated.name());
        assertEquals(original.id(), updated.id());
        assertEquals(original.uploaderId(),
                updated.uploaderId());
        assertEquals(original.uploadedAt(),
                updated.uploadedAt());
        assertEquals(original.storagePath(),
                updated.storagePath());

        assertEquals("New Notes",
                repository.findById("S001")
                        .orElseThrow().name());
    }

    @Test
    void ownerCanReplaceOwnFile() throws IOException {
        User owner = createUser("U001", Role.MEMBER);

        Scroll original = createScroll(
                "S001", "Java Notes", owner.id());

        Path replacement =
                tempDir.resolve("new-notes.txt");

        Files.writeString(replacement, "Updated content");

        Scroll updated = service.edit(
                owner, "S001", null, replacement);

        assertEquals(original.id(), updated.id());
        assertEquals(original.name(), updated.name());
        assertEquals(original.uploaderId(),
                updated.uploaderId());
        assertEquals(original.uploadedAt(),
                updated.uploadedAt());

        assertNotEquals(original.storagePath(),
                updated.storagePath());

        Path newStoredFile =
                dataDir.resolve(updated.storagePath());

        assertEquals("Updated content",
                au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.readString(newStoredFile));

        assertTrue(Files.exists(
                dataDir.resolve(original.storagePath())));

        assertEquals(2, updated.versions().size());
        assertEquals(original.storagePath(), updated.versions().get(0).storagePath());

        assertEquals(updated,
                repository.findById("S001").orElseThrow());
    }

    @Test
    void memberCannotEditAnotherUsersScroll()
            throws IOException {

        User owner = createUser("U001", Role.MEMBER);
        User another = createUser("U002", Role.MEMBER);

        Scroll original = createScroll(
                "S001", "Java Notes", owner.id());

        assertThrows(SecurityException.class,
                () -> service.edit(
                        another, "S001", "Changed", null));

        assertEquals(original,
                repository.findById("S001").orElseThrow());
    }

    @Test
    void adminCanEditAnotherUsersScroll()
            throws IOException {

        User owner = createUser("U001", Role.MEMBER);
        User admin = createUser("ADMIN", Role.ADMIN);

        createScroll("S001", "Java Notes", owner.id());

        Scroll updated = service.edit(
                admin, "S001", "Admin Updated", null);

        assertEquals("Admin Updated", updated.name());

        // Original uploader must remain unchanged
        assertEquals(owner.id(), updated.uploaderId());
    }

    @Test
    void duplicateNameIsRejected() throws IOException {
        User owner = createUser("U001", Role.MEMBER);

        Scroll original = createScroll(
                "S001", "Java Notes", owner.id());

        createScroll(
                "S002", "Python Notes", owner.id());

        assertThrows(IllegalArgumentException.class,
                () -> service.edit(
                        owner, "S001", "Python Notes", null));

        assertEquals(original,
                repository.findById("S001").orElseThrow());
    }

    @Test
    void invalidReplacementDoesNotChangeScroll()
            throws IOException {

        User owner = createUser("U001", Role.MEMBER);

        Scroll original = createScroll(
                "S001", "Java Notes", owner.id());

        Path missing = tempDir.resolve("missing.txt");

        assertThrows(IOException.class,
                () -> service.edit(
                        owner, "S001", null, missing));

        assertEquals(original,
                repository.findById("S001").orElseThrow());

        assertTrue(Files.exists(
                dataDir.resolve(original.storagePath())));
    }

    @Test
    void renamePreservesDownloadCount() throws IOException {
        User owner = createUser("U001", Role.MEMBER);

        createScroll("S001", "Java Notes", owner.id());

        repository.incrementDownloadCount("S001");
        repository.incrementDownloadCount("S001");

        Scroll updated = service.edit(
                owner, "S001", "New Notes", null);

        assertEquals(2L, updated.downloadCount());

        assertEquals(2L,
                repository.findById("S001")
                        .orElseThrow().downloadCount());
    }

    @Test
    void editRequiresAtLeastOneChange()
            throws IOException {

        User owner = createUser("U001", Role.MEMBER);

        createScroll("S001", "Java Notes", owner.id());

        assertThrows(IllegalArgumentException.class,
                () -> service.edit(
                        owner, "S001", null, null));
    }

    @Test
    void unknownScrollIsRejected() {
        User owner = createUser("U001", Role.MEMBER);

        assertThrows(IllegalArgumentException.class,
                () -> service.edit(
                        owner, "UNKNOWN", "New Name", null));
    }
}
