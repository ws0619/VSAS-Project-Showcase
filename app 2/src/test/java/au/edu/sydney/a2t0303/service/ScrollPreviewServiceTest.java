
package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.storage.ScrollRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ScrollPreviewServiceTest {

    @TempDir
    Path tempDir;

    private static final String STORED_PATH =
            "scroll-files/123e4567-e89b-12d3-a456-426614174000.bin";

    // Create test scroll metadata
    private Scroll sampleScroll(String storagePath, long sizeBytes) {
        Instant now = Instant.parse("2026-10-21T08:00:00Z");

        return new Scroll(
                "S001",
                "Java Notes",
                "U001",
                now,
                now,
                storagePath,
                "notes.txt",
                sizeBytes,
                "a".repeat(64)
        );
    }

    // Create a fake repository without changing team code
    private ScrollRepository repositoryWith(List<Scroll> scrolls) {
        return new ScrollRepository() {

            @Override
            public List<Scroll> findAll() {
                return scrolls;
            }

            @Override
            public void save(Scroll scroll) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private ScrollPreviewService serviceFor(Scroll scroll) {
        return new ScrollPreviewService(
                repositoryWith(List.of(scroll)),
                tempDir.resolve("data")
        );
    }

    // Store test bytes in the same directory structure as US04
    private Scroll storeTestFile(byte[] content) throws IOException {
        Path file = tempDir.resolve("data").resolve(STORED_PATH);

        Files.createDirectories(file.getParent());
        au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.write(file, content);

        return sampleScroll(STORED_PATH, content.length);
    }

    @Test
    void previewShowsFirstTenTextLines() throws IOException {

        String content = IntStream.rangeClosed(1, 15)
                .mapToObj(i -> "Line " + i)
                .reduce("", (a, b) -> a + b + "\n");

        Scroll scroll = storeTestFile(
                content.getBytes(StandardCharsets.UTF_8));

        var result = serviceFor(scroll).preview("S001");

        assertTrue(result.textFile());
        assertEquals("S001", result.scrollId());
        assertEquals("Java Notes", result.scrollName());

        assertEquals(10, result.textLines().size());

        assertEquals("Line 1", result.textLines().get(0));
        assertEquals("Line 10", result.textLines().get(9));

        assertFalse(result.textLines().contains("Line 11"));
        assertEquals("", result.hexPreview());
    }

    @Test
    void previewShowsFirst32BinaryBytes() throws IOException {

        byte[] content = new byte[40];

        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) i;
        }

        Scroll scroll = storeTestFile(content);

        var result = serviceFor(scroll).preview("S001");

        assertFalse(result.textFile());
        assertEquals(40, result.sizeBytes());
        assertTrue(result.textLines().isEmpty());

        String expectedHex = HexFormat.ofDelimiter(" ")
                .withUpperCase()
                .formatHex(Arrays.copyOf(content, 32));

        assertEquals(expectedHex, result.hexPreview());
    }

    @Test
    void invalidUtf8IsTreatedAsBinary() throws IOException {

        byte[] invalidUtf8 = {
                (byte) 0xC3,
                (byte) 0x28
        };

        Scroll scroll = storeTestFile(invalidUtf8);

        var result = serviceFor(scroll).preview("S001");

        assertFalse(result.textFile());
        assertEquals("C3 28", result.hexPreview());
    }

    @Test
    void textContainingNullByteIsBinary() throws IOException {

        byte[] content = "Hello\u0000World"
                .getBytes(StandardCharsets.UTF_8);

        Scroll scroll = storeTestFile(content);

        var result = serviceFor(scroll).preview("S001");

        assertFalse(result.textFile());
        assertTrue(result.hexPreview().contains("00"));
    }

    @Test
    void emptyFileCanBePreviewed() throws IOException {

        Scroll scroll = storeTestFile(new byte[0]);

        var result = serviceFor(scroll).preview("S001");

        assertTrue(result.textFile());
        assertTrue(result.textLines().isEmpty());
        assertEquals(0, result.sizeBytes());
    }

    @Test
    void unknownScrollIdIsRejected() {

        ScrollPreviewService service = new ScrollPreviewService(
                repositoryWith(List.of()),
                tempDir.resolve("data")
        );

        IOException error = assertThrows(
                IOException.class,
                () -> service.preview("S999")
        );

        assertTrue(error.getMessage().contains("not found"));
    }

    @Test
    void unsafeStoragePathIsRejected() {

        Scroll scroll = sampleScroll(
                "scroll-files/../../secret.txt", 10
        );

        assertThrows(
                IOException.class,
                () -> serviceFor(scroll).preview("S001")
        );
    }

    @Test
    void missingStoredFileIsRejected() {

        Scroll scroll = sampleScroll(STORED_PATH, 100);

        assertThrows(
                IOException.class,
                () -> serviceFor(scroll).preview("S001")
        );
    }

    @Test
    void previewDoesNotModifyStoredFile() throws IOException {

        byte[] original = "Hello from VSAS!"
                .getBytes(StandardCharsets.UTF_8);

        Scroll scroll = storeTestFile(original);

        Path file = tempDir.resolve("data").resolve(STORED_PATH);
        byte[] encryptedBefore = Files.readAllBytes(file);
        serviceFor(scroll).preview("S001");
        assertArrayEquals(encryptedBefore, Files.readAllBytes(file));
        assertArrayEquals(original, au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.read(file));
    }
}
