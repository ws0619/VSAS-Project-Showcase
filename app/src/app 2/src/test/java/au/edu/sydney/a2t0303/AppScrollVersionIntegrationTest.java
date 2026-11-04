package au.edu.sydney.a2t0303;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.storage.JsonScrollCatalog;
import au.edu.sydney.a2t0303.storage.JsonUserRepository;
import au.edu.sydney.a2t0303.storage.LocalScrollFileStorage;
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

class AppScrollVersionIntegrationTest {
    @TempDir Path directory;

    private String run(String commands) throws IOException {
        var bytes = new ByteArrayOutputStream();
        App.run(directory, new Scanner(commands), new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void ownerCanReplaceThenListVersionsThroughMainMenuAndAfterRestart() throws IOException {
        var users = new JsonUserRepository(directory.resolve("users.json"));
        users.save(TestAccounts.user("U001", "alice", "Alice", Role.MEMBER, "password"));
        var catalog = new JsonScrollCatalog(directory.resolve("scrolls.json"));
        var stored = new LocalScrollFileStorage(directory)
                .store(Files.writeString(directory.resolve("first.bin"), "first"));
        Instant date = Instant.parse("2026-10-20T10:00:00Z");
        catalog.save(new Scroll("S001", "Notes", "U001", date, date,
                stored.storagePath(), stored.originalFileName(), stored.sizeBytes(), stored.sha256()));
        Path replacement = Files.writeString(directory.resolve("second.bin"), "second");

        String login = "2\nalice\npassword\n";
        String output = run(login + "4\nS001\n\n" + replacement + "\n8\nS001\n0\n");
        assertTrue(output.contains("8. View my scroll version history"));
        assertTrue(output.contains("Scroll updated successfully!"));
        assertTrue(output.contains("Version 1 | 2026-10-20T10:00:00Z | first.bin"));
        assertTrue(output.contains("Version 2 (current)"));
        assertTrue(Files.exists(directory.resolve(stored.storagePath())));
        String restarted = run(login + "8\nS001\n0\n");
        assertTrue(restarted.contains("Version 1 | 2026-10-20T10:00:00Z | first.bin"));
        assertTrue(restarted.contains("Version 2 (current)"));
    }

    @Test
    void guestCannotOpenHistoryMenu() throws IOException {
        String output = run("3\n8\n0\n");
        assertFalse(output.contains("8. View my scroll version history"));
        assertFalse(output.contains("===== Scroll Version History ====="));
        assertTrue(output.contains("Invalid option."));
    }
}
