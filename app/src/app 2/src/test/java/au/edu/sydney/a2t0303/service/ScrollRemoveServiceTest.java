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

class ScrollRemoveServiceTest {

    @TempDir
    Path tempDir;

    private Path dataDir;
    private JsonScrollCatalog repository;
    private LocalScrollFileStorage fileStorage;
    private ScrollRemoveService service;

    @BeforeEach
    void setUp() throws IOException {
        dataDir = tempDir.resolve("data");

        repository = new JsonScrollCatalog(
                dataDir.resolve("scrolls.json"));

        fileStorage = new LocalScrollFileStorage(dataDir);

        service = new ScrollRemoveService(
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
            String ownerId) throws IOException {

        Path source = tempDir.resolve(id + ".txt");
        Files.writeString(source, "Original content");

        var stored = fileStorage.store(source);

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
    void ownerCanRemoveOwnScroll() throws IOException {

        User owner = createUser("U001", Role.MEMBER);

        Scroll original = createScroll(
                "S001", "Java Notes", owner.id());

        Path storedFile =
                dataDir.resolve(original.storagePath());

        assertTrue(Files.exists(storedFile));

        service.remove(owner, "S001");

        // Scroll must disappear from the catalog
        assertTrue(repository.findById("S001").isEmpty());
        assertTrue(repository.findAll().isEmpty());

        // Physical file must also be removed
        assertFalse(Files.exists(storedFile));
    }

    @Test
    void memberCannotRemoveAnotherUsersScroll()
            throws IOException {

        User owner = createUser("U001", Role.MEMBER);
        User other = createUser("U002", Role.MEMBER);

        Scroll original = createScroll(
                "S001", "Java Notes", owner.id());

        assertThrows(SecurityException.class,
                () -> service.remove(other, "S001"));

        assertEquals(original,
                repository.findById("S001").orElseThrow());

        assertTrue(Files.exists(
                dataDir.resolve(original.storagePath())));
    }

    @Test
    void adminCanRemoveAnotherUsersScroll()
            throws IOException {

        User owner = createUser("U001", Role.MEMBER);
        User admin = createUser("ADMIN", Role.ADMIN);

        Scroll original = createScroll(
                "S001", "Java Notes", owner.id());

        service.remove(admin, "S001");

        assertTrue(repository.findById("S001").isEmpty());

        assertFalse(Files.exists(
                dataDir.resolve(original.storagePath())));
    }

    @Test
    void guestCannotRemoveScroll() throws IOException {

        Scroll original = createScroll(
                "S001", "Java Notes", "U001");

        assertThrows(SecurityException.class,
                () -> service.remove(null, "S001"));

        assertEquals(original,
                repository.findById("S001").orElseThrow());
    }

    @Test
    void unknownScrollCannotBeRemoved() {

        User owner = createUser("U001", Role.MEMBER);

        assertThrows(IllegalArgumentException.class,
                () -> service.remove(owner, "UNKNOWN"));
    }

    @Test
    void fileDeletionFailureRestoresCatalog()
            throws IOException {

        User owner = createUser("U001", Role.MEMBER);

        Scroll original = createScroll(
                "S001", "Java Notes", owner.id());

        // Create a valid record with an invalid storage path
        Scroll invalid = new Scroll(
                original.id(),
                original.name(),
                original.uploaderId(),
                original.uploadedAt(),
                original.updatedAt(),
                "invalid-file-path",
                original.originalFileName(),
                original.sizeBytes(),
                original.sha256(),
                original.downloadCount()
        );

        repository.update(invalid);

        assertThrows(IOException.class,
                () -> service.remove(owner, "S001"));

        // The metadata should be restored after failure
        assertEquals(invalid,
                repository.findById("S001").orElseThrow());
    }
}

