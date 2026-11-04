package au.edu.sydney.a2t0303.cli;


import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.ScrollEditService;
import au.edu.sydney.a2t0303.storage.JsonScrollCatalog;
import au.edu.sydney.a2t0303.storage.LocalScrollFileStorage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class ScrollEditCliTest {

    @TempDir
    Path tempDir;

    private Path dataDir;
    private JsonScrollCatalog repository;
    private LocalScrollFileStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        dataDir = tempDir.resolve("data");

        repository = new JsonScrollCatalog(
                dataDir.resolve("scrolls.json"));

        storage = new LocalScrollFileStorage(dataDir);
    }

    private User user(String id, Role role) {
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

        var stored = storage.store(source);

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

    private String runCli(User currentUser, String commands) {
        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        try (Scanner input = new Scanner(commands);
             PrintStream output = new PrintStream(
                     buffer, true, StandardCharsets.UTF_8)) {

            ScrollEditCli cli = new ScrollEditCli(
                    new ScrollEditService(repository, storage),
                    input,
                    output
            );

            cli.edit(currentUser);
        }

        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void ownerCanRenameThroughCli() throws IOException {

        User owner = user("U001", Role.MEMBER);
        createScroll("S001", "Old Notes", owner.id());

        String output = runCli(
                owner,
                "S001\nNew Notes\n\n"
        );

        assertTrue(output.contains(
                "Scroll updated successfully!"));

        assertEquals("New Notes",
                repository.findById("S001")
                        .orElseThrow().name());
    }

    @Test
    void anotherMemberCannotRenameThroughCli()
            throws IOException {

        createScroll("S001", "Java Notes", "U001");

        User other = user("U002", Role.MEMBER);

        String output = runCli(
                other,
                "S001\nChanged Name\n\n"
        );

        assertTrue(output.contains(
                "You cannot edit another user's scroll."));

        assertEquals("Java Notes",
                repository.findById("S001")
                        .orElseThrow().name());
    }

    @Test
    void adminCanRenameAnotherUsersScroll()
            throws IOException {

        createScroll("S001", "Java Notes", "U001");

        User admin = user("ADMIN", Role.ADMIN);

        String output = runCli(
                admin,
                "S001\nAdmin Updated\n\n"
        );

        assertTrue(output.contains(
                "Scroll updated successfully!"));

        Scroll result =
                repository.findById("S001").orElseThrow();

        assertEquals("Admin Updated", result.name());
        assertEquals("U001", result.uploaderId());
    }

    @Test
    void ownerCanReplaceFileThroughCli()
            throws IOException {

        User owner = user("U001", Role.MEMBER);

        Scroll original = createScroll(
                "S001", "Java Notes", owner.id());

        Path replacement = tempDir.resolve("new.txt");
        Files.writeString(replacement, "New content");

        String output = runCli(
                owner,
                "S001\n\n" + replacement + "\n"
        );

        assertTrue(output.contains(
                "Scroll updated successfully!"));

        Scroll updated =
                repository.findById("S001").orElseThrow();

        assertNotEquals(
                original.storagePath(),
                updated.storagePath());

        assertEquals("New content",
                au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.readString(
                        dataDir.resolve(updated.storagePath())));
    }

    @Test
    void guestCannotEditThroughCli() throws IOException {

        createScroll("S001", "Java Notes", "U001");

        String output = runCli(
                null,
                "S001\nChanged Name\n\n"
        );

        assertTrue(output.contains(
                "Please log in to edit a scroll."));
        assertFalse(output.contains(
        "New name (leave blank to keep current):"));

        assertFalse(output.contains(
                "Replacement file path (leave blank to keep current):"));

        assertEquals("Java Notes",
                repository.findById("S001")
                        .orElseThrow().name());
    }
    @Test
    void invalidScrollIdIsRejectedBeforeEditPrompts()
            throws IOException {

        User owner = user("U001", Role.MEMBER);
        Scroll original = createScroll(
                "S001", "Java Notes", owner.id());

        String output = runCli(
                owner,
                "INVALID123\n\n"
        );

        // Invalid ID is reported immediately
        assertTrue(output.contains(
                "Invalid Scroll ID: Scroll does not exist."));

        assertTrue(output.contains(
                "Please enter another Scroll ID or cancel."));

        // No further edit details should be requested
        assertFalse(output.contains(
                "New name (leave blank to keep current):"));

        assertFalse(output.contains(
                "Replacement file path (leave blank to keep current):"));

        // User can cancel
        assertTrue(output.contains(
                "Edit cancelled."));

        // Existing scroll remains unchanged
        Scroll after = repository.findById("S001")
                .orElseThrow();

        assertEquals(original.name(), after.name());
        assertEquals(original.storagePath(), after.storagePath());
        assertEquals(original.downloadCount(), after.downloadCount());

        assertEquals(
                "Original content",
                au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.readString(dataDir.resolve(after.storagePath()))
        );
    }
    @Test
    void invalidScrollIdCanBeRetriedSuccessfully()
            throws IOException {

        User owner = user("U001", Role.MEMBER);
        createScroll("S001", "Java Notes", owner.id());

        String output = runCli(
                owner,
                "INVALID123\nS001\nUpdated Notes\n\n"
        );

        // First ID is rejected
        assertTrue(output.contains(
                "Invalid Scroll ID: Scroll does not exist."));

        // The invalid ID is reported before edit details
        assertTrue(
                output.indexOf("Invalid Scroll ID:")
                        < output.indexOf("New name (")
        );

        // Second ID is valid and editing succeeds
        assertTrue(output.contains(
                "Scroll updated successfully!"));

        Scroll updated = repository.findById("S001")
                .orElseThrow();

        assertEquals("Updated Notes", updated.name());

        // Edit should not affect download statistics
        assertEquals(0L, updated.downloadCount());
    }
    @Test
    void unauthorizedEditIsRejectedBeforeFurtherPrompts()
            throws IOException {

        Scroll original = createScroll(
                "S001", "Java Notes", "U001");

        User other = user("U002", Role.MEMBER);

        String output = runCli(
                other,
                "S001\n"
        );

        // User has no permission to edit this scroll
        assertTrue(output.contains(
                "You cannot edit another user's scroll."));

        // No additional fields should be requested
        assertFalse(output.contains(
                "New name (leave blank to keep current):"));

        assertFalse(output.contains(
                "Replacement file path (leave blank to keep current):"));

        // Metadata remains unchanged
        Scroll after = repository.findById("S001")
                .orElseThrow();

        assertEquals(original.name(), after.name());
        assertEquals(original.storagePath(), after.storagePath());
        assertEquals(original.downloadCount(), after.downloadCount());
    }
}
