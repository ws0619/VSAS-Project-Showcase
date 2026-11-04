package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.App;
import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.*;
import au.edu.sydney.a2t0303.storage.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EncryptedScrollIntegrationTest {
    @TempDir Path directory;
    private Path data;
    private JsonScrollCatalog catalog;
    private LocalScrollFileStorage storage;
    private AuthenticationService authentication;
    private User owner;
    private final Instant firstDate = Instant.parse("2026-10-20T10:00:00Z");

    @BeforeEach
    void setUp() throws IOException {
        data = directory.resolve("data");
        catalog = new JsonScrollCatalog(data.resolve("scrolls.json"));
        storage = new LocalScrollFileStorage(data);
        var users = new JsonUserRepository(data.resolve("users.json"));
        owner = TestAccounts.user("U001", "alice", "Alice", Role.MEMBER, "password");
        users.save(owner);
        authentication = new AuthenticationService(users, new PasswordHasher());
        assertTrue(authentication.login("alice", "password"));
    }
    private Path source(byte[] content) throws IOException { return Files.write(directory.resolve("source.bin"), content); }
    private Scroll upload(byte[] content) throws IOException {
        return new ScrollUploadService(new ScrollUploadValidator(), storage, catalog,
                Clock.fixed(firstDate, ZoneOffset.UTC), authentication)
                .upload("S001", "Encrypted Notes", source(content).toString(), owner.id());
    }
    private ScrollPreviewService preview() { return new ScrollPreviewService(catalog, data); }
    private ScrollDownloadService download() { return new ScrollDownloadService(catalog, authentication, data); }
    private String run(String input) throws IOException {
        var bytes = new ByteArrayOutputStream();
        App.run(data, new Scanner(input), new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void textPreviewAndDownloadReturnOriginalUtf8AndLineEndings() throws IOException {
        byte[] original = "Private notes 你好\r\nSecond line\nThird line\rFinal line".getBytes(StandardCharsets.UTF_8);
        Scroll scroll = upload(original);
        byte[] encrypted = Files.readAllBytes(data.resolve(scroll.storagePath()));
        var result = preview().preview("S001");
        assertTrue(result.textFile());
        assertEquals(List.of("Private notes 你好", "Second line", "Third line", "Final line"), result.textLines());
        assertEquals(original.length, result.sizeBytes());
        Path destination = directory.resolve("download.bin");
        download().download("S001", destination);
        assertArrayEquals(original, Files.readAllBytes(destination));
        assertEquals(1, catalog.findById("S001").orElseThrow().downloadCount());
        assertArrayEquals(encrypted, Files.readAllBytes(data.resolve(scroll.storagePath())));
    }

    @Test
    void binaryAndEmptyFilePreviewAndDownloadAreUnchanged() throws IOException {
        byte[] bytes = new byte[1024]; new Random(5).nextBytes(bytes); bytes[0]=0;
        upload(bytes);
        var result = preview().preview("S001");
        assertFalse(result.textFile());
        assertEquals(HexFormat.ofDelimiter(" ").withUpperCase().formatHex(Arrays.copyOf(bytes,32)), result.hexPreview());
        Path downloaded = directory.resolve("binary.bin"); download().download("S001", downloaded);
        assertArrayEquals(bytes, Files.readAllBytes(downloaded));
        new ScrollEditService(catalog,storage).edit(owner,"S001",null,source(new byte[0]));
        assertTrue(preview().preview("S001").textFile());
        assertEquals(List.of(),preview().preview("S001").textLines());
        Path empty=directory.resolve("empty.bin"); download().download("S001",empty);
        assertEquals(0,Files.size(empty));
    }

    @Test
    void replacementRetainsEncryptedVersionsAndTheirPlaintextMetadataAcrossRestart() throws IOException {
        byte[] first = "PRIVATE FIRST CONTENT".getBytes(StandardCharsets.UTF_8);
        byte[] second = new byte[]{0, (byte)255, 42};
        var original = upload(first);
        byte[] oldCipher = Files.readAllBytes(data.resolve(original.storagePath()));
        var updated = new ScrollEditService(catalog, storage,
                Clock.fixed(firstDate.plusSeconds(3600),ZoneOffset.UTC)).edit(owner,"S001",null,source(second));
        assertArrayEquals(oldCipher,Files.readAllBytes(data.resolve(original.storagePath())));
        assertFalse(Arrays.equals(second,Files.readAllBytes(data.resolve(updated.storagePath()))));
        assertEquals(2,updated.versions().size());
        assertEquals(0,new ScrollStorageMigration(catalog,storage).migrate());
        var reopened = new JsonScrollCatalog(data.resolve("scrolls.json"));
        var versions = new ScrollVersionService(reopened).listVersions(owner,"S001");
        assertEquals(updated.versions(),versions);
        assertArrayEquals(first,new LocalScrollFileStorage(data).readAllBytes(versions.get(0).storagePath(),100));
        assertArrayEquals(second,new LocalScrollFileStorage(data).readAllBytes(versions.get(1).storagePath(),100));
        assertEquals(first.length,versions.get(0).sizeBytes());
        assertEquals(second.length,versions.get(1).sizeBytes());
        assertEquals(List.of(firstDate,firstDate.plusSeconds(3600)),versions.stream().map(ScrollVersion::createdAt).toList());
        Path target=directory.resolve("latest.bin");download().download("S001",target);
        assertArrayEquals(second,Files.readAllBytes(target));
        new ScrollRemoveService(catalog,storage).remove(owner,"S001");
        for(var version:versions) assertFalse(Files.exists(data.resolve(version.storagePath())));
    }

    @Test
    void authenticationFailurePublishesNoDownloadAndDoesNotIncrementCount() throws IOException {
        var original=upload(new byte[]{1,2,3,4});
        Path cipher=data.resolve(original.storagePath());
        byte[] bytes=Files.readAllBytes(cipher);bytes[bytes.length-1]^=1;Files.write(cipher,bytes);
        Path destination=directory.resolve("download.bin");
        assertThrows(IOException.class,()->download().download("S001",destination));
        assertThrows(IOException.class,()->preview().preview("S001"));
        assertFalse(Files.exists(destination));
        assertEquals(0,catalog.findById("S001").orElseThrow().downloadCount());
        try(var files=Files.list(directory)) { assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith("vsas-download-"))); }
    }

    @Test
    void previewLimitUsesPlaintextSizeAndDownloadStillHandlesLargerFiles() throws IOException {
        byte[] boundary = new byte[5*1024*1024]; Arrays.fill(boundary,(byte)'a');
        upload(boundary);
        assertEquals(boundary.length,preview().preview("S001").sizeBytes());
        byte[] large=Arrays.copyOf(boundary,boundary.length+1);
        new ScrollEditService(catalog,storage).edit(owner,"S001",null,source(large));
        assertThrows(IOException.class,()->preview().preview("S001"));
        Path target=directory.resolve("large.bin");download().download("S001",target);
        assertArrayEquals(large,Files.readAllBytes(target));
    }

    @Test
    void realCliUploadGuestPreviewAndOwnerDownloadWorkWithoutMenuChanges() throws IOException {
        byte[] original="Encrypted application journey\nSecond line".getBytes(StandardCharsets.UTF_8);
        Path file=source(original);
        assertTrue(run("2\nalice\npassword\n2\nS001\nCLI Notes\n"+file+"\n0\n").contains("Scroll uploaded successfully!"));
        String guest=run("3\n1\n1\nS001\n0\n0\n");
        assertTrue(guest.contains("Encrypted application journey"));
        var downloaded=Files.createDirectory(directory.resolve("downloads"));
        String ownerOutput=run("2\nalice\npassword\n1\n2\nS001\n"+downloaded+"\n0\n0\n");
        assertTrue(Files.exists(downloaded.resolve("source.bin")),ownerOutput);
        assertArrayEquals(original,Files.readAllBytes(downloaded.resolve("source.bin")));
    }

    @Test
    void startupMigratesLegacyScrollAndGuestCanPreviewItsOriginalText() throws Exception {
        byte[] original="Legacy secret\nSecond legacy line".getBytes(StandardCharsets.UTF_8);
        String relative="scroll-files/123e4567-e89b-12d3-a456-426614174000.bin";
        Files.createDirectories(data.resolve("scroll-files"));Files.write(data.resolve(relative),original);
        catalog.save(new Scroll("S001","Legacy","U001",firstDate,firstDate,relative,"legacy.txt",original.length,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original))));
        byte[] metadata=Files.readAllBytes(data.resolve("scrolls.json"));
        String output=run("3\n1\n1\nS001\n0\n0\n");
        assertTrue(output.contains("Legacy secret"));
        assertFalse(Arrays.equals(original,Files.readAllBytes(data.resolve(relative))));
        assertArrayEquals(metadata,Files.readAllBytes(data.resolve("scrolls.json")));
        assertTrue(run("3\n1\n1\nS001\n0\n0\n").contains("Legacy secret"));
    }
}
