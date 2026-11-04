package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.ScrollVersion;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.LocalScrollFileStorage;
import au.edu.sydney.a2t0303.storage.ScrollRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Objects;

public class ScrollEditService {

    private final ScrollRepository repository;
    private final LocalScrollFileStorage fileStorage;
    private final Clock clock;

    public ScrollEditService(
            ScrollRepository repository,
            LocalScrollFileStorage fileStorage
    ) {
        this(repository, fileStorage, Clock.systemUTC());
    }

    public ScrollEditService(
            ScrollRepository repository,
            LocalScrollFileStorage fileStorage,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.fileStorage = Objects.requireNonNull(fileStorage);
        this.clock = Objects.requireNonNull(clock);
    }
    /**
     * Validate a scroll ID and the user's edit permission
     * before requesting additional input.
     */
    public Scroll validateEditableScroll(
            User user,
            String scrollId
    ) throws IOException {

        // Check authentication
        if (user == null ||
                user.id() == null ||
                user.id().isBlank()) {
            throw new SecurityException(
                    "You must be logged in to edit a scroll.");
        }

        // Validate Scroll ID
        if (scrollId == null || scrollId.isBlank()) {
            throw new IllegalArgumentException(
                    "Scroll ID cannot be empty.");
        }

        // Check whether the scroll exists
        Scroll original = repository.findById(scrollId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Scroll does not exist."));

        // Check owner or admin permissions
        if (!user.isAdmin() &&
                !original.uploaderId().equals(user.id())) {
            throw new SecurityException(
                    "You cannot edit another user's scroll.");
        }

        return original;
    }

    /**
     * Edit an existing scroll.
     *
     * @param user current logged-in user
     * @param scrollId ID of the scroll to edit
     * @param newName new name, or null to keep the original
     * @param newFile replacement file, or null to keep the original
     * @return the updated scroll
     */
    public Scroll edit(
            User user,
            String scrollId,
            String newName,
            Path newFile
    ) throws IOException {

        // Validate ID and edit permission
        Scroll original = validateEditableScroll(user, scrollId);

        // 4. Determine the new name
        String targetName = original.name();

        if (newName != null) {
            targetName = newName.trim();

            if (targetName.isEmpty()) {
                throw new IllegalArgumentException(
                        "Scroll name cannot be empty.");
            }
        }

        boolean nameChanged =
                !targetName.equals(original.name());

        boolean fileChanged = newFile != null;

        if (!nameChanged && !fileChanged) {
            throw new IllegalArgumentException(
                    "No changes requested.");
        }

        // 5. Check name uniqueness before copying files
        if (nameChanged) {
            for (Scroll other : repository.findAll()) {
                if (!other.id().equals(original.id()) &&
                        other.name().equals(targetName)) {
                    throw new IllegalArgumentException(
                            "Scroll name already exists.");
                }
            }
        }

        // 6. Copy replacement file if provided
        LocalScrollFileStorage.StoredFile stored = null;

        if (fileChanged) {
            stored = fileStorage.store(newFile);
        }

        // 7. Create updated metadata
        Instant now = clock.instant();
        var versions = new ArrayList<>(original.versions());
        if (stored != null) {
            versions.add(new ScrollVersion(versions.size() + 1, now,
                    stored.storagePath(), stored.originalFileName(),
                    stored.sizeBytes(), stored.sha256()));
        }
        Scroll updated = new Scroll(
                original.id(),
                targetName,
                original.uploaderId(),
                original.uploadedAt(),
                now,
                stored == null
                        ? original.storagePath()
                        : stored.storagePath(),
                stored == null
                        ? original.originalFileName()
                        : stored.originalFileName(),
                stored == null
                        ? original.sizeBytes()
                        : stored.sizeBytes(),
                stored == null
                        ? original.sha256()
                        : stored.sha256(),
                original.downloadCount(),
                versions
        );

        // 8. Save metadata and roll back new file on failure
        try {
            repository.update(updated);
        } catch (IOException | RuntimeException e) {

            if (stored != null) {
                try {
                    fileStorage.delete(stored.storagePath());
                } catch (IOException cleanupError) {
                    e.addSuppressed(cleanupError);
                }
            }

            throw e;
        }

        // Earlier files remain in storage and are referenced by version history.
        return updated;
    }
}
