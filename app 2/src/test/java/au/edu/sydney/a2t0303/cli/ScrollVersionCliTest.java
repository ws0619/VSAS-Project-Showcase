package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.ScrollEditService;
import au.edu.sydney.a2t0303.service.ScrollVersionService;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class ScrollVersionCliTest {
    @TempDir Path directory;
    private JsonScrollCatalog catalog;
    private User owner;

    @BeforeEach
    void setUp() throws IOException {
        catalog = new JsonScrollCatalog(directory.resolve("scrolls.json"));
        owner = TestAccounts.user("U001", "alice", "Alice", Role.MEMBER, "password");
        var storage = new LocalScrollFileStorage(directory);
        var file = storage.store(Files.writeString(directory.resolve("old.bin"), "old"));
        var date = Instant.parse("2026-10-20T10:00:00Z");
        catalog.save(new Scroll("S001", "Notes", owner.id(), date, date,
                file.storagePath(), file.originalFileName(), file.sizeBytes(), file.sha256()));
        new ScrollEditService(catalog, storage, Clock.fixed(date.plusSeconds(3600), ZoneOffset.UTC))
                .edit(owner, "S001", null, Files.writeString(directory.resolve("new.bin"), "new"));
    }

    private String run(User user, String commands) {
        var bytes = new ByteArrayOutputStream();
        new ScrollVersionCli(new ScrollVersionService(catalog), new Scanner(commands),
                new PrintStream(bytes, true, StandardCharsets.UTF_8)).showHistory(user);
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void listsVersionsWithUtcDatesAndMarksCurrentVersion() {
        String output = run(owner, " S001 \n");
        assertTrue(output.contains("Version 1 | 2026-10-20T10:00:00Z | old.bin | 3 bytes"));
        assertTrue(output.contains("Version 2 (current) | 2026-10-20T11:00:00Z | new.bin | 3 bytes"));
        assertTrue(output.indexOf("Version 1") < output.indexOf("Version 2"));
        assertFalse(output.contains("scroll-files/"));
    }

    @Test
    void guestIsRejectedBeforePrompt() {
        String output = run(null, "S001\n");
        assertTrue(output.contains("Please log in"));
        assertFalse(output.contains("Enter Scroll ID"));
    }

    @Test
    void otherMembersCannotSeeFileNamesOrDates() {
        var other = TestAccounts.user("U002", "bob", "Bob", Role.MEMBER, "password");
        String output = run(other, "S001\n");
        assertTrue(output.contains("You cannot view another user's scroll version history."));
        assertFalse(output.contains("old.bin"));
        assertFalse(output.contains("2026-10-20"));
    }

    @Test
    void unknownScrollReportsError() {
        assertTrue(run(owner, "MISSING\n").contains("Scroll does not exist."));
    }

    @Test
    void blankOrEndOfInputReturnsWithoutListing() {
        assertFalse(run(owner, "\n").contains("Version 1"));
        assertFalse(run(owner, "").contains("Version 1"));
    }

    @Test
    void repositoryReadFailureReportsError() throws IOException {
        Files.writeString(directory.resolve("scrolls.json"), "invalid json");
        assertTrue(run(owner, "S001\n").contains("Unable to view version history:"));
    }
}
