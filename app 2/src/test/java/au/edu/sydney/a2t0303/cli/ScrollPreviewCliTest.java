
package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.service.ScrollPreviewService;
import au.edu.sydney.a2t0303.storage.ScrollRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScrollPreviewCliTest {

    @TempDir
    Path tempDir;

    private static final String STORAGE_PATH =
            "scroll-files/123e4567-e89b-12d3-a456-426614174000.bin";

    private Scroll createScroll(long sizeBytes) {
        Instant now = Instant.parse("2026-10-21T08:00:00Z");

        return new Scroll(
                "S001",
                "Java Notes",
                "U001",
                now,
                now,
                STORAGE_PATH,
                "notes.txt",
                sizeBytes,
                "a".repeat(64)
        );
    }

    private ScrollRepository repositoryWith(Scroll scroll) {
        return new ScrollRepository() {

            @Override
            public List<Scroll> findAll() {
                return List.of(scroll);
            }

            @Override
            public void save(Scroll record) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private ScrollPreviewCli createCli(byte[] content)
            throws IOException {

        Path dataDir = tempDir.resolve("data");
        Path file = dataDir.resolve(STORAGE_PATH);

        Files.createDirectories(file.getParent());
        au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.write(file, content);

        ScrollRepository repository =
                repositoryWith(createScroll(content.length));

        ScrollPreviewService service =
                new ScrollPreviewService(repository, dataDir);

        return new ScrollPreviewCli(service);
    }

    private String capturePreview(
            ScrollPreviewCli cli, String scrollId)
            throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(
                output, true, StandardCharsets.UTF_8)) {

            cli.showPreview(scrollId, out);
        }

        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void displaysTextPreview() throws IOException {

        ScrollPreviewCli cli = createCli(
                "First line\nSecond line\nThird line\n"
                        .getBytes(StandardCharsets.UTF_8)
        );

        String output = capturePreview(cli, "S001");

        assertTrue(output.contains("Scroll ID: S001"));
        assertTrue(output.contains("Name: Java Notes"));
        assertTrue(output.contains("Type: Text"));

        assertTrue(output.contains("First line"));
        assertTrue(output.contains("Second line"));
        assertTrue(output.contains("Third line"));

        assertTrue(output.contains("End of Preview"));
    }

    @Test
    void displaysBinaryPreview() throws IOException {

        byte[] bytes = new byte[40];

        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }

        ScrollPreviewCli cli = createCli(bytes);

        String output = capturePreview(cli, "S001");

        assertTrue(output.contains("Type: Binary"));
        assertTrue(output.contains("File size: 40 bytes"));
        assertTrue(output.contains("First 32 Bytes (Hex)"));

        assertTrue(output.contains("00 01 02 03"));
        assertTrue(output.contains("1C 1D 1E 1F"));

        // Bytes 32-39 must not appear in the preview
        assertFalse(output.contains("20 21 22 23"));
    }

    @Test
    void displaysEmptyFileMessage() throws IOException {

        ScrollPreviewCli cli = createCli(new byte[0]);

        String output = capturePreview(cli, "S001");

        assertTrue(output.contains("Type: Text"));
        assertTrue(output.contains("(Empty file)"));
    }

    @Test
    void unknownScrollIdReturnsError() throws IOException {

        ScrollPreviewCli cli = createCli(
                "Example".getBytes(StandardCharsets.UTF_8)
        );

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output)) {

            assertThrows(
                    IOException.class,
                    () -> cli.showPreview("S999", out)
            );
        }
    }

    @Test
    void rejectsNullOutputStream() throws IOException {

        ScrollPreviewCli cli = createCli(
                "Example".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(
                NullPointerException.class,
                () -> cli.showPreview("S001", null)
        );
    }
}
