package au.edu.sydney.a2t0303.service;


import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.LocalScrollFileStorage;
import au.edu.sydney.a2t0303.storage.ScrollRepository;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ScrollRemoveService {

    private static final Logger LOGGER = Logger.getLogger(ScrollRemoveService.class.getName());

    private final ScrollRepository repository;
    private final LocalScrollFileStorage fileStorage;

    public ScrollRemoveService(
            ScrollRepository repository,
            LocalScrollFileStorage fileStorage) {

        this.repository = Objects.requireNonNull(repository);
        this.fileStorage = Objects.requireNonNull(fileStorage);
    }

    /**
     * Checks the scroll ID and the user's permission before asking for confirmation.
     * remove() repeats these checks.
     */
    public Scroll validateRemovableScroll(User user, String scrollId)
            throws IOException {

        // 1. Check authentication
        if (user == null ||
                user.id() == null ||
                user.id().isBlank()) {
            throw new SecurityException(
                    "Please log in to remove a scroll.");
        }

        // 2. Validate scroll ID
        if (scrollId == null || scrollId.isBlank()) {
            throw new IllegalArgumentException(
                    "Scroll ID cannot be empty.");
        }

        // 3. Find the scroll
        Scroll original = repository.findById(scrollId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Scroll does not exist."));

        // 4. Check owner or admin permissions
        if (!user.isAdmin() &&
                !original.uploaderId().equals(user.id())) {
            throw new SecurityException(
                    "You cannot remove another user's scroll.");
        }

        return original;
    }

    public Scroll remove(User user, String scrollId)
            throws IOException {

        // 1-4. Validate login, ID, existence and owner/admin permission
        Scroll original = validateRemovableScroll(user, scrollId);

        // 5. Remove metadata from the catalog
        repository.deleteById(original.id());

        // 6. Delete the stored file
        try {
            fileStorage.delete(original.storagePath());
        } catch (IOException e) {

            // Try to restore metadata if file deletion fails
            try {
                repository.save(original);
            } catch (IOException | RuntimeException rollbackError) {
                e.addSuppressed(rollbackError);
            }

            throw e;
        }

        // The scroll is removed. Clean up retained files without restoring metadata
        // that would now refer to an already deleted current file.
        for (var version : original.versions()) {
            if (!version.storagePath().equals(original.storagePath())) {
                try {
                    fileStorage.delete(version.storagePath());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Could not remove an archived scroll file.", e);
                }
            }
        }

        return original;
    }
}
