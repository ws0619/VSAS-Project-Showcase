package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.ScrollVersion;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.ScrollRepository;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public final class ScrollVersionService {
    private final ScrollRepository repository;

    public ScrollVersionService(ScrollRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    /** Returns versions oldest first; follows the existing edit permission rules. */
    public List<ScrollVersion> listVersions(User user, String scrollId) throws IOException {
        if (user == null || user.id() == null || user.id().isBlank()) {
            throw new SecurityException("Please log in to view scroll version history.");
        }
        if (scrollId == null || scrollId.isBlank()) {
            throw new IllegalArgumentException("Scroll ID cannot be empty.");
        }
        var scroll = repository.findById(scrollId).orElseThrow(() ->
                new IllegalArgumentException("Scroll does not exist."));
        if (!user.isAdmin() && !scroll.uploaderId().equals(user.id())) {
            throw new SecurityException("You cannot view another user's scroll version history.");
        }
        return scroll.versions();
    }
}
