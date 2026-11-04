package au.edu.sydney.a2t0303.storage;


import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.ScrollVersion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class JsonScrollCatalog
        implements ScrollRepository {

    private final Path file;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // Represents the complete JSON document
    public record CatalogData(
            int schemaVersion,
            List<Scroll> scrolls
    ) {}

    public JsonScrollCatalog(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();

        Files.createDirectories(this.file.getParent());

        // Create metadata file on first startup
        if (Files.notExists(this.file)) {
            write(new CatalogData(1, List.of()));
        }
    }

    @Override
    public synchronized List<Scroll> findAll()
            throws IOException {

        return List.copyOf(read().scrolls());
    }

    @Override
    public synchronized void save(Scroll record)
            throws IOException {

        validate(record);

        List<Scroll> records =
                new ArrayList<>(read().scrolls());

        // Recheck uniqueness before saving
        for (Scroll existing : records) {

            if (existing.id().equals(record.id())) {
                throw new IllegalArgumentException(
                        "Scroll ID already exists.");
            }

            if (existing.name().equals(record.name())) {
                throw new IllegalArgumentException(
                        "Scroll name already exists.");
            }
        }

        records.add(record);

        write(new CatalogData(1, records));
    }

    private CatalogData read() throws IOException {

        CatalogData data;

        try {
            data = mapper.readValue(
                    file.toFile(), CatalogData.class);
        } catch (JsonProcessingException e) {
            throw new IOException(
                    "Invalid scroll metadata JSON.", e);
        }

        if (data == null ||
                data.schemaVersion() != 1 ||
                data.scrolls() == null) {
            throw new IOException(
                    "Invalid or unsupported scroll metadata schema.");
        }

        
    for (Scroll record : data.scrolls()) {
        try {
            validate(record);
        } catch (IllegalArgumentException e) {
            throw new IOException(
                    "Incomplete scroll metadata.", e);
        }
    }


        return data;
    }

    private void write(CatalogData data) throws IOException {

        Path tempFile = Files.createTempFile(
                file.getParent(), "scrolls-", ".tmp");

        try {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(tempFile.toFile(), data);

            try {
                Files.move(
                        tempFile, file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        tempFile, file,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Override
    public synchronized void update(Scroll scroll)
            throws IOException {

        validate(scroll);

        List<Scroll> records =
                new ArrayList<>(read().scrolls());

        int targetIndex = -1;

        for (int i = 0; i < records.size(); i++) {
            Scroll existing = records.get(i);

            if (existing.id().equals(scroll.id())) {
                targetIndex = i;
            } else if (existing.name().equals(scroll.name())) {
                throw new IllegalArgumentException(
                        "Scroll name already exists.");
            }
        }

        if (targetIndex == -1) {
            throw new IllegalArgumentException(
                    "Scroll does not exist.");
        }

        Scroll original = records.get(targetIndex);

        // Immutable ownership and original upload date
        if (!original.uploaderId().equals(scroll.uploaderId()) ||
                !original.uploadedAt().equals(scroll.uploadedAt())) {
            throw new IllegalArgumentException(
                    "Uploader and original upload date cannot change.");
        }
        if (scroll.downloadCount() != original.downloadCount()) {
            throw new IllegalArgumentException(
                    "Download count cannot be changed by update().");
        }

        // Replace existing metadata
        records.set(targetIndex, scroll);

        write(new CatalogData(1, records));
    }

    @Override
    public synchronized void deleteById(String id)
            throws IOException {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Scroll ID cannot be empty.");
        }

        List<Scroll> records =
                new ArrayList<>(read().scrolls());

        boolean removed = records.removeIf(
                scroll -> scroll.id().equals(id));

        if (!removed) {
            throw new IllegalArgumentException(
                    "Scroll does not exist.");
        }

        write(new CatalogData(1, records));
    }

    @Override
    public synchronized void incrementDownloadCount(String scrollId)
            throws IOException {

        // 1. Validate scroll ID
        if (scrollId == null || scrollId.isBlank()) {
            throw new IllegalArgumentException(
                    "Scroll ID cannot be empty.");
        }

        // 2. Load existing scrolls
        List<Scroll> records =
                new ArrayList<>(read().scrolls());

        // 3. Find the target scroll
        for (int i = 0; i < records.size(); i++) {

            Scroll current = records.get(i);

            if (current.id().equals(scrollId)) {

                if (current.downloadCount() == Long.MAX_VALUE) {
                    throw new IllegalStateException(
                            "Download count overflow.");
                }

                // 4. Create updated scroll
                Scroll updated = new Scroll(
                        current.id(),
                        current.name(),
                        current.uploaderId(),
                        current.uploadedAt(),
                        current.updatedAt(),
                        current.storagePath(),
                        current.originalFileName(),
                        current.sizeBytes(),
                        current.sha256(),
                        current.downloadCount() + 1,
                        current.versions()
                );

                // 5. Replace the existing record
                records.set(i, updated);

                // 6. Persist the updated count
                write(new CatalogData(1, records));

                return;
            }
        }

        throw new IllegalArgumentException(
                "Scroll does not exist.");
    }

    private static void validate(Scroll scroll) {

        if (scroll == null ||
                scroll.id() == null ||
                scroll.id().isBlank() ||
                scroll.name() == null ||
                scroll.name().isBlank() ||
                scroll.uploaderId() == null ||
                scroll.uploaderId().isBlank() ||
                scroll.uploadedAt() == null ||
                scroll.updatedAt() == null ||
                scroll.storagePath() == null ||
                scroll.storagePath().isBlank() ||
                scroll.originalFileName() == null ||
                scroll.originalFileName().isBlank() ||
                scroll.sha256() == null ||
                scroll.sha256().isBlank() ||
                scroll.sizeBytes() < 0 ||
                scroll.downloadCount() < 0) {

            throw new IllegalArgumentException(
                    "Invalid or incomplete scroll metadata.");
        }

        for (int i = 0; i < scroll.versions().size(); i++) {
            ScrollVersion version = scroll.versions().get(i);
            if (version.versionNumber() != i + 1 || version.createdAt() == null ||
                    version.storagePath() == null || version.storagePath().isBlank() ||
                    version.originalFileName() == null || version.originalFileName().isBlank() ||
                    version.sizeBytes() < 0 || version.sha256() == null || version.sha256().isBlank()) {
                throw new IllegalArgumentException("Invalid scroll version metadata.");
            }
        }
        ScrollVersion current = scroll.versions().get(scroll.versions().size() - 1);
        if (!current.storagePath().equals(scroll.storagePath()) ||
                !current.originalFileName().equals(scroll.originalFileName()) ||
                current.sizeBytes() != scroll.sizeBytes() ||
                !current.sha256().equals(scroll.sha256())) {
            throw new IllegalArgumentException("Current file must match the latest scroll version.");
        }
    }

}
