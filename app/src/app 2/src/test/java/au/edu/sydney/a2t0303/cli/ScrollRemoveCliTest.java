package au.edu.sydney.a2t0303.cli;



import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.ScrollRemoveService;
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

class ScrollRemoveCliTest {

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
        Instant now =
                Instant.parse("2026-10-19T08:00:00Z");

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

        Instant now =
                Instant.parse("2026-10-18T08:00:00Z");

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

            ScrollRemoveCli cli = new ScrollRemoveCli(
                    new ScrollRemoveService(repository, storage),
                    input,
                    output
            );

            cli.remove(currentUser);
        }

        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void ownerCanConfirmAndRemoveScroll() throws IOException {

        User owner = user("U001", Role.MEMBER);

        Scroll scroll = createScroll(
                "S001", "Java Notes", owner.id());

        Path storedFile =
                dataDir.resolve(scroll.storagePath());

        String output = runCli(owner, "S001\nYES\n");

        assertTrue(output.contains(
                "Scroll removed successfully!"));

        assertTrue(repository.findById("S001").isEmpty());
        assertFalse(Files.exists(storedFile));
    }

    @Test
    void noConfirmationKeepsScroll() throws IOException {

        User owner = user("U001", Role.MEMBER);

        Scroll scroll = createScroll(
                "S001", "Java Notes", owner.id());

        String output = runCli(owner, "S001\nNO\n");

        assertTrue(output.contains("Removal cancelled."));

        assertEquals(scroll,
                repository.findById("S001").orElseThrow());

        assertTrue(Files.exists(
                dataDir.resolve(scroll.storagePath())));
    }

    @Test
    void blankConfirmationKeepsScroll() throws IOException {

        User owner = user("U001", Role.MEMBER);

        Scroll scroll = createScroll(
                "S001", "Java Notes", owner.id());

        String output = runCli(owner, "S001\n\n");

        assertTrue(output.contains("Removal cancelled."));

        assertEquals(scroll,
                repository.findById("S001").orElseThrow());
    }

    @Test
    void memberCannotRemoveAnotherUsersScroll()
            throws IOException {

        Scroll scroll = createScroll(
                "S001", "Java Notes", "U001");

        User other = user("U002", Role.MEMBER);

        String output = runCli(other, "S001\nYES\n");

        assertTrue(output.contains(
                "You cannot remove another user's scroll."));

        // Rejected before any confirmation is requested
        assertFalse(output.contains("Type YES to confirm:"));

        assertEquals(scroll,
                repository.findById("S001").orElseThrow());

        assertTrue(Files.exists(
                dataDir.resolve(scroll.storagePath())));
    }

    @Test
    void adminCanRemoveAnotherUsersScroll()
            throws IOException {

        Scroll scroll = createScroll(
                "S001", "Java Notes", "U001");

        User admin = user("ADMIN", Role.ADMIN);

        String output = runCli(admin, "S001\nYES\n");

        assertTrue(output.contains(
                "Scroll removed successfully!"));

        assertTrue(repository.findById("S001").isEmpty());

        assertFalse(Files.exists(
                dataDir.resolve(scroll.storagePath())));
    }

    @Test
    void unknownScrollIdIsRejectedBeforeConfirmation() throws IOException {

        Scroll scroll = createScroll("S001", "Java Notes", "U001");

        String output = runCli(user("U001", Role.MEMBER), "MISSING\n\n");

        assertTrue(output.contains("Invalid Scroll ID: Scroll does not exist."));
        assertTrue(output.contains("Please enter another Scroll ID or press Enter to cancel."));
        assertFalse(output.contains("Type YES to confirm:"));
        assertTrue(output.contains("Removal cancelled."));
        assertEquals(scroll, repository.findById("S001").orElseThrow());
    }

    @Test
    void wrongIdCanBeRetriedAndConfirmationShowsTheScrollName() throws IOException {

        User owner = user("U001", Role.MEMBER);
        createScroll("S001", "Java Notes", owner.id());

        String output = runCli(owner, "MISSING\nS001\nYES\n");

        assertTrue(output.contains("Invalid Scroll ID: Scroll does not exist."));
        assertTrue(output.contains("Are you sure you want to remove scroll S001 (Java Notes)?"));
        assertTrue(output.contains("Scroll removed successfully!"));
        assertTrue(repository.findById("S001").isEmpty());
    }

    @Test
    void guestCannotRemoveScroll() throws IOException {

        Scroll scroll = createScroll(
                "S001", "Java Notes", "U001");

        String output = runCli(null, "S001\nYES\n");

        assertTrue(output.contains(
                "Please log in to remove a scroll."));

        assertEquals(scroll,
                repository.findById("S001").orElseThrow());
    }
}

