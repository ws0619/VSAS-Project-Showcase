package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.ScrollRepository;
import au.edu.sydney.a2t0303.storage.UserRepository;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * US14: an administrator deletes another user's account together with every scroll that
 * user uploaded (catalog records, current files and retained versions).
 */
public final class AdminUserDeletionService {

    /** The account that would be deleted and the scrolls that would be removed with it. */
    public record DeletionPreview(User user, List<Scroll> scrolls) {
    }

    private final UserRepository users;
    private final ScrollRepository scrolls;
    private final ScrollRemoveService scrollRemover;
    private final AuthenticationService authentication;

    public AdminUserDeletionService(UserRepository users, ScrollRepository scrolls,
            ScrollRemoveService scrollRemover, AuthenticationService authentication) {
        this.users = Objects.requireNonNull(users, "users");
        this.scrolls = Objects.requireNonNull(scrolls, "scrolls");
        this.scrollRemover = Objects.requireNonNull(scrollRemover, "scrollRemover");
        this.authentication = Objects.requireNonNull(authentication, "authentication");
    }

    /** @return the logged-in administrator */
    public User requireAdmin() {
        return authentication.currentUser().filter(User::isAdmin)
                .orElseThrow(() -> new SecurityException("Only administrators can delete users."));
    }

    /**
     * Checks that the account can be deleted and lists the scrolls that would go with it.
     *
     * @throws IllegalArgumentException if the ID key is blank, unknown or the admin's own
     */
    public DeletionPreview preview(String idKey) throws IOException {
        User admin = requireAdmin();
        if (idKey == null || idKey.isBlank()) {
            throw new IllegalArgumentException("ID key is required.");
        }
        String id = idKey.strip();
        if (id.equals(admin.id())) {
            throw new IllegalArgumentException("You cannot delete your own account.");
        }
        User user = users.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No user has ID key \"" + id + "\"."));
        List<Scroll> owned = scrolls.findAll().stream()
                .filter(scroll -> scroll.uploaderId().equals(id))
                .toList();
        return new DeletionPreview(user, owned);
    }

    /**
     * Deletes the user's scrolls first and the account last, so a failure part-way through
     * never leaves scrolls whose uploader no longer exists.
     *
     * @return what was deleted
     */
    public DeletionPreview deleteUser(String idKey) throws IOException {
        DeletionPreview target = preview(idKey);
        User admin = requireAdmin();
        for (Scroll scroll : target.scrolls()) {
            scrollRemover.remove(admin, scroll.id());
        }
        users.deleteById(target.user().id());
        return target;
    }
}
