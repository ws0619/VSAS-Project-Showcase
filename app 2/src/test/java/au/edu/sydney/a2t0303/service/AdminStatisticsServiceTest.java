package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AdminStatisticsServiceTest {

    @TempDir
    Path directory;

    private final TestAccounts.MemoryUsers users = new TestAccounts.MemoryUsers();
    private final AuthenticationService authentication =
            new AuthenticationService(users, new PasswordHasher(1_000));
    private Path catalogFile;
    private JsonScrollCatalog scrolls;
    private LocalScrollFileStorage storage;
    private User alice;

    @BeforeEach
    void setUp() throws IOException {
        users.save(TestAccounts.user("A001", "admin", "Library Admin", Role.ADMIN, "adminSecret"));
        alice = TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, "aliceSecret");
        users.save(alice);
        catalogFile = directory.resolve("data/scrolls.json");
        scrolls = new JsonScrollCatalog(catalogFile);
        storage = new LocalScrollFileStorage(directory.resolve("data"));
    }

    private Path source(String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private void upload(String scrollId) throws IOException {
        var stored = storage.store(source(scrollId + ".txt", "first version of " + scrollId));
        Instant now = Instant.parse("2026-10-20T00:00:00Z");
        scrolls.save(new Scroll(scrollId, "Scroll " + scrollId, "U001", now, now,
                stored.storagePath(), stored.originalFileName(), stored.sizeBytes(), stored.sha256()));
    }

    private AdminStatisticsService service(JsonScrollCatalog catalog) {
        return new AdminStatisticsService(catalog, authentication);
    }

    @Test
    void everyScrollIsListedWithUploadAndDownloadCounts() throws IOException {
        upload("S002");
        upload("S001");
        scrolls.incrementDownloadCount("S001");
        scrolls.incrementDownloadCount("S001");
        authentication.login("admin", "adminSecret");

        var rows = service(scrolls).statistics();

        assertEquals(2, rows.size());
        assertEquals("S001", rows.get(0).scrollId());
        assertEquals("U001", rows.get(0).uploaderId());
        assertEquals(1, rows.get(0).uploadCount());
        assertEquals(2, rows.get(0).downloadCount());
        assertEquals(0, rows.get(1).downloadCount());
    }

    @Test
    void replacingTheFileCountsAsAnotherUpload() throws IOException {
        upload("S001");
        var editor = new ScrollEditService(scrolls, storage,
                Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC));
        editor.edit(alice, "S001", null, source("replacement.txt", "second version"));
        authentication.login("admin", "adminSecret");

        assertEquals(2, service(scrolls).statistics().get(0).uploadCount());
    }

    @Test
    void countsAreKeptAfterRestart() throws IOException {
        upload("S001");
        scrolls.incrementDownloadCount("S001");
        authentication.login("admin", "adminSecret");

        var restarted = service(new JsonScrollCatalog(catalogFile)).statistics();

        assertEquals(1, restarted.get(0).uploadCount());
        assertEquals(1, restarted.get(0).downloadCount());
    }

    @Test
    void onlyAdministratorsCanViewStatistics() throws IOException {
        upload("S001");

        assertThrows(SecurityException.class, () -> service(scrolls).statistics());
        authentication.enterGuest();
        assertThrows(SecurityException.class, () -> service(scrolls).statistics());
        authentication.login("alice", "aliceSecret");
        assertThrows(SecurityException.class, () -> service(scrolls).statistics());
    }
}
