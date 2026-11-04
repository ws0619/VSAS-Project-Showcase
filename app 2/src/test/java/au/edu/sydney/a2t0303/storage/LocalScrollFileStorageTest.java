package au.edu.sydney.a2t0303.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalScrollFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void validFileIsCopiedIntoStorage() throws IOException {

        Path source = tempDir.resolve("notes.pdf");
        byte[] content = {1, 2, 3, 4, 5};
        Files.write(source, content);

        LocalScrollFileStorage storage =
                new LocalScrollFileStorage(tempDir.resolve("data"));

        String storedPath = storage.copy(source);

        Path destination =
                tempDir.resolve("data").resolve(storedPath);

        assertTrue(Files.exists(destination));
        assertTrue(storedPath.startsWith("scroll-files/"));
        assertArrayEquals(content, EncryptedStorageTestSupport.read(destination));
        assertArrayEquals(content, Files.readAllBytes(source));
    }

    @Test
    void repeatedUploadsDoNotOverwriteEachOther() throws IOException {

        Path source = tempDir.resolve("notes.txt");
        Files.writeString(source, "Hello Scroll");

        LocalScrollFileStorage storage =
                new LocalScrollFileStorage(tempDir.resolve("data"));

        String first = storage.copy(source);
        String second = storage.copy(source);

        assertNotEquals(first, second);
        assertTrue(Files.exists(
                tempDir.resolve("data").resolve(first)));
        assertTrue(Files.exists(
                tempDir.resolve("data").resolve(second)));
    }

    @Test
    void missingFileIsRejected() {

        LocalScrollFileStorage storage =
                new LocalScrollFileStorage(tempDir.resolve("data"));

        Path missing = tempDir.resolve("missing.pdf");

        assertThrows(IOException.class,
                () -> storage.copy(missing));
    }

    @Test
    void nullSourceIsRejected() {

        LocalScrollFileStorage storage =
                new LocalScrollFileStorage(tempDir.resolve("data"));

        assertThrows(IOException.class,
                () -> storage.copy(null));
    }

    @Test
    void directorySourceIsRejected() {

        LocalScrollFileStorage storage =
                new LocalScrollFileStorage(tempDir.resolve("data"));

        assertThrows(IOException.class,
                () -> storage.copy(tempDir));
    }

    @Test
    void nullStoragePathIsRejected() {

        LocalScrollFileStorage storage =
                new LocalScrollFileStorage(tempDir.resolve("data"));

        assertThrows(IOException.class,
                () -> storage.delete(null));
    }

    @Test
    void unsafeStoragePathIsRejected() {

        LocalScrollFileStorage storage =
                new LocalScrollFileStorage(tempDir.resolve("data"));

        assertThrows(IOException.class,
                () -> storage.delete("../other.txt"));
    }

    @Test
    void deletingMissingStoredFileIsSafe() throws IOException {

        LocalScrollFileStorage storage =
                new LocalScrollFileStorage(tempDir.resolve("data"));

        String missingFile =
                "scroll-files/123e4567-e89b-12d3-a456-426614174000.bin";

        assertDoesNotThrow(
                () -> storage.delete(missingFile));
    }

    @Test
    void deletingStoredFileRemovesIt() throws IOException {

        Path source = tempDir.resolve("notes.txt");
        Files.writeString(source, "Test");

        Path dataDir = tempDir.resolve("data");

        LocalScrollFileStorage storage =
                new LocalScrollFileStorage(dataDir);

        String storedPath = storage.copy(source);

        Path destination = dataDir.resolve(storedPath);

        assertTrue(Files.exists(destination));

        storage.delete(storedPath);

        assertFalse(Files.exists(destination));
        assertTrue(Files.exists(source));
    }

    @Test
    void storedFileContainsCorrectMetadata() throws IOException {

        Path source = tempDir.resolve("notes.txt");
        Files.writeString(source, "abc");

        LocalScrollFileStorage storage =
                new LocalScrollFileStorage(tempDir.resolve("data"));

        var result = storage.store(source);

        assertEquals("notes.txt", result.originalFileName());
        assertEquals(3, result.sizeBytes());

        // Expected SHA-256 of "abc"
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223" +
            "b00361a396177a9cb410ff61f20015ad",
            result.sha256()
        );

        Path storedFile = tempDir.resolve("data")
                .resolve(result.storagePath());

        assertTrue(Files.exists(storedFile));
        assertEquals("abc", EncryptedStorageTestSupport.readString(storedFile));
        assertEquals("abc", Files.readString(source));
    }


}
