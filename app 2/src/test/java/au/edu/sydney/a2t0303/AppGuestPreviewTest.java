
package au.edu.sydney.a2t0303;

import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.service.DefaultAdminService;
import au.edu.sydney.a2t0303.storage.JsonScrollCatalog;
import au.edu.sydney.a2t0303.storage.JsonUserRepository;

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

class AppGuestPreviewTest {

    @TempDir
    Path directory;

    @Test
    void guestCanPreviewScrollFromList() throws IOException {

        String storagePath =
                "scroll-files/123e4567-e89b-12d3-a456-426614174000.bin";

        Path storedFile = directory.resolve(storagePath);

        Files.createDirectories(storedFile.getParent());

        byte[] content = "Hello VSAS!\nSecond line\nThird line\n"
                .getBytes(StandardCharsets.UTF_8);

        au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.write(storedFile, content);

        Instant now = Instant.parse("2026-10-21T08:00:00Z");

        Scroll scroll = new Scroll(
                "S001",
                "Java Notes",
                "U001",
                now,
                now,
                storagePath,
                "notes.txt",
                content.length,
                "a".repeat(64)
        );

        JsonScrollCatalog catalog = new JsonScrollCatalog(
                directory.resolve("scrolls.json")
        );

        catalog.save(scroll);

        // 3 = Continue as guest
        // 1 = View all scrolls
        // S001 = Select scroll for preview
        // 0 = Exit
        String userInput = "3\n1\n1\nS001\n0\n0\n";

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        App.run(
                directory,
                new Scanner(userInput),
                new PrintStream(bytes, true, StandardCharsets.UTF_8)
        );

        String output = bytes.toString(StandardCharsets.UTF_8);

        // Verify guest access
        assertTrue(output.contains("User: Guest | Type: Guest"));

        // Verify scroll listing
        assertTrue(output.contains("Java Notes"));
        assertTrue(output.contains("S001"));

        // Verify preview
        assertTrue(output.contains("=== Scroll Preview ==="));
        assertTrue(output.contains("Type: Text"));
        assertTrue(output.contains("Hello VSAS!"));
        assertTrue(output.contains("Second line"));

        // Preview must not change stored content
        assertArrayEquals(content, au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.read(storedFile));

        // US12 creates one bootstrap admin; preview must not create a guest/member account.
        var users = new JsonUserRepository(
                directory.resolve("users.json")
        ).findAll();
        assertEquals(1, users.size());
        assertEquals(DefaultAdminService.DEFAULT_USERNAME, users.getFirst().username());
        assertTrue(users.getFirst().isAdmin());

        // Existing scroll metadata must remain unchanged
        assertEquals(scroll, catalog.findById("S001").orElseThrow());
    }
}
