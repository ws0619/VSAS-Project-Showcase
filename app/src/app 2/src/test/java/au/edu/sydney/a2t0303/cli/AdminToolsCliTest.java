package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.service.AdminStatisticsService;
import au.edu.sydney.a2t0303.service.AdminUserDeletionService;
import au.edu.sydney.a2t0303.service.AdminUserService;
import au.edu.sydney.a2t0303.service.AuthenticationService;
import au.edu.sydney.a2t0303.service.PasswordHasher;
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

/** US14 and US16 through the administrator submenu. */
class AdminToolsCliTest {

    @TempDir
    Path directory;

    private final TestAccounts.MemoryUsers users = new TestAccounts.MemoryUsers();
    private final AuthenticationService authentication =
            new AuthenticationService(users, new PasswordHasher(1_000));
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    private JsonScrollCatalog scrolls;
    private LocalScrollFileStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        users.save(TestAccounts.user("A001", "admin", "Library Admin", Role.ADMIN, "adminSecret"));
        users.save(TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, "aliceSecret"));
        scrolls = new JsonScrollCatalog(directory.resolve("data/scrolls.json"));
        storage = new LocalScrollFileStorage(directory.resolve("data"));
        Path source = directory.resolve("notes.txt");
        Files.writeString(source, "notes");
        var stored = storage.store(source);
        Instant now = Instant.parse("2026-10-20T00:00:00Z");
        scrolls.save(new Scroll("S001", "Java Notes", "U001", now, now, stored.storagePath(),
                stored.originalFileName(), stored.sizeBytes(), stored.sha256()));
        authentication.login("admin", "adminSecret");
    }

    private String run(String typed) {
        var input = new Scanner(typed);
        new AdminCli(new AdminUserService(users, authentication), null,
                new AdminDeleteUserCli(new AdminUserDeletionService(users, scrolls,
                        new ScrollRemoveService(scrolls, storage), authentication), input, output),
                new AdminStatisticsCli(new AdminStatisticsService(scrolls, authentication), output),
                input, output).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void menuShowsDeleteAndStatisticsOptions() {
        String result = run("9\n0\n");

        assertTrue(result.contains("3. Delete a user"));
        assertTrue(result.contains("4. View scroll statistics"));
        assertTrue(result.contains("Invalid option. Please choose 0, 1, 3 or 4."));
    }

    @Test
    void deletionShowsWhatWillBeRemovedAndNeedsYes() throws IOException {
        String result = run("3\nU001\nYES\n0\n");

        assertTrue(result.contains("User: alice (Alice Example) | ID key: U001 | Type: User"));
        assertTrue(result.contains("Their 1 uploaded scroll(s) will also be deleted."));
        assertTrue(result.contains("User alice deleted. 1 scroll(s) removed."));
        assertTrue(users.findById("U001").isEmpty());
        assertTrue(scrolls.findAll().isEmpty());
    }

    @Test
    void anythingOtherThanYesCancels() throws IOException {
        String result = run("3\nU001\nyes\n0\n");

        assertTrue(result.contains("Deletion cancelled. No data was changed."));
        assertTrue(users.findById("U001").isPresent());
        assertEquals(1, scrolls.findAll().size());
    }

    @Test
    void ownAccountAndUnknownIdsAreRefused() {
        String result = run("3\nA001\n3\nU999\n3\n\n0\n");

        assertTrue(result.contains("Deletion failed: You cannot delete your own account."));
        assertTrue(result.contains("Deletion failed: No user has ID key \"U999\"."));
        assertTrue(result.contains("Deletion cancelled."));
        assertEquals(2, users.findAll().size());
    }

    @Test
    void statisticsTableShowsCounts() throws IOException {
        scrolls.incrementDownloadCount("S001");

        String result = run("4\n0\n");

        assertTrue(result.contains("=== Scroll Statistics ==="));
        assertTrue(result.contains("Uploads"));
        assertTrue(result.contains("Downloads"));
        assertTrue(result.contains("S001"));
        assertTrue(result.contains("Total: 1 scroll(s), 1 upload(s), 1 download(s)."));
    }

    @Test
    void emptyLibraryHasAClearStatisticsMessage() throws IOException {
        run("3\nU001\nYES\n0\n");
        bytes.reset();

        assertTrue(run("4\n0\n").contains("No scrolls have been uploaded yet."));
    }
}
