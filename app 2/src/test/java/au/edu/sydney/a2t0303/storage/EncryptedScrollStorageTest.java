package au.edu.sydney.a2t0303.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EncryptedScrollStorageTest {
    @TempDir Path directory;
    private Path data() { return directory.resolve("data"); }
    private LocalScrollFileStorage storage() { return new LocalScrollFileStorage(data()); }
    private Path source(byte[] bytes) throws IOException { return Files.write(directory.resolve("source.bin"), bytes); }
    private byte[] read(String path) throws IOException { return storage().readAllBytes(path, 10 * 1024 * 1024); }

    @Test
    void diskContainsCiphertextAndIndependentJceDecryptionReturnsOriginal() throws Exception {
        byte[] original = "PRIVATE SCROLL CONTENT — 你好\nSecond line".getBytes(StandardCharsets.UTF_8);
        var stored = storage().store(source(original));
        byte[] encrypted = Files.readAllBytes(data().resolve(stored.storagePath()));
        assertFalse(Arrays.equals(original, encrypted));
        assertFalse(new String(encrypted, StandardCharsets.ISO_8859_1).contains("PRIVATE SCROLL CONTENT"));
        assertEquals(original.length + ScrollFileEncryption.OVERHEAD, encrypted.length);
        assertEquals(original.length, stored.sizeBytes());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)), stored.sha256());
        byte[] key = Files.readAllBytes(data().resolve(ScrollFileEncryption.KEY_FILE));
        assertEquals(32, key.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, Arrays.copyOfRange(encrypted, 8, 20)));
        cipher.updateAAD(Arrays.copyOfRange(encrypted, 0, 8));
        cipher.updateAAD(stored.storagePath().getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(original, cipher.doFinal(encrypted, 20, encrypted.length - 20));
        assertArrayEquals(original, read(stored.storagePath()));
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("source.bin")));
    }

    @Test
    void repeatedUploadsUseDifferentNoncesAndOnePersistentKey() throws IOException {
        Path source = source("same content".getBytes(StandardCharsets.UTF_8));
        var first = storage().store(source);
        byte[] key = Files.readAllBytes(data().resolve(ScrollFileEncryption.KEY_FILE));
        var second = storage().store(source);
        byte[] a = Files.readAllBytes(data().resolve(first.storagePath()));
        byte[] b = Files.readAllBytes(data().resolve(second.storagePath()));
        assertFalse(Arrays.equals(Arrays.copyOfRange(a, 8, 20), Arrays.copyOfRange(b, 8, 20)));
        assertArrayEquals(key, Files.readAllBytes(data().resolve(ScrollFileEncryption.KEY_FILE)));
        assertEquals(first.sha256(), second.sha256());
        assertArrayEquals(Files.readAllBytes(source), read(first.storagePath()));
        assertArrayEquals(Files.readAllBytes(source), read(second.storagePath()));
    }

    @Test
    void emptyAndArbitraryBinaryFilesRoundTrip() throws IOException {
        for (int size : new int[]{0, 1, 16, 8193, 128 * 1024}) {
            byte[] bytes = new byte[size]; new Random(17 + size).nextBytes(bytes);
            var stored = storage().store(source(bytes));
            assertArrayEquals(bytes, read(stored.storagePath()));
            assertEquals(size, stored.sizeBytes());
        }
    }

    @Test
    void keySurvivesNewStorageInstancesAndRelativeDataPaths() throws IOException {
        var stored = storage().store(source(new byte[]{0, (byte)255, 18}));
        var reopened = new LocalScrollFileStorage(Path.of("").toAbsolutePath().relativize(data().toAbsolutePath()));
        assertArrayEquals(new byte[]{0, (byte)255, 18}, reopened.readAllBytes(stored.storagePath(), 10));
    }

    @Test
    void differentLibrariesGenerateIndependentKeys() throws IOException {
        storage().store(source(new byte[]{1}));
        Path other = directory.resolve("other");
        new LocalScrollFileStorage(other).store(directory.resolve("source.bin"));
        assertFalse(Arrays.equals(Files.readAllBytes(data().resolve(ScrollFileEncryption.KEY_FILE)),
                Files.readAllBytes(other.resolve(ScrollFileEncryption.KEY_FILE))));
    }

    @Test
    void missingKeyFailsReadsAndNewUploadsWithoutReplacingKey() throws IOException {
        var stored = storage().store(source(new byte[]{1, 2, 3}));
        Files.delete(data().resolve(ScrollFileEncryption.KEY_FILE));
        assertThrows(IOException.class, () -> read(stored.storagePath()));
        assertThrows(IOException.class, () -> storage().store(directory.resolve("source.bin")));
        assertFalse(Files.exists(data().resolve(ScrollFileEncryption.KEY_FILE)));
        try (var files = Files.list(data().resolve("scroll-files"))) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void wrongAndMalformedKeysAreRejected() throws IOException {
        var stored = storage().store(source(new byte[]{1, 2, 3}));
        Path key = data().resolve(ScrollFileEncryption.KEY_FILE);
        Files.write(key, new byte[32]);
        assertThrows(IOException.class, () -> read(stored.storagePath()));
        Files.write(key, new byte[3]);
        assertThrows(IOException.class, () -> read(stored.storagePath()));
        assertThrows(IOException.class, () -> storage().store(directory.resolve("source.bin")));
        assertEquals(3, Files.size(key));
    }

    @Test
    void ciphertextNonceTagAndHeaderTamperingAreRejected() throws IOException {
        var stored = storage().store(source(new byte[300]));
        Path file = data().resolve(stored.storagePath());
        byte[] original = Files.readAllBytes(file);
        for (int index : new int[]{0, 8, 40, original.length - 1}) {
            byte[] changed = original.clone(); changed[index] ^= 1; Files.write(file, changed);
            assertThrows(IOException.class, () -> read(stored.storagePath()), "Tamper at " + index);
        }
        Files.write(file, original);
        assertArrayEquals(new byte[300], read(stored.storagePath()));
    }

    @Test
    void truncatedFilesAndSwappedStoredFilesAreRejected() throws IOException {
        var a = storage().store(source(new byte[50]));
        var b = storage().store(source(new byte[50]));
        byte[] original = Files.readAllBytes(data().resolve(a.storagePath()));
        Files.write(data().resolve(b.storagePath()), original);
        assertThrows(IOException.class, () -> read(b.storagePath()));
        for (int size : new int[]{0, 8, 19, 35, original.length - 1}) {
            Files.write(data().resolve(a.storagePath()), Arrays.copyOf(original, size));
            assertThrows(IOException.class, () -> read(a.storagePath()));
        }
    }

    @Test
    void oversizedCiphertextIsRejectedBeforeDecrypting() throws IOException {
        var stored = storage().store(source(new byte[1024]));
        assertThrows(IOException.class, () -> storage().readAllBytes(stored.storagePath(), 100));
    }

    @Test
    void normalReadsNeverAcceptUnmarkedPlaintext() throws IOException {
        Path file = data().resolve("scroll-files/123e4567-e89b-12d3-a456-426614174000.bin");
        Files.createDirectories(file.getParent()); Files.writeString(file, "legacy plaintext");
        assertThrows(IOException.class, () -> read("scroll-files/" + file.getFileName()));
        assertFalse(Files.exists(data().resolve(ScrollFileEncryption.KEY_FILE)));
    }

    @Test
    void symbolicStorageDirectoryAndKeyAreRejected() throws IOException {
        Path outside = directory.resolve("outside"); Files.createDirectories(outside);
        Files.createDirectories(data());
        try { Files.createSymbolicLink(data().resolve("scroll-files"), outside); }
        catch (IOException | UnsupportedOperationException e) { Assumptions.abort("Symlinks unavailable"); }
        assertThrows(IOException.class, () -> storage().store(source(new byte[]{1})));
        Files.delete(data().resolve("scroll-files"));
        Files.createSymbolicLink(data().resolve(ScrollFileEncryption.KEY_FILE), Files.write(directory.resolve("outside-key"), new byte[32]));
        assertThrows(IOException.class, () -> storage().store(source(new byte[]{1})));
    }

    @Test
    void keyHasOwnerOnlyPermissionsOnPosixFilesystems() throws IOException {
        storage().store(source(new byte[]{1}));
        Path key = data().resolve(ScrollFileEncryption.KEY_FILE);
        Assumptions.assumeTrue(Files.getFileAttributeView(key, java.nio.file.attribute.PosixFileAttributeView.class) != null);
        assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(key));
    }
}
