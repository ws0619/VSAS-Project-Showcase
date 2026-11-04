package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.service.ScrollUploadService;
import au.edu.sydney.a2t0303.service.ScrollUploadValidator;
import au.edu.sydney.a2t0303.storage.JsonScrollCatalog;
import au.edu.sydney.a2t0303.storage.LocalScrollFileStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class ScrollUploadCliTest {

    @TempDir
    Path tempDir;

    private JsonScrollCatalog createRepository()
            throws IOException {

        return new JsonScrollCatalog(
                tempDir.resolve("data/scrolls.json"));
    }

    private String runCli(
            String userInput,
            String authenticatedUserId)
            throws IOException {

        Path dataDir = tempDir.resolve("data");

        ScrollUploadService service = new ScrollUploadService(
                new ScrollUploadValidator(),
                new LocalScrollFileStorage(dataDir),
                createRepository(),
                Clock.systemUTC()
        );

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        ScrollUploadCli cli = new ScrollUploadCli(
                service,
                new Scanner(userInput),
                new PrintStream(
                        output, true,
                        StandardCharsets.UTF_8)
        );

        cli.upload(authenticatedUserId);

        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void successfulUpload() throws IOException {

        Path source = tempDir.resolve("notes.txt");
        Files.writeString(source, "Hello Scroll");

        String result = runCli(
                "S001\nJava Notes\n" + source + "\n",
                "U001"
        );

        assertTrue(result.contains(
                "Scroll uploaded successfully!"));

        var scrolls = createRepository().findAll();

        assertEquals(1, scrolls.size());
        assertEquals("S001", scrolls.get(0).id());
        assertEquals("U001", scrolls.get(0).uploaderId());

        assertTrue(Files.exists(
                tempDir.resolve("data")
                        .resolve(scrolls.get(0).storagePath())
        ));
    }

    @Test
    void missingFileIsRejectedImmediatelyAndCanBeCancelled() throws IOException {

        Path missing = tempDir.resolve("missing.txt");

        String result = runCli(
                "S002\nMissing Notes\n" + missing + "\n\n",
                "U001"
        );

        assertTrue(result.contains(
                "File does not exist or is not a regular file. Please try again or press Enter to cancel."));
        assertTrue(result.contains("Upload cancelled."));
        assertTrue(createRepository().findAll().isEmpty());
    }

    @Test
    void unauthenticatedUserIsRejected() throws IOException {

        String result = runCli("", null);

        assertTrue(result.contains(
                "Login is required to upload."));

        assertFalse(result.contains("Enter Scroll ID:"));
    }

    @Test
    void incompleteInputCancelsUpload() throws IOException {

        String result = runCli("S001\n", "U001");

        assertTrue(result.contains("Upload cancelled."));
        assertTrue(createRepository().findAll().isEmpty());
    }

    @Test
    void duplicateScrollIdShowsError() throws IOException {

        Path source = tempDir.resolve("notes.txt");
        Files.writeString(source, "Hello Scroll");

        runCli(
                "S001\nJava Notes\n" + source + "\n",
                "U001"
        );

        String result = runCli("S001\n\n", "U001");

        // The duplicate ID is reported before the name is requested
        assertTrue(result.contains(
                "Scroll ID already exists. Please try again or press Enter to cancel."));
        assertFalse(result.contains("Enter Scroll Name:"));
        assertTrue(result.contains("Upload cancelled."));
        assertEquals(1, createRepository().findAll().size());
    }

    @Test
    void duplicateNameIsRejectedBeforeTheFilePathIsRequested() throws IOException {

        Path source = tempDir.resolve("notes.txt");
        Files.writeString(source, "Hello Scroll");
        runCli("S001\nJava Notes\n" + source + "\n", "U001");

        String result = runCli("S002\nJava Notes\n\n", "U001");

        assertTrue(result.contains(
                "Scroll name already exists. Please try again or press Enter to cancel."));
        assertFalse(result.contains("Enter File Path:"));
        assertEquals(1, createRepository().findAll().size());
    }

    @Test
    void onlyTheWrongValueIsAskedAgainAndUploadThenSucceeds() throws IOException {

        Path source = tempDir.resolve("notes.txt");
        Files.writeString(source, "Hello Scroll");
        runCli("S001\nJava Notes\n" + source + "\n", "U001");
        Path missing = tempDir.resolve("missing.txt");

        String result = runCli(
                "S001\n S002 \nJava Notes\nPython Notes\n" + missing + "\n" + source + "\n",
                "U001"
        );

        assertTrue(result.contains("Scroll ID already exists."));
        assertTrue(result.contains("Scroll name already exists."));
        assertTrue(result.contains("File does not exist or is not a regular file."));
        assertTrue(result.contains("Scroll uploaded successfully!"));

        var saved = createRepository().findById("S002").orElseThrow();
        assertEquals("Python Notes", saved.name());
        assertEquals("U001", saved.uploaderId());
    }

    @Test
    void blankScrollIdCancelsWithoutFurtherPrompts() throws IOException {

        String result = runCli("\n", "U001");

        assertTrue(result.contains("Upload cancelled."));
        assertFalse(result.contains("Enter Scroll Name:"));
        assertTrue(createRepository().findAll().isEmpty());
    }
}
