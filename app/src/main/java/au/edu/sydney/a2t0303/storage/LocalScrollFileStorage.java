package au.edu.sydney.a2t0303.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Stores encrypted binaries; callers continue to use plaintext sizes and hashes. */
public class LocalScrollFileStorage {
    private final Path dataDirectory;
    private final ScrollFileEncryption encryption;

    public LocalScrollFileStorage(Path dataDirectory) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory).toAbsolutePath().normalize();
        this.encryption = new ScrollFileEncryption(this.dataDirectory);
    }

    public record StoredFile(String storagePath, String originalFileName, long sizeBytes, String sha256) {}

    public String copy(Path source) throws IOException {
        return store(source).storagePath();
    }

    public StoredFile store(Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            throw new IOException("Source file is invalid.");
        }
        Path directory = storageDirectory(true);
        String relativePath = "scroll-files/" + UUID.randomUUID() + ".bin";
        Path temp = Files.createTempFile(directory, "upload-", ".tmp");
        try {
            ScrollFileEncryption.PlaintextMetadata metadata;
            try (InputStream input = Files.newInputStream(source);
                 OutputStream output = Files.newOutputStream(temp)) {
                metadata = encryption.encrypt(input, output, relativePath);
            }
            move(temp, dataDirectory.resolve(relativePath), false);
            return new StoredFile(relativePath, source.getFileName().toString(),
                    metadata.sizeBytes(), metadata.sha256());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Bounded authenticated read for preview; no plaintext temporary file. */
    public byte[] readAllBytes(String relativePath, long maxBytes) throws IOException {
        Path file = readableFile(relativePath);
        long size = plaintextSize(file);
        if (maxBytes < 0 || size > maxBytes || size > Integer.MAX_VALUE - 8) {
            throw new IOException("File is too large to preview (maximum 5 MiB). Please download the file instead.");
        }
        var output = new ByteArrayOutputStream((int) size);
        try (InputStream input = Files.newInputStream(file)) {
            encryption.decrypt(input, output, relativePath);
        }
        return output.toByteArray();
    }

    /** Caller uses an unpublished download temporary file and publishes only on success. */
    public void writePlaintext(String relativePath, Path destination) throws IOException {
        Path file = readableFile(relativePath);
        if (Files.isSymbolicLink(destination)) throw new IOException("Unsafe download temporary file.");
        try (InputStream input = Files.newInputStream(file);
             OutputStream output = Files.newOutputStream(destination)) {
            encryption.decrypt(input, output, relativePath);
        }
    }

    public void delete(String relativePath) throws IOException {
        Path file = resolve(relativePath);
        // Preserve idempotent deletion when a data directory has not been created yet.
        if (Files.notExists(dataDirectory.resolve("scroll-files"), LinkOption.NOFOLLOW_LINKS)) return;
        storageDirectory(false);
        if (Files.isSymbolicLink(file)) throw new IOException("Unsafe scroll file: symbolic link.");
        Files.deleteIfExists(file);
    }

    List<String> storedPaths() throws IOException {
        if (Files.notExists(dataDirectory.resolve("scroll-files"), LinkOption.NOFOLLOW_LINKS)) return List.of();
        Path directory = storageDirectory(false);
        List<String> paths = new ArrayList<>();
        try (var files = Files.newDirectoryStream(directory, "*.bin")) {
            for (Path file : files) {
                String relative = "scroll-files/" + file.getFileName();
                readableFile(relative);
                paths.add(relative);
            }
        }
        return paths;
    }

    /** Explicit, resumable migration. Never guess legacy format during a normal read. */
    boolean migrateLegacyFile(String relativePath, Long expectedSize, String expectedHash) throws IOException {
        Path file = readableFile(relativePath);
        if (!isLegacyFile(relativePath, expectedSize, expectedHash)) {
            encryption.requireExistingKey();
            return false;
        }
        Path temp = Files.createTempFile(storageDirectory(false), "encrypt-", ".tmp");
        try {
            ScrollFileEncryption.PlaintextMetadata metadata;
            try (InputStream input = Files.newInputStream(file);
                 OutputStream output = Files.newOutputStream(temp)) {
                metadata = encryption.encrypt(input, output, relativePath);
            }
            // A damaged encrypted header must not be reinterpreted as a legacy scroll.
            if ((expectedSize != null && expectedSize != metadata.sizeBytes()) ||
                    (expectedHash != null && !expectedHash.equals(metadata.sha256()))) {
                throw new IOException("Legacy scroll does not match its metadata; migration stopped without replacing it.");
            }
            move(temp, file, true);
            return true;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    void prepareMigration(Set<String> legacyPaths) throws IOException {
        encryption.prepareMigration(legacyPaths);
    }

    boolean isLegacyFile(String path, Long expectedSize, String expectedHash) throws IOException {
        Path file = readableFile(path);
        if (!ScrollFileEncryption.isEncrypted(file)) return true;
        // A genuine legacy payload may begin with the envelope magic bytes.
        if (expectedSize == null || expectedHash == null || Files.size(file) != expectedSize) return false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return expectedHash.equals(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private long plaintextSize(Path file) throws IOException {
        long size = Files.size(file);
        if (!ScrollFileEncryption.isEncrypted(file) || size < ScrollFileEncryption.OVERHEAD) {
            throw new IOException("Invalid encrypted scroll file.");
        }
        return size - ScrollFileEncryption.OVERHEAD;
    }

    private Path resolve(String relativePath) throws IOException {
        if (relativePath == null || !relativePath.matches("scroll-files/[0-9a-fA-F-]{36}\\.bin")) {
            throw new IOException("Invalid storage path.");
        }
        return dataDirectory.resolve(relativePath).normalize();
    }

    private Path storageDirectory(boolean create) throws IOException {
        Path directory = dataDirectory.resolve("scroll-files");
        if (Files.isSymbolicLink(directory)) throw new IOException("Unsafe storage directory: symbolic link.");
        if (create) Files.createDirectories(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) ||
                !directory.toRealPath().equals(dataDirectory.toRealPath().resolve("scroll-files"))) {
            throw new IOException("Storage directory is unavailable or unsafe.");
        }
        return directory;
    }

    private Path readableFile(String relativePath) throws IOException {
        Path file = resolve(relativePath);
        Path directory = storageDirectory(false);
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ||
                !Files.isReadable(file) || !file.toRealPath().startsWith(directory.toRealPath())) {
            throw new IOException("Scroll file is unavailable or unsafe.");
        }
        return file;
    }

    private static void move(Path from, Path to, boolean replace) throws IOException {
        try {
            if (replace) Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            if (replace) Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(from, to);
        }
    }
}
