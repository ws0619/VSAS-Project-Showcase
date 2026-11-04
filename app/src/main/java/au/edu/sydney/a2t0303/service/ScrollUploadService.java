package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.storage.LocalScrollFileStorage;
import au.edu.sydney.a2t0303.storage.ScrollRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ScrollUploadService {

    private final ScrollUploadValidator validator;
    private final LocalScrollFileStorage storage;
    private final ScrollRepository repository;
    private final Clock clock;
    private final AuthenticationService authentication;

    public ScrollUploadService(
            ScrollUploadValidator validator,
            LocalScrollFileStorage storage,
            ScrollRepository repository,
            Clock clock) {

        this(validator, storage, repository, clock, null);
    }

    /**
     * Bind the application upload service to its live session. The original
     * four-argument constructor remains an adapter for trusted US04 callers,
     * which are responsible for supplying an authenticated uploader ID.
     */
    public ScrollUploadService(
            ScrollUploadValidator validator,
            LocalScrollFileStorage storage,
            ScrollRepository repository,
            Clock clock,
            AuthenticationService authentication) {
        this.validator = validator;
        this.storage = storage;
        this.repository = repository;
        this.clock = clock;
        this.authentication = authentication;
    }

    /** Checks a typed scroll ID straight away, before the name is requested. */
    public void checkScrollId(String scrollId) throws IOException {
        requireUploadSession();
        validator.validateScrollId(scrollId, existingValues(Scroll::id));
    }

    /** Checks a typed scroll name straight away, before the file path is requested. */
    public void checkScrollName(String scrollName) throws IOException {
        requireUploadSession();
        validator.validateScrollName(scrollName, existingValues(Scroll::name));
    }

    /** Checks a typed file path straight away. upload() still repeats every check. */
    public void checkFilePath(String filePath) {
        validator.validateFilePath(filePath);
    }

    private void requireUploadSession() {
        if (authentication != null) {
            authentication.requireUploadPermission();
        }
    }

    private Set<String> existingValues(Function<Scroll, String> field) throws IOException {
        return repository.findAll().stream()
                .map(field)
                .collect(Collectors.toSet());
    }

    public Scroll upload(
            String scrollId,
            String scrollName,
            String filePath,
            String authenticatedUploaderId) throws IOException {

        // Check the live session before any metadata read or binary-file write.
        if (authentication != null) {
            var user = authentication.requireUploadPermission();
            if (!Objects.equals(user.id(), authenticatedUploaderId)) {
                throw new SecurityException("The uploader ID must match the logged-in user.");
            }
        }

        // 1. Verify the authenticated user
        if (authenticatedUploaderId == null ||
                authenticatedUploaderId.isBlank()) {
            throw new SecurityException(
                    "Login is required to upload a scroll.");
        }

        // 2. Retrieve existing scrolls
        List<Scroll> existing = repository.findAll();

        Set<String> existingIds = existing.stream()
                .map(Scroll::id)
                .collect(Collectors.toSet());

        Set<String> existingNames = existing.stream()
                .map(Scroll::name)
                .collect(Collectors.toSet());

        // 3. Validate ID and name
        validator.validateScrollIdentity(
                scrollId,
                scrollName,
                existingIds,
                existingNames
        );

        // 4. Validate source file
        Path source = validator.validateFilePath(filePath);

        // 5. Copy binary file and calculate metadata
        var storedFile = storage.store(source);

        // 6. Generate upload timestamps
        Instant now = Instant.now(clock);

        // 7. Create shared Scroll model
        Scroll scroll = new Scroll(
                scrollId,
                scrollName,
                authenticatedUploaderId,
                now,
                now,
                storedFile.storagePath(),
                storedFile.originalFileName(),
                storedFile.sizeBytes(),
                storedFile.sha256()
        );

        // 8. Persist scroll metadata
        try {
            repository.save(scroll);

        } catch (IOException | RuntimeException e) {

            // Remove copied file if metadata saving fails
            try {
                storage.delete(storedFile.storagePath());
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }

            throw e;
        }

        return scroll;
    }
}
