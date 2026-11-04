package au.edu.sydney.a2t0303.storage;

import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.ScrollVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScrollStorageMigrationTest {
    @TempDir Path directory;
    static final String FIRST = "scroll-files/123e4567-e89b-12d3-a456-426614174000.bin";
    static final String SECOND = "scroll-files/123e4567-e89b-12d3-a456-426614174001.bin";
    private JsonScrollCatalog catalog() throws IOException { return new JsonScrollCatalog(directory.resolve("scrolls.json")); }
    private LocalScrollFileStorage storage() { return new LocalScrollFileStorage(directory); }
    private int migrate() throws IOException { return new ScrollStorageMigration(catalog(), storage()).migrate(); }
    private static String hash(byte[] bytes) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private ScrollVersion legacy(String path, int number, byte[] content) throws Exception {
        Path file = directory.resolve(path); Files.createDirectories(file.getParent()); Files.write(file, content);
        return new ScrollVersion(number, Instant.parse("2026-10-20T10:00:00Z").plusSeconds(number), path,
                "version"+number+".bin", content.length, hash(content));
    }
    private Scroll save(ScrollVersion... versions) throws IOException {
        var latest = versions[versions.length - 1];
        var scroll = new Scroll("S001", "Legacy", "U001", versions[0].createdAt(), latest.createdAt(),
                latest.storagePath(), latest.originalFileName(), latest.sizeBytes(), latest.sha256(), 7, List.of(versions));
        catalog().save(scroll); return scroll;
    }

    @Test
    void migratesCurrentAndHistoricalFilesWithoutChangingMetadata() throws Exception {
        byte[] first = "original\n你好".getBytes(StandardCharsets.UTF_8);
        byte[] second = {0, (byte)255, 1};
        Scroll before = save(legacy(FIRST, 1, first), legacy(SECOND, 2, second));
        byte[] json = Files.readAllBytes(directory.resolve("scrolls.json"));
        assertEquals(2, migrate());
        assertArrayEquals(json, Files.readAllBytes(directory.resolve("scrolls.json")));
        assertEquals(before, catalog().findById("S001").orElseThrow());
        assertTrue(ScrollFileEncryption.isEncrypted(directory.resolve(FIRST)));
        assertTrue(ScrollFileEncryption.isEncrypted(directory.resolve(SECOND)));
        assertArrayEquals(first, storage().readAllBytes(FIRST, 1000));
        assertArrayEquals(second, storage().readAllBytes(SECOND, 1000));
        byte[] encrypted = Files.readAllBytes(directory.resolve(FIRST));
        assertEquals(0, migrate());
        assertArrayEquals(encrypted, Files.readAllBytes(directory.resolve(FIRST)));
    }

    @Test
    void legacyPayloadBeginningWithMagicIsStillMigrated() throws Exception {
        byte[] bytes = "VSASENC1 is the literal content of this old scroll".getBytes(StandardCharsets.UTF_8);
        save(legacy(FIRST, 1, bytes));
        assertEquals(1, migrate());
        assertArrayEquals(bytes, storage().readAllBytes(FIRST, 1000));
    }

    @Test
    void incorrectLegacyMetadataStopsMigrationWithoutReplacingFile() throws Exception {
        var version = legacy(FIRST, 1, new byte[]{1,2,3});
        save(new ScrollVersion(1, version.createdAt(), FIRST, "x.bin", 3, "a".repeat(64)));
        byte[] before = Files.readAllBytes(directory.resolve(FIRST));
        assertThrows(IOException.class, this::migrate);
        assertArrayEquals(before, Files.readAllBytes(directory.resolve(FIRST)));
        try (var files = Files.list(directory.resolve("scroll-files"))) { assertEquals(1, files.count()); }
    }

    @Test
    void damagedEncryptedHeaderIsNotSilentlyMigratedAsLegacy() throws Exception {
        var stored = storage().store(Files.writeString(directory.resolve("source.txt"), "original"));
        save(new ScrollVersion(1, Instant.now(), stored.storagePath(), "source.txt", stored.sizeBytes(), stored.sha256()));
        Path path = directory.resolve(stored.storagePath());
        byte[] corrupt = Files.readAllBytes(path); corrupt[0]^=1; Files.write(path,corrupt);
        assertThrows(IOException.class, this::migrate);
        assertArrayEquals(corrupt, Files.readAllBytes(path));
    }

    @Test
    void missingKeyStopsMigrationAndIsNotRecreated() throws Exception {
        var stored = storage().store(Files.writeString(directory.resolve("source.txt"), "original"));
        save(new ScrollVersion(1, Instant.now(), stored.storagePath(), "source.txt", stored.sizeBytes(), stored.sha256()));
        Files.delete(directory.resolve(ScrollFileEncryption.KEY_FILE));
        assertThrows(IOException.class, this::migrate);
        assertFalse(Files.exists(directory.resolve(ScrollFileEncryption.KEY_FILE)));
    }

    @Test
    void handlesEmptyLibraryMissingBinariesAndOrphanLegacyFiles() throws Exception {
        assertEquals(0,migrate());
        assertFalse(Files.exists(directory.resolve(ScrollFileEncryption.KEY_FILE)));
        var v = legacy(FIRST,1,new byte[0]); save(v); Files.delete(directory.resolve(FIRST));
        assertEquals(0,migrate());
        legacy(SECOND,2,new byte[]{17,18});
        assertEquals(1,migrate());
        assertArrayEquals(new byte[]{17,18},storage().readAllBytes(SECOND,100));
    }
}
