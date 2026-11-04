package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.UserRepository;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** US12: administrator-only access to public account profile fields. */
public final class AdminUserService {
    /** Passwords, hashes, salts and hashing metadata are deliberately absent. */
    public record UserProfile(String username, String fullName, String email,
                              String phone, String idKey, String type) {}

    private final UserRepository repository;
    private final AuthenticationService authentication;

    public AdminUserService(UserRepository repository, AuthenticationService authentication) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authentication = Objects.requireNonNull(authentication, "authentication");
    }

    public void requireAdmin() {
        authentication.currentUser().filter(User::isAdmin)
                .orElseThrow(() -> new SecurityException(
                        "Only administrators can view all users."));
    }

    /** Checks the current session before reading any profiles; returns only displayable data. */
    public List<UserProfile> listUsers() throws IOException {
        requireAdmin();
        return repository.findAll().stream()
                .sorted(Comparator.comparing(User::username))
                .map(user -> new UserProfile(user.username(), user.fullName(), user.email(),
                        user.phone(), user.id(), user.isAdmin() ? "Admin" : "User"))
                .toList();
    }
}
