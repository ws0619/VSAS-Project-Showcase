
package au.edu.sydney.a2t0303;

import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.PasswordHasher;
import au.edu.sydney.a2t0303.storage.JsonScrollCatalog;
import au.edu.sydney.a2t0303.storage.JsonUserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class AppDownloadIntegrationTest {

    @TempDir
    Path directory;

    private static final String STORAGE_PATH =
            "scroll-files/123e4567-e89b-12d3-a456-426614174000.bin";

    private void createTestScroll() throws Exception {

        Path storedFile = directory.resolve(STORAGE_PATH);
        Files.createDirectories(storedFile.getParent());

        byte[] content = "Hello VSAS!\nJava Notes\n"
                .getBytes(StandardCharsets.UTF_8);

        au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.write(storedFile, content);

        Instant now = Instant.parse("2026-10-21T08:00:00Z");

        new JsonScrollCatalog(
                directory.resolve("scrolls.json")
        ).save(new Scroll(
                "S001", "Java Notes", "U001",
                now, now,
                STORAGE_PATH, "notes.txt",
                content.length, "a".repeat(64)
        ));
    }

    private void createTestUser() throws Exception {

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
                "U001", "alice", "Alice Smith",
                "alice@example.com", "0400123456",
                Role.MEMBER,
                Base64.getEncoder().encodeToString(hash),
                Base64.getEncoder().encodeToString(salt),
                PasswordHasher.ALGORITHM,
                iterations,
                now, now
        );

        new JsonUserRepository(
                directory.resolve("users.json")
        ).save(user);
    }

    private String runApp(String typedInput) throws Exception {

        ByteArrayOutputStream bytes =
                new ByteArrayOutputStream();

        try (PrintStream output = new PrintStream(
                bytes, true, StandardCharsets.UTF_8);
             Scanner input = new Scanner(typedInput)) {

            App.run(directory, input, output);
        }

        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void memberDownloadsAndReturnsToScrollList()
            throws Exception {

        createTestScroll();
        createTestUser();

        Path folder = directory.resolve("downloads");
        Files.createDirectories(folder);

        // 2 = Log in
        // 1 = View all scrolls
        // 1 = Download a scroll
        // S001 = Scroll ID
        // 0 = Return to main menu
        // 0 = Exit
        String typed = "2\nalice\ntest-password\n"
        + "1\n2\nS001\n"
        + folder + "\n0\n0\n";

        String output = runApp(typed);
        JsonScrollCatalog catalog = new JsonScrollCatalog(
                directory.resolve("scrolls.json")
        );

        Path destination = folder.resolve(
                catalog.findById("S001")
                        .orElseThrow()
                        .originalFileName()
        );

        assertTrue(output.contains("Login successful."));
        assertTrue(output.contains(
                "Scroll downloaded successfully"));

        assertTrue(Files.exists(destination));

        assertEquals(
                "Hello VSAS!\nJava Notes\n",
                Files.readString(destination)
        );


        assertEquals(
                1L,
                catalog.findById("S001")
                        .orElseThrow()
                        .downloadCount()
        );

        // The scroll list should be shown again
        String heading = "=== Available Scrolls ===";

        int first = output.indexOf(heading);
        int second = output.indexOf(
                heading, first + heading.length());

        assertTrue(first >= 0);
        assertTrue(second > first);
    }

    @Test
    void guestCannotDownloadFromApplication()
            throws Exception {

        createTestScroll();

        // Guest browses the list and attempts download
        // through the existing restricted guest menu.
       String output = runApp("3\n1\n2\n0\n0\n");

        assertTrue(output.contains(
                "User: Guest | Type: Guest"));

        assertTrue(output.contains(
                "Please log in to download a scroll."));

        JsonScrollCatalog catalog = new JsonScrollCatalog(
                directory.resolve("scrolls.json")
        );

        assertEquals(
                0L,
                catalog.findById("S001")
                        .orElseThrow()
                        .downloadCount()
        );
    }
}
