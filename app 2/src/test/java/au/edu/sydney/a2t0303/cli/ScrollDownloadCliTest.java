
package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.storage.ScrollRepository;
import java.util.List;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.AuthenticationService;
import au.edu.sydney.a2t0303.service.PasswordHasher;
import au.edu.sydney.a2t0303.service.ScrollDownloadService;
import au.edu.sydney.a2t0303.storage.JsonScrollCatalog;
import au.edu.sydney.a2t0303.storage.JsonUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class ScrollDownloadCliTest {

    @TempDir
    Path tempDir;

    private Path dataDir;
    private JsonScrollCatalog scrolls;

    private static final String STORAGE_PATH =
            "scroll-files/123e4567-e89b-12d3-a456-426614174000.bin";

    @BeforeEach
    void setUp() throws IOException {
        dataDir = tempDir.resolve("data");

        scrolls = new JsonScrollCatalog(
                dataDir.resolve("scrolls.json")
        );
    }

    private byte[] createScroll() throws IOException {

        byte[] content = "Hello VSAS!\nJava Notes\n"
                .getBytes(StandardCharsets.UTF_8);

        Path file = dataDir.resolve(STORAGE_PATH);

        Files.createDirectories(file.getParent());
        au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.write(file, content);

        Instant now = Instant.parse("2026-10-21T08:00:00Z");

        scrolls.save(new Scroll(
                "S001",
                "Java Notes",
                "U001",
                now,
                now,
                STORAGE_PATH,
                "notes.txt",
                content.length,
                "a".repeat(64)
        ));

        return content;
    }

    private AuthenticationService guest()
            throws IOException {

        JsonUserRepository users =
                new JsonUserRepository(
                        dataDir.resolve("users.json"));

        AuthenticationService authentication =
                new AuthenticationService(
                        users, new PasswordHasher());

        authentication.enterGuest();

        return authentication;
    }

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

        JsonUserRepository users =
                new JsonUserRepository(
                        dataDir.resolve("users.json"));

        users.save(user);

        AuthenticationService authentication =
                new AuthenticationService(
                        users, new PasswordHasher());

        assertTrue(
                authentication.login("alice", password)
        );

        return authentication;
    }

    private String runCli(
            AuthenticationService authentication,
            String typedInput) {

        ByteArrayOutputStream bytes =
                new ByteArrayOutputStream();

        PrintStream output = new PrintStream(
                bytes, true, StandardCharsets.UTF_8);

        ScrollDownloadService service =
                new ScrollDownloadService(
                        scrolls,
                        authentication,
                        dataDir
                );

        ScrollDownloadCli cli =
                new ScrollDownloadCli(
                        service,
                        new Scanner(typedInput),
                        output
                );

        cli.download();
        output.flush();

        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void successfulDownloadShowsConfirmation()
            throws Exception {

        byte[] original = createScroll();

        Path folder = tempDir.resolve("downloads");
        Files.createDirectories(folder);

        Path destination = folder.resolve(
                scrolls.findById("S001")
                        .orElseThrow()
                        .originalFileName()
        );

        String output = runCli(
                loggedInUser(),
                "S001\n" + folder + "\n"
        );

        assertTrue(output.contains(
                "Scroll downloaded successfully"));
        assertTrue(output.contains(
                destination.toAbsolutePath().normalize().toString()
        ));

        assertArrayEquals(
                original,
                Files.readAllBytes(destination)
        );

        assertEquals(
                1L,
                scrolls.findById("S001")
                        .orElseThrow()
                        .downloadCount()
        );
    }

    @Test
    void guestDownloadIsRejected()
            throws IOException {

        createScroll();

        Path destination =
                tempDir.resolve("guest-download.txt");

        String output = runCli(
                guest(),
                "S001\n" + destination + "\n"
        );

        assertTrue(output.contains(
                "Please log in to download a scroll."));

        assertFalse(Files.exists(destination));

        assertEquals(
                0L,
                scrolls.findById("S001")
                        .orElseThrow()
                        .downloadCount()
        );
    }

    @Test
    void emptyScrollIdCancelsDownload()
            throws IOException {

        createScroll();

        String output = runCli(
                guest(),
                "\n"
        );

        assertTrue(output.contains(
                "Download cancelled."));

        assertEquals(
                0L,
                scrolls.findById("S001")
                        .orElseThrow()
                        .downloadCount()
        );
    }

    @Test
    void emptyDestinationCancelsDownload()
            throws Exception {

        createScroll();

        String output = runCli(
                loggedInUser(),
                "S001\n\n"
        );

        assertTrue(output.contains(
                "Download cancelled."));
    }

    @Test
    void existingFileIsNotOverwritten()
            throws Exception {

        createScroll();

        Path folder = tempDir.resolve("downloads-collision");
        Files.createDirectories(folder);

        Path destination = folder.resolve(
                scrolls.findById("S001")
                        .orElseThrow()
                        .originalFileName()
        );

        Files.writeString(
                destination,
                "Original user file"
        );

        String output = runCli(
                loggedInUser(),
                "S001\n" + folder + "\n\n"
        );

        assertTrue(output.contains(
                "Destination file already exists"));

        assertTrue(output.contains(
                "Please choose another folder or cancel."));

        assertTrue(output.contains(
                "Download cancelled."));

        assertEquals(
                "Original user file",
                Files.readString(destination)
        );

        assertEquals(
                0L,
                scrolls.findById("S001")
                        .orElseThrow()
                        .downloadCount()
        );
    }
    @Test
    void nonexistentFolderAllowsCancellation() throws Exception {
        createScroll();

        Path folder = tempDir.resolve("missing-folder");

        String output = runCli(
                loggedInUser(),
                "S001\n" + folder + "\n\n"
        );

        assertTrue(output.contains(
                "Destination folder does not exist."));

        assertTrue(output.contains(
                "Please choose another folder or cancel."));

        assertTrue(output.contains(
                "Download cancelled."));

        assertFalse(Files.exists(folder));

        assertEquals(
                0L,
                scrolls.findById("S001")
                        .orElseThrow()
                        .downloadCount()
        );
    }

    @Test
    void regularFileIsNotAcceptedAsFolder() throws Exception {
        createScroll();

        Path file = tempDir.resolve("not-a-folder.txt");
        Files.writeString(file, "Original content");

        String output = runCli(
                loggedInUser(),
                "S001\n" + file + "\n\n"
        );

        assertTrue(output.contains(
                "Destination path is not a folder."));

        assertTrue(output.contains(
                "Please choose another folder or cancel."));

        assertEquals(
                "Original content",
                Files.readString(file)
        );

        assertEquals(
                0L,
                scrolls.findById("S001")
                        .orElseThrow()
                        .downloadCount()
        );
    }
    @Test
    void invalidFolderCanBeRetriedSuccessfully() throws Exception {
        byte[] original = createScroll();

        Path invalidFolder = tempDir.resolve("missing-retry");
        Path validFolder = tempDir.resolve("valid-retry");
        Files.createDirectories(validFolder);

        Path destination = validFolder.resolve(
                scrolls.findById("S001")
                        .orElseThrow()
                        .originalFileName()
        );

        String output = runCli(
                loggedInUser(),
                "S001\n" + invalidFolder + "\n"
                        + validFolder + "\n"
        );

        assertTrue(output.contains(
                "Destination folder does not exist."));

        assertTrue(output.contains(
                "Scroll downloaded successfully"));

        assertArrayEquals(
                original,
                Files.readAllBytes(destination));

        assertEquals(
                1L,
                scrolls.findById("S001")
                        .orElseThrow()
                        .downloadCount()
        );
    }
    @Test
    void existingFileAllowsRetryInAnotherFolder() throws Exception {
        byte[] original = createScroll();

        Path firstFolder = tempDir.resolve("collision-retry");
        Path secondFolder = tempDir.resolve("success-retry");

        Files.createDirectories(firstFolder);
        Files.createDirectories(secondFolder);

        String filename = scrolls.findById("S001")
                .orElseThrow()
                .originalFileName();

        Path existing = firstFolder.resolve(filename);
        Path destination = secondFolder.resolve(filename);

        Files.writeString(existing, "Original user file");

        String output = runCli(
                loggedInUser(),
                "S001\n" + firstFolder + "\n"
                        + secondFolder + "\n"
        );

        assertTrue(output.contains(
                "Destination file already exists"));

        assertTrue(output.contains(
                "Scroll downloaded successfully"));

        assertEquals(
                "Original user file",
                Files.readString(existing));

        assertArrayEquals(
                original,
                Files.readAllBytes(destination));

        assertEquals(
                1L,
                scrolls.findById("S001")
                        .orElseThrow()
                        .downloadCount()
        );
    }

    @Test
    void invalidDestinationPathIsRejected()
            throws Exception {

        createScroll();

        String output = runCli(
                loggedInUser(),
                "S001\nbad\u0000path\n"
        );

        assertTrue(output.contains(
                "Invalid destination path."));
    }
    @Test
    void invalidScrollIdIsRejectedBeforeDestinationPrompt()
            throws Exception {

        createScroll();

        String output = runCli(
                loggedInUser(),
                "INVALID123\n\n"
        );

        assertTrue(output.contains(
                "Scroll not found: INVALID123"));

        assertTrue(output.contains(
                "Please enter another Scroll ID or cancel."));

        assertFalse(output.contains(
                "Enter destination folder"));

        assertTrue(output.contains(
                "Download cancelled."));

        assertEquals(
                0L,
                scrolls.findById("S001")
                        .orElseThrow()
                        .downloadCount()
        );
    }
    @Test
    void invalidScrollIdCanBeRetriedSuccessfully()
            throws Exception {

        byte[] original = createScroll();

        Path folder = tempDir.resolve("id-retry-download");
        Files.createDirectories(folder);

        Path destination = folder.resolve(
                scrolls.findById("S001")
                        .orElseThrow()
                        .originalFileName()
        );

        String output = runCli(
                loggedInUser(),
                "INVALID123\nS001\n" + folder + "\n"
        );

        assertTrue(output.contains(
                "Scroll not found: INVALID123"));

        assertTrue(output.contains(
                "Please enter another Scroll ID or cancel."));

        assertTrue(output.contains(
                "Scroll downloaded successfully"));

        assertArrayEquals(
                original,
                Files.readAllBytes(destination)
        );

        assertEquals(
                1L,
                scrolls.findById("S001")
                        .orElseThrow()
                        .downloadCount()
        );
    }

@Test
void cliReportsPartialSuccessClearly() throws Exception {

    byte[] original = createScroll();

    ScrollRepository failingRepository =
            new ScrollRepository() {

        @Override
        public List<Scroll> findAll() throws IOException {
            return scrolls.findAll();
        }

        @Override
        public void save(Scroll scroll) throws IOException {
            scrolls.save(scroll);
        }

        @Override
        public void incrementDownloadCount(String scrollId)
                throws IOException {

            throw new IOException(
                    "Simulated count update failure"
            );
        }
    };

    ScrollDownloadService service =
            new ScrollDownloadService(
                    failingRepository,
                    loggedInUser(),
                    dataDir
            );
        Path folder = tempDir.resolve("downloads-partial");
        Files.createDirectories(folder);

        Path destination = folder.resolve(
                scrolls.findById("S001")
                        .orElseThrow()
                        .originalFileName()
        );


    ByteArrayOutputStream bytes =
            new ByteArrayOutputStream();

    try (PrintStream output = new PrintStream(
            bytes, true, StandardCharsets.UTF_8);
         Scanner input = new Scanner(
                 "S001\n" + folder + "\n")) {

        ScrollDownloadCli cli =
                new ScrollDownloadCli(
                        service,
                        input,
                        output
                );

        cli.download();
    }

    String result =
            bytes.toString(StandardCharsets.UTF_8);

    // CLI acknowledges that the file was saved
    assertTrue(result.contains(
            "Scroll file downloaded successfully"));

    // CLI warns about the counter failure
    assertTrue(result.contains(
            "Warning: The download count could not be updated."));

    // Must not incorrectly report a full download failure
    assertFalse(result.contains(
            "Download could not be completed:"));

    // The downloaded file actually exists
    assertArrayEquals(
            original,
            Files.readAllBytes(destination)
    );

    // No count update occurred
    assertEquals(
            0L,
            scrolls.findById("S001")
                    .orElseThrow()
                    .downloadCount()
    );
}

}
