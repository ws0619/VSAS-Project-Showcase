
package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.storage.ScrollRepository;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.JsonScrollCatalog;
import au.edu.sydney.a2t0303.storage.JsonUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ScrollDownloadServiceTest {

    @TempDir
    Path tempDir;

    private Path dataDir;
    private JsonScrollCatalog catalog;

    private static final String STORAGE_PATH =
            "scroll-files/123e4567-e89b-12d3-a456-426614174000.bin";

    @BeforeEach
    void setUp() throws IOException {
        dataDir = tempDir.resolve("data");
        catalog = new JsonScrollCatalog(
                dataDir.resolve("scrolls.json"));
    }

    // Create a stored scroll with actual file content.
    private byte[] createScroll() throws IOException {

        byte[] content = "Hello VSAS!\nJava Notes\n"
                .getBytes(StandardCharsets.UTF_8);

        Path file = dataDir.resolve(STORAGE_PATH);
        Files.createDirectories(file.getParent());
        au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.write(file, content);

        Instant now = Instant.parse("2026-10-21T08:00:00Z");

        Scroll scroll = new Scroll(
                "S001",
                "Java Notes",
                "U001",
                now,
                now,
                STORAGE_PATH,
                "notes.txt",
                content.length,
                "a".repeat(64)
        );

        catalog.save(scroll);
        return content;
    }

    // Create a real authenticated test session.
    private AuthenticationService loggedInUser()
            throws IOException, GeneralSecurityException {

        String password = "test-password";
        byte[] salt = "fixed-test-salt-123"
                .getBytes(StandardCharsets.UTF_8);

        int iterations = 100000;

        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                iterations,
                256
        );

        byte[] hash;
        try {
            hash = SecretKeyFactory.getInstance(
                    PasswordHasher.ALGORITHM
            ).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }

        Instant now = Instant.parse("2026-10-21T08:00:00Z");

        User user = new User(
                "U001",
                "alice",
                "Alice Smith",
                "alice@example.com",
                "0400123456",
                Role.MEMBER,
                Base64.getEncoder().encodeToString(hash),
                Base64.getEncoder().encodeToString(salt),
                PasswordHasher.ALGORITHM,
                iterations,
                now,
                now
        );

        JsonUserRepository users = new JsonUserRepository(
                dataDir.resolve("users.json")
        );

        users.save(user);

        AuthenticationService authentication =
                new AuthenticationService(
                        users, new PasswordHasher());

        assertTrue(authentication.login("alice", password));

        return authentication;
    }

    private ScrollDownloadService service(
            AuthenticationService authentication) {

        return new ScrollDownloadService(
                catalog,
                authentication,
                dataDir
        );
    }

    private long downloadCount() throws IOException {
        return catalog.findById("S001")
                .orElseThrow()
                .downloadCount();
    }

    @Test
    void successfulDownloadCopiesFileAndIncrementsCount()
            throws Exception {

        byte[] original = createScroll();

        ScrollDownloadService downloader =
                service(loggedInUser());

        Path destination =
                tempDir.resolve("downloaded-notes.txt");

        downloader.download("S001", destination);

        // The complete file must be copied
        assertTrue(Files.exists(destination));
        assertArrayEquals(
                original,
                Files.readAllBytes(destination)
        );

        // Download count increases exactly once
        assertEquals(1L, downloadCount());
    }

    @Test
    void guestCannotDownload() throws IOException {

        createScroll();

        AuthenticationService authentication =
                new AuthenticationService(
                        new JsonUserRepository(
                                dataDir.resolve("users.json")),
                        new PasswordHasher()
                );

        authentication.enterGuest();

        Path destination =
                tempDir.resolve("guest-download.txt");

        SecurityException error = assertThrows(
                SecurityException.class,
                () -> service(authentication)
                        .download("S001", destination)
        );

        assertTrue(error.getMessage().contains("Please log in"));
        assertFalse(Files.exists(destination));
        assertEquals(0L, downloadCount());
    }

    @Test
    void unknownScrollCannotBeDownloaded() throws Exception {

        createScroll();

        Path destination =
                tempDir.resolve("unknown.txt");

        ScrollDownloadService downloader =
                service(loggedInUser());

        assertThrows(
                IOException.class,
                () -> downloader.download("S999", destination)
        );

        assertFalse(Files.exists(destination));
        assertEquals(0L, downloadCount());
    }

    @Test
    void existingDestinationIsNotOverwritten() throws Exception {

        createScroll();

        Path destination =
                tempDir.resolve("existing.txt");

        Files.writeString(destination, "My original file");

        ScrollDownloadService downloader =
                service(loggedInUser());

        assertThrows(
                IOException.class,
                () -> downloader.download("S001", destination)
        );

        assertEquals(
                "My original file",
                Files.readString(destination)
        );

        assertEquals(0L, downloadCount());
    }

    @Test
    void missingStoredFileDoesNotIncreaseCount() throws Exception {

        createScroll();

        Files.delete(dataDir.resolve(STORAGE_PATH));

        Path destination =
                tempDir.resolve("missing.txt");

        ScrollDownloadService downloader =
                service(loggedInUser());

        assertThrows(
                IOException.class,
                () -> downloader.download("S001", destination)
        );

        assertFalse(Files.exists(destination));
        assertEquals(0L, downloadCount());
    }

    @Test
    void downloadCountSurvivesRepositoryRestart() throws Exception {

        createScroll();

        ScrollDownloadService downloader =
                service(loggedInUser());

        Path destination =
                tempDir.resolve("restored.txt");

        downloader.download("S001", destination);

        // Simulate application restart
        JsonScrollCatalog restartedCatalog =
                new JsonScrollCatalog(
                        dataDir.resolve("scrolls.json"));

        long restoredCount = restartedCatalog
                .findById("S001")
                .orElseThrow()
                .downloadCount();

        assertEquals(1L, restoredCount);
    }

