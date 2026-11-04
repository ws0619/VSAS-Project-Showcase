package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.ScrollVersion;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.JsonScrollCatalog;
import au.edu.sydney.a2t0303.storage.LocalScrollFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScrollVersionServiceTest {
    @TempDir Path directory;
    private JsonScrollCatalog catalog;
    private LocalScrollFileStorage storage;
    private User owner;
    private Scroll original;
    private final Instant uploaded = Instant.parse("2026-10-20T10:00:00Z");
    private final Instant replaced = Instant.parse("2026-10-21T11:00:00Z");

    @BeforeEach
    void setUp() throws IOException {
        catalog = new JsonScrollCatalog(directory.resolve("scrolls.json"));
        storage = new LocalScrollFileStorage(directory);
        owner = TestAccounts.user("U001", "alice", "Alice", Role.MEMBER, "password");
        var stored = storage.store(source("first.bin", "original bytes"));
        original = new Scroll("S001", "Notes", owner.id(), uploaded, uploaded,
                stored.storagePath(), stored.originalFileName(), stored.sizeBytes(), stored.sha256());
        catalog.save(original);
    }

    private Path source(String name, String content) throws IOException {
        return Files.writeString(directory.resolve(name), content);
    }

    private ScrollEditService editor(Instant date) {
        return new ScrollEditService(catalog, storage, Clock.fixed(date, ZoneOffset.UTC));
    }

    @Test
    void repeatedReplacementsKeepEveryFileAndDateAfterRestart() throws IOException {
        Scroll second = editor(replaced).edit(owner, "S001", null, source("second.bin", "second bytes"));
        Instant thirdDate = replaced.plusSeconds(3600);
        Scroll third = editor(thirdDate).edit(owner, "S001", "New Notes", source("third.bin", "third bytes"));

        var reopened = new JsonScrollCatalog(directory.resolve("scrolls.json"));
        var versions = new ScrollVersionService(reopened).listVersions(owner, "S001");
        assertEquals(List.of(1, 2, 3), versions.stream().map(ScrollVersion::versionNumber).toList());
        assertEquals(List.of(uploaded, replaced, thirdDate), versions.stream().map(ScrollVersion::createdAt).toList());
        assertEquals(List.of(original.storagePath(), second.storagePath(), third.storagePath()),
                versions.stream().map(ScrollVersion::storagePath).toList());
        assertEquals("original bytes", au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.readString(directory.resolve(versions.get(0).storagePath())));
        assertEquals("second bytes", au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.readString(directory.resolve(versions.get(1).storagePath())));
        assertEquals("third bytes", au.edu.sydney.a2t0303.storage.EncryptedStorageTestSupport.readString(directory.resolve(versions.get(2).storagePath())));
        assertEquals(original.sha256(), versions.get(0).sha256());
        assertEquals(original.sizeBytes(), versions.get(0).sizeBytes());
        assertEquals(third, reopened.findById("S001").orElseThrow());
        assertThrows(UnsupportedOperationException.class, versions::clear);
    }

    @Test
    void renamingAndDownloadingDoNotCreateVersionsOrChangeTheirDates() throws IOException {
        var updated = editor(replaced).edit(owner, "S001", null, source("second.bin", "second"));
        var renamed = editor(replaced.plusSeconds(3600)).edit(owner, "S001", "Renamed", null);
        catalog.incrementDownloadCount("S001");
        var counted = catalog.findById("S001").orElseThrow();
        assertEquals(updated.versions(), renamed.versions());
        assertEquals(updated.versions(), counted.versions());
        assertEquals(1, counted.downloadCount());
        assertEquals(replaced, counted.versions().get(1).createdAt());
    }

    @Test
    void failedMetadataWriteRollsBackOnlyTheNewFile() throws IOException {
        editor(replaced).edit(owner, "S001", null, source("second.bin", "second"));
        var before = catalog.findById("S001").orElseThrow();
        var failing = new JsonScrollCatalog(directory.resolve("scrolls.json")) {
            @Override public synchronized void update(Scroll scroll) throws IOException {
                throw new IOException("Simulated write failure");
            }
        };
        var editor = new ScrollEditService(failing, storage);
        assertThrows(IOException.class, () -> editor.edit(owner, "S001", null, source("third.bin", "third")));
        assertEquals(before, catalog.findById("S001").orElseThrow());
        try (var files = Files.list(directory.resolve("scroll-files"))) {
            assertEquals(2, files.count());
        }
        for (var version : before.versions()) {
            assertTrue(Files.exists(directory.resolve(version.storagePath())));
        }
    }

    @Test
    void invalidReplacementKeepsExistingHistory() throws IOException {
        editor(replaced).edit(owner, "S001", null, source("second.bin", "second"));
        var before = catalog.findById("S001").orElseThrow();
        assertThrows(IOException.class, () -> editor(replaced).edit(owner, "S001", null, directory.resolve("missing.bin")));
        assertEquals(before, catalog.findById("S001").orElseThrow());
    }

    @Test
    void ownerAndAdminCanListButOtherMembersAndGuestsCannot() throws IOException {
        var service = new ScrollVersionService(catalog);
        assertEquals(original.versions(), service.listVersions(owner, "S001"));
        var admin = TestAccounts.user("ADMIN", "admin", "Admin", Role.ADMIN, "password");
        assertEquals(original.versions(), service.listVersions(admin, "S001"));
        var other = TestAccounts.user("U002", "bob", "Bob", Role.MEMBER, "password");
        assertThrows(SecurityException.class, () -> service.listVersions(other, "S001"));
        assertThrows(SecurityException.class, () -> service.listVersions(null, "S001"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "UNKNOWN"})
    void rejectsInvalidScrollIds(String id) {
        assertThrows(IllegalArgumentException.class, () -> new ScrollVersionService(catalog).listVersions(owner, id));
    }

    @Test
    void adminReplacementIsRetainedForTheOriginalOwner() throws IOException {
        var admin = TestAccounts.user("ADMIN", "admin", "Admin", Role.ADMIN, "password");
        var updated = editor(replaced).edit(admin, "S001", null, source("admin.bin", "admin revision"));
        assertEquals(owner.id(), updated.uploaderId());
        assertEquals(2, new ScrollVersionService(catalog).listVersions(owner, "S001").size());
    }

    @Test
    void deletingScrollRemovesAllRetainedFiles() throws IOException {
        var updated = editor(replaced).edit(owner, "S001", null, source("second.bin", "second"));
        new ScrollRemoveService(catalog, storage).remove(owner, "S001");
        assertTrue(catalog.findById("S001").isEmpty());
        for (var version : updated.versions()) {
            assertFalse(Files.exists(directory.resolve(version.storagePath())));
        }
    }

    @Test
    void legacyCatalogWithoutVersionsStartsAtOneAndCanBeReplaced() throws IOException {
        Path file = directory.resolve("scrolls.json");
        Files.writeString(file, """
                {"schemaVersion":1,"scrolls":[{
                  "id":"S001","name":"Legacy","uploaderId":"U001",
                  "uploadedAt":"2026-10-20T10:00:00Z","updatedAt":"2026-10-20T10:00:00Z",
                  "storagePath":"%s","originalFileName":"first.bin",
                  "sizeBytes":%d,"sha256":"%s"
                }]}
                """.formatted(original.storagePath(), original.sizeBytes(), original.sha256()));
        var legacy = catalog.findById("S001").orElseThrow();
        assertEquals(1, legacy.versions().size());
        assertEquals(uploaded, legacy.versions().get(0).createdAt());
        var updated = editor(replaced).edit(owner, "S001", null, source("second.bin", "second"));
        assertEquals(2, updated.versions().size());
        assertEquals(0, updated.downloadCount());
        assertTrue(Files.exists(directory.resolve(original.storagePath())));
    }
}
