package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.storage.LocalScrollFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.storage.ScrollRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScrollUploadServiceTest {

    @TempDir
    Path tempDir;

    // Fake repository used only for testing
    static class FakeCatalog
            implements ScrollRepository {

        List<Scroll> records =
                new ArrayList<>();

        boolean failOnSave = false;

        @Override
        public List<Scroll> findAll() {
            return List.copyOf(records);
        }

        @Override
        public void save(
                Scroll scroll)
                throws IOException {

            if (failOnSave) {
                throw new IOException("Save failed.");
            }

            records.add(scroll);
        }
    }

    private ScrollUploadService createService(
            FakeCatalog catalog) {

        return new ScrollUploadService(
                new ScrollUploadValidator(),
                new LocalScrollFileStorage(
                        tempDir.resolve("data")),
                catalog,
                Clock.fixed(
                        Instant.parse("2026-10-12T08:00:00Z"),
                        ZoneOffset.UTC)
        );
    }

    @Test
    void successfulUploadRecordsOwnerAndDate()
            throws IOException {

        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "Hello Scroll");

        FakeCatalog catalog = new FakeCatalog();
        ScrollUploadService service = createService(catalog);

        var result = service.upload(
                "S001",
                "Java Notes",
                file.toString(),
                "U001"
        );

        assertEquals("S001", result.id());
        assertEquals("Java Notes", result.name());
        assertEquals("U001", result.uploaderId());

        assertEquals(
                Instant.parse("2026-10-12T08:00:00Z"),
                result.uploadedAt()
        );

        assertEquals(1, catalog.records.size());

        Path storedFile = tempDir.resolve("data")
                .resolve(result.storagePath());

        assertTrue(Files.exists(storedFile));
        assertEquals("Hello Scroll",
                au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.readString(storedFile));
        assertEquals(result.uploadedAt(), result.updatedAt());
        assertEquals("notes.txt", result.originalFileName());
        assertEquals(12, result.sizeBytes());
        assertEquals(64, result.sha256().length());
    }

    @Test
    void duplicateIdIsRejected() throws IOException {

        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "Test");

        FakeCatalog catalog = new FakeCatalog();
        ScrollUploadService service = createService(catalog);

        service.upload("S001", "Java Notes",
                file.toString(), "U001");

        assertThrows(IllegalArgumentException.class,
                () -> service.upload(
                        "S001", "New Notes",
                        file.toString(), "U001"));

        assertEquals(1, catalog.records.size());
    }

    @Test
    void duplicateNameIsRejected() throws IOException {

        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "Test");

        FakeCatalog catalog = new FakeCatalog();
        ScrollUploadService service = createService(catalog);

        service.upload("S001", "Java Notes",
                file.toString(), "U001");

        assertThrows(IllegalArgumentException.class,
                () -> service.upload(
                        "S002", "Java Notes",
                        file.toString(), "U001"));

        assertEquals(1, catalog.records.size());
    }

    @Test
    void guestCannotUpload() {

        FakeCatalog catalog = new FakeCatalog();
        ScrollUploadService service = createService(catalog);

        assertThrows(SecurityException.class,
                () -> service.upload(
                        "S001", "Notes",
                        "notes.txt", null));
    }

    @Test
    void failedSaveCleansUpCopiedFile() throws IOException {

        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "Test");

        FakeCatalog catalog = new FakeCatalog();
        catalog.failOnSave = true;

        ScrollUploadService service = createService(catalog);

        assertThrows(IOException.class,
                () -> service.upload(
                        "S001", "Notes",
                        file.toString(), "U001"));

        Path storageDir = tempDir.resolve("data")
                .resolve("scroll-files");

        try (var files = Files.list(storageDir)) {
            assertEquals(0, files.count());
        }

        assertTrue(catalog.records.isEmpty());
    }
}