@Test
void savedFileIsPreservedWhenCountUpdateFails()
        throws Exception {

    // Create an actual stored scroll
    byte[] original = createScroll();

    // Fake repository: reading works,
    // but updating the download count fails.
    ScrollRepository failingRepository =
            new ScrollRepository() {

        @Override
        public List<Scroll> findAll() throws IOException {
            return catalog.findAll();
        }

        @Override
        public void save(Scroll scroll) throws IOException {
            catalog.save(scroll);
        }

        @Override
        public void incrementDownloadCount(String scrollId)
                throws IOException {

            throw new IOException(
                    "Simulated metadata write failure");
        }
    };

    ScrollDownloadService downloader =
            new ScrollDownloadService(
                    failingRepository,
                    loggedInUser(),
                    dataDir
            );

    Path destination =
            tempDir.resolve("partial-success.txt");

    // Verify the special exception
    var error = assertThrows(
            ScrollDownloadService.DownloadCountUpdateException.class,
            () -> downloader.download("S001", destination)
    );

    // File should exist even though count update failed
    assertTrue(Files.exists(destination));

    // Complete file content must be preserved
    assertArrayEquals(
            original,
            Files.readAllBytes(destination)
    );

    // Exception identifies the saved destination
    assertEquals(
            destination.toAbsolutePath().normalize(),
            error.savedPath()
    );

    // Count was not updated
    assertEquals(0L, downloadCount());
}

@Test
void symlinkedStorageDirectoryIsRejected()
        throws Exception {

    // Create a directory outside the trusted data folder
    Path outside = tempDir.resolve("outside-storage");
    Files.createDirectories(outside);

    Path storageDir = dataDir.resolve("scroll-files");

    // Create a symbolic link:
    // data/scroll-files -> outside-storage
    try {
        Files.createSymbolicLink(
                storageDir,
                outside.toAbsolutePath()
        );
    } catch (UnsupportedOperationException |
             IOException |
             SecurityException e) {

        Assumptions.assumeTrue(
                false,
                "Symlink creation is not supported: "
                        + e.getMessage()
        );
        return;
    }

    // Create a real file outside the intended storage folder
    byte[] content = "External file content"
            .getBytes(StandardCharsets.UTF_8);

    Files.write(
            outside.resolve(
                    "123e4567-e89b-12d3-a456-426614174000.bin"),
            content
    );

    Instant now = Instant.parse("2026-10-21T08:00:00Z");

    // Save metadata referencing the symlinked storage directory
    catalog.save(new Scroll(
            "S001",
            "External Scroll",
            "U001",
            now,
            now,
            STORAGE_PATH,
            "external.txt",
            content.length,
            "a".repeat(64)
    ));

    Path destination =
            tempDir.resolve("downloaded-external.txt");

    ScrollDownloadService downloader =
            service(loggedInUser());

    // Download must be rejected
    IOException error = assertThrows(
            IOException.class,
            () -> downloader.download("S001", destination)
    );

    assertTrue(
            error.getMessage().contains("Unsafe")
    );

    // No destination file should be created
    assertFalse(Files.exists(destination));

    // Count must remain zero
    assertEquals(0L, downloadCount());
}

}
