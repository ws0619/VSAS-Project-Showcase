package au.edu.sydney.a2t0303.storage;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;

/** Versioned AES-256-GCM envelope. The key never lives in scroll-files. */
final class ScrollFileEncryption {
    static final String KEY_FILE = "scroll-storage.key";
    private static final byte[] MAGIC = "VSASENC1".getBytes(StandardCharsets.US_ASCII);
    private static final int NONCE_BYTES = 12;
    static final int OVERHEAD = MAGIC.length + NONCE_BYTES + 16;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Object KEY_LOCK = new Object();
    private final Path dataDirectory;

    ScrollFileEncryption(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    record PlaintextMetadata(long sizeBytes, String sha256) {}

    static boolean isEncrypted(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return Arrays.equals(MAGIC, input.readNBytes(MAGIC.length));
        }
    }

    void requireExistingKey() throws IOException {
        key(false);
    }

    PlaintextMetadata encrypt(InputStream input, OutputStream output, String storagePath)
            throws IOException {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, storagePath, true);
        MessageDigest digest = sha256();
        output.write(MAGIC);
        output.write(nonce);
        byte[] buffer = new byte[8192];
        long size = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            digest.update(buffer, 0, count);
            size += count;
            write(output, cipher.update(buffer, 0, count));
        }
        finish(cipher, output);
        return new PlaintextMetadata(size, HexFormat.of().formatHex(digest.digest()));
    }

    void decrypt(InputStream input, OutputStream output, String storagePath) throws IOException {
        if (!Arrays.equals(MAGIC, input.readNBytes(MAGIC.length))) {
            throw new IOException("Invalid encrypted scroll header. Legacy files must be migrated at startup.");
        }
        byte[] nonce = input.readNBytes(NONCE_BYTES);
        if (nonce.length != NONCE_BYTES) {
            throw new IOException("Encrypted scroll is truncated.");
        }
        Cipher cipher = cipher(Cipher.DECRYPT_MODE, nonce, storagePath, false);
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            write(output, cipher.update(buffer, 0, count));
        }
        // Never suppress authentication failures or return ciphertext as plaintext.
        finish(cipher, output);
    }

    private static void write(OutputStream output, byte[] bytes) throws IOException {
        if (bytes != null) output.write(bytes);
    }

    private static void finish(Cipher cipher, OutputStream output) throws IOException {
        try {
            write(output, cipher.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IOException("Scroll encryption/authentication failed. Check the storage key and file integrity.", e);
        }
    }

    private Cipher cipher(int mode, byte[] nonce, String storagePath, boolean createKey)
            throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key(createKey), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(MAGIC);
            // Bind ciphertext to its stored filename to reject swapped scroll files.
            cipher.updateAAD(storagePath.getBytes(StandardCharsets.UTF_8));
            return cipher;
        } catch (GeneralSecurityException e) {
            throw new IOException("Could not initialise scroll encryption.", e);
        }
    }

    void prepareMigration(Set<String> legacyPaths) throws IOException {
        key(true, legacyPaths);
    }

    private byte[] key(boolean allowCreate) throws IOException {
        return key(allowCreate, Set.of());
    }

    private byte[] key(boolean allowCreate, Set<String> legacyPaths) throws IOException {
        synchronized (KEY_LOCK) {
            Path file = dataDirectory.resolve(KEY_FILE);
            if (Files.isSymbolicLink(file)) throw new IOException("Unsafe storage key: symbolic link.");
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != 32) {
                    throw new IOException("Invalid scroll storage key; restore the original 32-byte key from backup.");
                }
                return Files.readAllBytes(file);
            }
            if (!allowCreate || containsEncryptedFiles(legacyPaths)) {
                throw new IOException("Scroll storage key is missing; restore scroll-storage.key from backup.");
            }
            Files.createDirectories(dataDirectory);
            Path temp = Files.getFileAttributeView(dataDirectory,
                    java.nio.file.attribute.PosixFileAttributeView.class) != null
                    ? Files.createTempFile(dataDirectory, "scroll-key-", ".tmp",
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                    : Files.createTempFile(dataDirectory, "scroll-key-", ".tmp");
            try {
                byte[] bytes = new byte[32];
                RANDOM.nextBytes(bytes);
                Files.write(temp, bytes);
                // Publish a completely written key; never intentionally replace an existing one.
                try {
                    Files.move(temp, file);
                } catch (FileAlreadyExistsException e) {
                    return key(false);
                }
                return bytes;
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    private boolean containsEncryptedFiles(Set<String> legacyPaths) throws IOException {
        Path directory = dataDirectory.resolve("scroll-files");
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return false;
        try (var files = Files.newDirectoryStream(directory, "*.bin")) {
            for (Path file : files) {
                if (!legacyPaths.contains("scroll-files/" + file.getFileName()) &&
                        !Files.isSymbolicLink(file) && Files.isRegularFile(file) && isEncrypted(file)) return true;
            }
        }
        return false;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }
}
