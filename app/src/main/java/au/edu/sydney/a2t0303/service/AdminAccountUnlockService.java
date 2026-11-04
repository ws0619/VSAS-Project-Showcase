package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.UserRepository;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** US20: only a current, unlocked administrator may reset a locked account. */
public final class AdminAccountUnlockService {
    public record LockedAccount(String id, String username, int failedAttempts) {}
    private final UserRepository users;
    private final AuthenticationService authentication;

    public AdminAccountUnlockService(UserRepository users, AuthenticationService authentication) {
        this.users = Objects.requireNonNull(users);
        this.authentication = Objects.requireNonNull(authentication);
    }

    private void requireAdmin() throws IOException {
        User session = authentication.currentUser().filter(User::isAdmin)
                .orElseThrow(() -> new SecurityException("Only administrators can unlock accounts."));
        users.findById(session.id()).filter(user -> user.isAdmin() && !user.locked())
                .orElseThrow(() -> new SecurityException("Only current, unlocked administrators can unlock accounts."));
    }

    public List<LockedAccount> listLockedAccounts() throws IOException {
        requireAdmin();
        return users.findAll().stream().filter(User::locked)
                .sorted(Comparator.comparing(User::username))
                .map(user -> new LockedAccount(user.id(), user.username(), user.failedLoginAttempts()))
                .toList();
    }

    public User unlock(String userId) throws IOException {
        requireAdmin();
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("User ID cannot be empty.");
        return users.unlockAccount(userId.strip());
    }
}
