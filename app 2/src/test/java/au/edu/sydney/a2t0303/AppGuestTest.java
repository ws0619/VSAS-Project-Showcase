package au.edu.sydney.a2t0303;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
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
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class AppGuestTest {
    @TempDir Path directory;

    private String run(String typed) throws IOException {
        var bytes = new ByteArrayOutputStream();
        App.run(directory, new Scanner(typed), new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void applicationGuestJourneyCreatesNoAccountBeyondTheDefaultAdminOrScrollRecords() throws IOException {
        String result = run("3\n1\n2\n4\n3\n0\n");
        assertTrue(result.contains("User: Guest | Type: Guest"));
        assertTrue(result.contains("The library is currently empty."));
        assertTrue(result.contains("Please log in to upload a scroll."));
        assertTrue(result.contains("Please log in to download a scroll."));
        assertTrue(result.contains("You have returned to the start menu."));
        var users = new JsonUserRepository(directory.resolve("users.json")).findAll();
        assertEquals(1, users.size());
        assertEquals(DefaultAdminService.DEFAULT_USERNAME, users.getFirst().username());
        assertTrue(users.getFirst().isAdmin());
        assertTrue(new JsonScrollCatalog(directory.resolve("scrolls.json")).findAll().isEmpty());
        assertFalse(Files.exists(directory.resolve("scroll-files")));
    }

    @Test
    void guestBrowsesRealUploadedScrollsAfterApplicationRestartWithoutChangingFiles() throws IOException {
        Path source = directory.resolve("source.bin");
        byte[] contents = {0, 1, 2, (byte) 255, 0, 7};
        Files.write(source, contents);
        new JsonUserRepository(directory.resolve("users.json")).save(
                TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, "secret123"));
        assertTrue(run("2\nalice\nsecret123\n2\nS001\nBinary Notes\n" + source + "\n3\n0\n")
                .contains("Scroll uploaded successfully!"));
        var catalog = new JsonScrollCatalog(directory.resolve("scrolls.json"));
        var scroll = catalog.findAll().getFirst();
        String usersBefore = Files.readString(directory.resolve("users.json"));
        String scrollsBefore = Files.readString(directory.resolve("scrolls.json"));
        String output = run("3\n1\n2\n4\n0\n");
        assertTrue(output.contains("S001"));
        assertTrue(output.contains("Binary Notes"));
        assertTrue(output.contains("Please log in to download a scroll."));
        assertEquals(usersBefore, Files.readString(directory.resolve("users.json")));
        assertEquals(scrollsBefore, Files.readString(directory.resolve("scrolls.json")));
        assertArrayEquals(contents, au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.read(directory.resolve(scroll.storagePath())));
        try (var stored = Files.list(directory.resolve("scroll-files"))) {
            assertEquals(1, stored.count());
        }
    }

    @Test
    void guestDoesNotNeedReadableAccountRecordsToBrowse() throws IOException {
        Files.writeString(directory.resolve("users.json"), "{broken");
        String output = run("3\n1\n0\n");
        assertTrue(output.contains("User: Guest | Type: Guest"));
        assertTrue(output.contains("The library is currently empty."));
        assertEquals("{broken", Files.readString(directory.resolve("users.json")));
    }

    @Test
    void guestCanRecoverFromCorruptScrollDataAndExit() throws IOException {
        Files.writeString(directory.resolve("scrolls.json"), "{broken");
        String output = run("3\n1\n3\n0\n");
        assertTrue(output.contains("The scroll list could not be read."));
        assertTrue(output.contains("You have returned to the start menu."));
        assertTrue(output.contains("Goodbye!"));
        assertEquals("{broken", Files.readString(directory.resolve("scrolls.json")));
    }
}
