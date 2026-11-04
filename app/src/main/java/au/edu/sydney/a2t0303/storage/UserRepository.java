package au.edu.sydney.a2t0303.storage;

import au.edu.sydney.a2t0303.model.User;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Stores and looks up registered users.
 * The JSON implementation is part of US06; tests can use a simple in-memory version.
 */
public interface UserRepository {

    // Retrieve all users
    List<User> findAll() throws IOException;

    // Save a new user
    void save(User user) throws IOException;

    // Delete the user with this ID key (used by admin delete, US14)
    void deleteById(String id) throws IOException;

    /**
     * Replaces the stored user that has the same ID key (used by profile updates, US07).
     * Implementations that support updates override this; the default refuses, so
     * test repositories that never update users do not need to implement it.
     *
     * @throws IllegalArgumentException if no user has this ID key, the record is
     *                                  invalid, or the username belongs to another user
     */
    default void update(User user) throws IOException {
        throw new UnsupportedOperationException("Updating users is not supported.");
    }

    /** Persist one authentication result. Locked accounts remain locked even for a correct password. */
    default User recordLoginResult(User verifiedUser, boolean successful) throws IOException {
        User original = findById(verifiedUser.id()).orElseThrow(() -> new SecurityException("Account changed. Please log in again."));
        if (!original.locked() && !original.sameLoginCredentials(verifiedUser)) {
            throw new SecurityException("Account credentials changed. Please log in again.");
        }
        User updated = original.afterLoginAttempt(successful);
        if (!updated.equals(original)) update(updated);
        return updated;
    }

    /** Trusted storage operation; the administrator service enforces authorization. */
    default User unlockAccount(String id) throws IOException {
        User original = findById(id).orElseThrow(() -> new IllegalArgumentException("User not found."));
        if (!original.locked()) throw new IllegalArgumentException("Account is not locked.");
        User updated = original.withLoginState(0, false);
        update(updated);
        return updated;
    }

    // Find a user by their ID key
    default Optional<User> findById(String id) throws IOException {
        return findAll().stream()
                .filter(user -> user.id().equals(id))
                .findFirst();
    }

    // Find a user by their login name (used by login, US02)
    default Optional<User> findByUsername(String username) throws IOException {
        return findAll().stream()
                .filter(user -> user.username().equals(username))
                .findFirst();
    }
}
