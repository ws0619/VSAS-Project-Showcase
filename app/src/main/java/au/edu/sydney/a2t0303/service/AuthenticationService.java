package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.UserRepository;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/** Owns registered-user and guest sessions, including restricted-operation checks. */
public final class AuthenticationService {
    public static final class AccountLockedException extends SecurityException {
        public AccountLockedException() {
            super("Account is locked after 3 failed login attempts. Please contact an administrator to unlock it.");
        }
    }

    private final UserRepository repository;
    private final PasswordHasher hasher;
    private User currentUser;
    private boolean guest;

    public AuthenticationService(UserRepository repository, PasswordHasher hasher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
    }

    /** A failed attempt, including a storage failure, never retains an old session. */
    public boolean login(String username, String password) throws IOException {
        logout();
        if (username == null || username.isBlank() || password == null) {
            return false;
        }
        Optional<User> result = repository.findByUsername(username.strip());
        if (result.isEmpty()) {
            return false;
        }
        User user = result.get();
        if (user.locked()) throw new AccountLockedException();
        if (user.role() == null || user.fullName() == null
                || !PasswordHasher.ALGORITHM.equals(user.passwordAlgorithm())) {
            return false;
        }
        boolean correct = hasher.verify(password, user.passwordHash(), user.passwordSalt(),
                user.passwordIterations());
        User updated = repository.recordLoginResult(user, correct);
        if (updated.locked()) throw new AccountLockedException();
        if (!correct) return false;
        currentUser = updated;
        return true;
    }

    public Optional<User> currentUser() {
        return Optional.ofNullable(currentUser);
    }

    /** Entering guest mode never retains a previously authenticated identity. */
    public void enterGuest() {
        logout();
        guest = true;
    }

    public boolean isGuest() {
        return guest;
    }

    public User requireUploadPermission() {
        return requireAuthenticatedUser("upload a scroll");
    }

    /** This gate is ready for US11; it does not implement downloading. */
    public User requireDownloadPermission() {
        return requireAuthenticatedUser("download a scroll");
    }

    private User requireAuthenticatedUser(String operation) {
        if (currentUser == null) {
            throw new SecurityException("Please log in to " + operation + ".");
        }
        return currentUser;
    }

    /** Keeps the session in step after the logged-in user's own profile changes (US07). */
    public void refreshCurrentUser(User updated) {
        if (currentUser != null && updated != null && currentUser.id().equals(updated.id())) {
            currentUser = updated;
        }
    }

    public void logout() {
        currentUser = null;
        guest = false;
    }
}
