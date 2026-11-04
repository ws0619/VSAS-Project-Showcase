package au.edu.sydney.a2t0303.storage;

import au.edu.sydney.a2t0303.service.ScrollUploadService;
import au.edu.sydney.a2t0303.service.ScrollUploadValidator;
import au.edu.sydney.a2t0303.model.Scroll;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class JsonScrollCatalogTest {

    @TempDir
    Path tempDir;

    private ScrollUploadService createService(
            JsonScrollCatalog catalog) {

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
    void uploadedScrollSurvivesRestart() throws IOException {

        Path source = tempDir.resolve("notes.txt");
        Files.writeString(source, "Hello Scroll");

        Path jsonFile = tempDir.resolve("data/scrolls.json");

        JsonScrollCatalog firstCatalog =
                new JsonScrollCatalog(jsonFile);

        var uploaded = createService(firstCatalog).upload(
                "S001",
                "Java Notes",
                source.toString(),
                "U001"
        );

        // Simulate restarting by creating a new repository
        JsonScrollCatalog restartedCatalog =
                new JsonScrollCatalog(jsonFile);

        var saved = restartedCatalog.findAll();

        assertEquals(1, saved.size());
        assertEquals(uploaded, saved.get(0));

        // The binary file must also remain available
        assertTrue(Files.exists(
                tempDir.resolve("data")
                        .resolve(uploaded.storagePath())
        ));
    }

    @Test
    void duplicateIdIsRejectedAfterRestart() throws IOException {

        Path source = tempDir.resolve("notes.txt");
        Files.writeString(source, "Test");

        Path jsonFile = tempDir.resolve("data/scrolls.json");

        createService(new JsonScrollCatalog(jsonFile))
                .upload("S001", "Java Notes",
                        source.toString(), "U001");

        ScrollUploadService restartedService =
                createService(new JsonScrollCatalog(jsonFile));

        assertThrows(IllegalArgumentException.class,
                () -> restartedService.upload(
                        "S001", "Other Notes",
                        source.toString(), "U001"));
    }

    @Test
    void corruptedJsonIsNotSilentlyReset() throws IOException {

        Path jsonFile = tempDir.resolve("scrolls.json");
        Files.writeString(jsonFile, "{invalid json");

        JsonScrollCatalog catalog =
                new JsonScrollCatalog(jsonFile);

        assertThrows(IOException.class, catalog::findAll);

        assertEquals("{invalid json",
                Files.readString(jsonFile));
    }

    @Test
    void unsupportedSchemaIsRejected() throws IOException {

        Path jsonFile = tempDir.resolve("scrolls.json");

        Files.writeString(jsonFile,
                """
                {"schemaVersion": 99, "scrolls": []}
                """);

        JsonScrollCatalog catalog =
                new JsonScrollCatalog(jsonFile);

        assertThrows(IOException.class, catalog::findAll);
    }

    @Test
    void sharedRepositoryCanFindScrollById() throws IOException {

        Path source = tempDir.resolve("notes.txt");
        Files.writeString(source, "Hello Scroll");

        Path jsonFile = tempDir.resolve("data/scrolls.json");

        ScrollRepository repository =
                new JsonScrollCatalog(jsonFile);

        createService(new JsonScrollCatalog(jsonFile))
                .upload(
                        "S001",
                        "Java Notes",
                        source.toString(),
                        "U001"
                );

        var result = repository.findById("S001");

        assertTrue(result.isPresent());
        assertEquals("Java Notes", result.get().name());
        assertEquals("U001", result.get().uploaderId());

        assertTrue(repository.findById("S999").isEmpty());
    }

    private Scroll sampleScroll(String id, String name) {
        Instant now = Instant.parse("2026-10-12T08:00:00Z");

        return new Scroll(
                id,
                name,
                "U001",
                now,
                now,
                "scroll-files/123e4567-e89b-12d3-a456-426614174000.bin",
                "notes.txt",
                3,
                "a".repeat(64)
        );
    }

    @Test
    void invalidScrollRecordsAreRejected() throws IOException {

        JsonScrollCatalog repository = new JsonScrollCatalog(
                tempDir.resolve("data/scrolls.json"));

        // Null record
        assertThrows(IllegalArgumentException.class,
                () -> repository.save(null));

        // Missing ID
        assertThrows(IllegalArgumentException.class,
                () -> repository.save(
                        sampleScroll(null, "Java Notes")));

        // Missing name
        assertThrows(IllegalArgumentException.class,
                () -> repository.save(
                        sampleScroll("S001", null)));

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void repositoryRejectsDuplicateIdAndName() throws IOException {

        JsonScrollCatalog repository = new JsonScrollCatalog(
                tempDir.resolve("data/scrolls.json"));

        repository.save(sampleScroll("S001", "Java Notes"));

        // Duplicate ID
        assertThrows(IllegalArgumentException.class,
                () -> repository.save(
                        sampleScroll("S001", "Other Notes")));

        // Duplicate name
        assertThrows(IllegalArgumentException.class,
                () -> repository.save(
                        sampleScroll("S002", "Java Notes")));

        assertEquals(1, repository.findAll().size());

    }

    @Test
    void incompleteScrollMetadataIsRejected() throws IOException {

        Path jsonFile = tempDir.resolve("scrolls.json");
        JsonScrollCatalog repository =
                new JsonScrollCatalog(jsonFile);

        String validJson = """
            {
            "schemaVersion": 1,
            "scrolls": [{
                "id": "S001",
                "name": "Java Notes",
                "uploaderId": "U001",
                "uploadedAt": "2026-10-12T08:00:00Z",
                "updatedAt": "2026-10-12T08:00:00Z",
                "storagePath": "scroll-files/test.bin",
                "originalFileName": "notes.txt",
                "sizeBytes": 3,
                "sha256": "abc123"
            }]
            }
            """;

        ObjectMapper mapper = new ObjectMapper();

        // Test each required field separately
        for (String field : List.of(
                "id",
                "name",
                "uploaderId",
                "uploadedAt",
                "updatedAt",
                "storagePath",
                "originalFileName",
                "sha256")) {

            ObjectNode root =
                    (ObjectNode) mapper.readTree(validJson);

            ObjectNode scroll =
                    (ObjectNode) root.withArray("scrolls").get(0);

            scroll.putNull(field);

            Files.writeString(
                    jsonFile,
                    mapper.writeValueAsString(root));

            assertThrows(IOException.class,
                    repository::findAll,
                    "Should reject missing field: " + field);
        }

        // Negative file size must also be rejected
        ObjectNode root =
                (ObjectNode) mapper.readTree(validJson);

        ObjectNode scroll =
                (ObjectNode) root.withArray("scrolls").get(0);

        scroll.put("sizeBytes", -1);

        Files.writeString(
                jsonFile,
                mapper.writeValueAsString(root));

        assertThrows(IOException.class, repository::findAll);
    }

    @Test
    void nullScrollListAndEntriesAreRejected() throws IOException {

        Path jsonFile = tempDir.resolve("scrolls.json");

        JsonScrollCatalog repository =
                new JsonScrollCatalog(jsonFile);

        Files.writeString(jsonFile,
                """
                {"schemaVersion": 1, "scrolls": null}
                """);

        assertThrows(IOException.class, repository::findAll);

        Files.writeString(jsonFile,
                """
                {"schemaVersion": 1, "scrolls": [null]}
                """);

        assertThrows(IOException.class, repository::findAll);
    }

        @Test
        void updateChangesExistingScrollName() throws IOException {

        JsonScrollCatalog repository = new JsonScrollCatalog(
                tempDir.resolve("data/scrolls.json"));

        Scroll original = sampleScroll("S001", "Java Notes");
        repository.save(original);

        Scroll updated = new Scroll(
                original.id(),
                "Advanced Java Notes",
                original.uploaderId(),
                original.uploadedAt(),
                original.updatedAt().plusSeconds(60),
                original.storagePath(),
                original.originalFileName(),
                original.sizeBytes(),
                original.sha256()
        );

        repository.update(updated);

        assertEquals(1, repository.findAll().size());

        assertEquals(
                "Advanced Java Notes",
                repository.findById("S001").orElseThrow().name()
        );
        }

        @Test
        void updateRejectsDuplicateName() throws IOException {

        JsonScrollCatalog repository = new JsonScrollCatalog(
                tempDir.resolve("data/scrolls.json"));

        Scroll first = sampleScroll("S001", "Java Notes");
        Scroll second = sampleScroll("S002", "Python Notes");

        repository.save(first);
        repository.save(second);

        Scroll updated = new Scroll(
                first.id(),
                "Python Notes",
                first.uploaderId(),
                first.uploadedAt(),
                first.updatedAt(),
                first.storagePath(),
                first.originalFileName(),
                first.sizeBytes(),
                first.sha256()
        );

        assertThrows(IllegalArgumentException.class,
                () -> repository.update(updated));

        assertEquals("Java Notes",
                repository.findById("S001").orElseThrow().name());
        }

        @Test
        void deleteRemovesExistingScroll() throws IOException {

        JsonScrollCatalog repository = new JsonScrollCatalog(
                tempDir.resolve("data/scrolls.json"));

        repository.save(sampleScroll("S001", "Java Notes"));
        repository.save(sampleScroll("S002", "Python Notes"));

        repository.deleteById("S001");

        assertTrue(repository.findById("S001").isEmpty());
        assertTrue(repository.findById("S002").isPresent());
        assertEquals(1, repository.findAll().size());
        }

        @Test
        void deleteRejectsUnknownScroll() throws IOException {

        JsonScrollCatalog repository = new JsonScrollCatalog(
                tempDir.resolve("data/scrolls.json"));

        assertThrows(IllegalArgumentException.class,
                () -> repository.deleteById("S999"));
        }

        @Test
        void downloadCountIncrementsAndSurvivesRestart()
                throws IOException {

        Path jsonFile = tempDir.resolve("data/scrolls.json");

        JsonScrollCatalog repository =
                new JsonScrollCatalog(jsonFile);

        repository.save(sampleScroll("S001", "Java Notes"));

        // Initial download count
        assertEquals(0,
                repository.findById("S001")
                        .orElseThrow().downloadCount());

        // Simulate two successful downloads
        repository.incrementDownloadCount("S001");
        repository.incrementDownloadCount("S001");

        // Recreate the repository to simulate restart
        JsonScrollCatalog restarted =
                new JsonScrollCatalog(jsonFile);

        assertEquals(2,
                restarted.findById("S001")
                        .orElseThrow().downloadCount());

        assertEquals(1, restarted.findAll().size());
        }

        @Test
        void downloadCountRejectsUnknownScroll()
                throws IOException {

        JsonScrollCatalog repository =
                new JsonScrollCatalog(
                        tempDir.resolve("data/scrolls.json"));

        assertThrows(IllegalArgumentException.class,
                () -> repository.incrementDownloadCount("S999"));
        }

        @Test
        void regularUpdateCannotResetDownloadCount()
                throws IOException {

        JsonScrollCatalog repository =
                new JsonScrollCatalog(
                        tempDir.resolve("data/scrolls.json"));

        Scroll original = sampleScroll("S001", "Java Notes");
        repository.save(original);
        repository.incrementDownloadCount("S001");

        // Old constructor defaults downloadCount to zero
        Scroll renamed = new Scroll(
                original.id(),
                "Updated Notes",
                original.uploaderId(),
                original.uploadedAt(),
                original.updatedAt(),
                original.storagePath(),
                original.originalFileName(),
                original.sizeBytes(),
                original.sha256()
        );

        assertThrows(IllegalArgumentException.class,
                () -> repository.update(renamed));

        assertEquals(1,
                repository.findById("S001")
                        .orElseThrow().downloadCount());
        }

        
        @Test
        void saveRejectsNegativeFileSize()
                throws IOException {

        JsonScrollCatalog repository =
                new JsonScrollCatalog(
                        tempDir.resolve("data/scrolls.json"));

        Scroll original = sampleScroll(
                "S001", "Java Notes");

        Scroll invalid = new Scroll(
                original.id(),
                original.name(),
                original.uploaderId(),
                original.uploadedAt(),
                original.updatedAt(),
                original.storagePath(),
                original.originalFileName(),
                -1,
                original.sha256(),
                original.downloadCount()
        );

        assertThrows(IllegalArgumentException.class,
                () -> repository.save(invalid));

        assertTrue(repository.findAll().isEmpty());
        }

        @Test
        void oldJsonWithoutDownloadCountCanBeRead()
                throws IOException {

        Path jsonFile =
                tempDir.resolve("data/scrolls.json");

        Files.createDirectories(jsonFile.getParent());

        // Simulates JSON saved before downloadCount existed
        Files.writeString(jsonFile, """
                {
                "schemaVersion": 1,
                "scrolls": [
                {
                "id": "S001",
                "name": "Java Notes",
                "uploaderId": "U001",
                "uploadedAt": "2026-10-12T08:00:00Z",
                "updatedAt": "2026-10-12T08:00:00Z",
                "storagePath": "scroll-files/test.bin",
                "originalFileName": "notes.txt",
                "sizeBytes": 3,
                "sha256": "abc123"
                }
                ]
                }
                """);

        JsonScrollCatalog repository =
                new JsonScrollCatalog(jsonFile);

        Scroll scroll =
                repository.findById("S001").orElseThrow();

        assertEquals(0L, scroll.downloadCount());
        assertEquals("Java Notes", scroll.name());
        }

}