
package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.storage.ScrollRepository;
import au.edu.sydney.a2t0303.storage.LocalScrollFileStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.nio.file.FileAlreadyExistsException;

public final class ScrollDownloadService {

        public static final class InvalidDestinationFolderException
            extends IOException {

        public InvalidDestinationFolderException(String message) {
            super(message);
        }
    }

    public static final class DownloadCountUpdateException
            extends IOException {

        private final Path savedPath;

        public DownloadCountUpdateException(
                Path savedPath, Throwable cause) {

            super("File saved, but download count update failed.", cause);
            this.savedPath = savedPath;
        }

        public Path savedPath() {
            return savedPath;
        }
    }


    private final ScrollRepository repository;
    private final AuthenticationService authentication;
    private final Path dataDirectory;

    public ScrollDownloadService(
            ScrollRepository repository,
            AuthenticationService authentication,
            Path dataDirectory) {

        this.repository = Objects.requireNonNull(
                repository, "repository");

        this.authentication = Objects.requireNonNull(
                authentication, "authentication");

        this.dataDirectory = Objects.requireNonNull(
                dataDirectory, "dataDirectory")
                .toAbsolutePath().normalize();
    }
    /**
     * Validate a scroll ID and download permission
     * before requesting a destination folder.
     */
    public void validateDownloadableScroll(String scrollId)
            throws IOException {

        // Check download permission first
        authentication.requireDownloadPermission();

        // Validate Scroll ID
        if (scrollId == null || scrollId.isBlank()) {
            throw new IllegalArgumentException(
                    "Scroll ID cannot be empty.");
        }

        // Check whether the scroll exists
        if (repository.findById(scrollId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Scroll not found: " + scrollId);
        }
    }
    public Path downloadToFolder(String scrollId, Path folder)
                throws IOException {

        // 1. Check download permission
        authentication.requireDownloadPermission();

        // 2. Validate Scroll ID
        if (scrollId == null || scrollId.isBlank()) {
                throw new IllegalArgumentException(
                        "Scroll ID cannot be empty.");
        }

        Objects.requireNonNull(folder, "folder");

        // 3. Find the scroll
        Scroll scroll = repository.findById(scrollId)
                .orElseThrow(() ->
                        new IOException("Scroll not found: " + scrollId));

        // 4. Get the original filename
        String filename = scroll.originalFileName();

        // Reject unsafe filenames
        if (filename == null || filename.isBlank()
                || filename.equals(".")
                || filename.equals("..")
                || filename.contains("/")
                || filename.contains("\\")
                || filename.indexOf('\0') >= 0) {

                throw new IOException("Invalid original filename.");
        }

        // 5. Validate destination folder
        Path destinationFolder =
                folder.toAbsolutePath().normalize();

        if (!Files.exists(destinationFolder)) {
                throw new InvalidDestinationFolderException(
                        "Destination folder does not exist.");
        }

        if (!Files.isDirectory(destinationFolder)) {
                throw new InvalidDestinationFolderException(
                        "Destination path is not a folder.");
        }

        // 6. Automatically use the original filename
        Path destination = destinationFolder.resolve(filename);

        // 7. Prevent overwriting an existing file
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileAlreadyExistsException(
                        destination.toString());
        }

        // 8. Reuse the existing download implementation
        download(scrollId, destination);

        // 9. Return the complete saved path
        return destination;
        }

    public void download(String scrollId, Path destination)
            throws IOException {

        // 1. Check permission before accessing any file
        authentication.requireDownloadPermission();

        if (scrollId == null || scrollId.isBlank()) {
            throw new IllegalArgumentException(
                    "Scroll ID cannot be empty.");
        }

        Objects.requireNonNull(destination, "destination");

        // 2. Find scroll metadata
        Scroll scroll = repository.findById(scrollId)
                .orElseThrow(() ->
                        new IOException("Scroll not found: " + scrollId));

        // 3. Validate stored file path
        String relativePath = scroll.storagePath();

        if (relativePath == null ||
                !relativePath.matches(
                        "scroll-files/[0-9a-fA-F-]{36}\\.bin")) {

            throw new IOException("Invalid storage path.");
        }

        Path storageDir = dataDirectory.resolve("scroll-files")
                .toAbsolutePath().normalize();

        Path source = dataDirectory.resolve(relativePath)
                .toAbsolutePath().normalize();

        // Reject symlinked storage directory
        if (Files.isSymbolicLink(storageDir)) {
            throw new IOException(
                    "Unsafe storage directory: symbolic link.");
        }

        if (!Files.isDirectory(
                storageDir, LinkOption.NOFOLLOW_LINKS)) {

            throw new IOException(
                    "Storage directory is unavailable.");
        }

        // Reject symlinked source file
        if (!source.startsWith(storageDir)
                || !Files.isRegularFile(
                        source, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(source)
                || Files.isSymbolicLink(source)) {

            throw new IOException(
                    "Scroll file is unavailable or unsafe.");
        }

        // Verify the real paths remain inside trusted storage
        Path trustedStorageDir =
                dataDirectory.toRealPath().resolve("scroll-files");

        Path actualStorageDir = storageDir.toRealPath();
        Path actualSource = source.toRealPath();

        if (!actualStorageDir.equals(trustedStorageDir)
                || !actualSource.startsWith(trustedStorageDir)) {

            throw new IOException("Unsafe storage path.");
        }

        // 4. Validate user-selected destination
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();

        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException(
                    "Destination directory does not exist.");
        }

        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(
                target.toString());
        }

        // 5. Copy the complete file to a temporary file
        Path temp = Files.createTempFile(
                parent, "vsas-download-", ".tmp");

        try {
            // Publish only after successful decryption and GCM authentication.
            new LocalScrollFileStorage(dataDirectory).writePlaintext(relativePath, temp);

            // Do not overwrite an existing destination file
            Files.move(temp, target);

        } finally {
            Files.deleteIfExists(temp);
        }

        // 6. Update count only after saving the file
        try {
            repository.incrementDownloadCount(scrollId);

        } catch (IOException | RuntimeException e) {

            throw new DownloadCountUpdateException(target, e);
        }
    }
}
