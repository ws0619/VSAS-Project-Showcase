package au.edu.sydney.a2t0303.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A registered user of the library.
 * Fields follow Section 3 of Design-Docs/storage-design.md.
 *
 * @param id                 the user's ID key; unique and cannot be changed after registration
 * @param username           the login name; unique
 * @param fullName           the user's full name
 * @param email              the user's email address
 * @param phone              the phone number, stored as text to keep leading zeros and "+"
 * @param role               ADMIN or MEMBER
 * @param passwordHash       the salted password hash (Base64), never the plain password
 * @param passwordSalt       the random salt used for the hash (Base64)
 * @param passwordAlgorithm  the hashing algorithm, e.g. PBKDF2WithHmacSHA256
 * @param passwordIterations the PBKDF2 work factor used for the hash
 * @param createdAt          when the account was created (UTC)
 * @param updatedAt          when the account was last changed (UTC)
 * @param failedLoginAttempts consecutive failed logins, from zero to three
 * @param locked             whether administrator intervention is required to log in
 */
public record User(
        String id,
        String username,
        String fullName,
        String email,
        String phone,
        Role role,
        String passwordHash,
        String passwordSalt,
        String passwordAlgorithm,
        int passwordIterations,
        Instant createdAt,
        Instant updatedAt,
        int failedLoginAttempts,
        boolean locked
) {
    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 3;

    /** Older callers and legacy JSON start unlocked with no recorded failures. */
    public User(String id, String username, String fullName, String email, String phone,
                Role role, String passwordHash, String passwordSalt, String passwordAlgorithm,
                int passwordIterations, Instant createdAt, Instant updatedAt) {
        this(id, username, fullName, email, phone, role, passwordHash, passwordSalt,
                passwordAlgorithm, passwordIterations, createdAt, updatedAt, 0, false);
    }

    public User withLoginState(int attempts, boolean accountLocked) {
        return new User(id, username, fullName, email, phone, role, passwordHash, passwordSalt,
                passwordAlgorithm, passwordIterations, createdAt, updatedAt, attempts, accountLocked);
    }

    /** Prevent a password/account change during verification from authenticating an older snapshot. */
    public boolean sameLoginCredentials(User other) {
        return other != null && Objects.equals(id, other.id) && Objects.equals(username, other.username)
                && Objects.equals(createdAt, other.createdAt) && Objects.equals(passwordHash, other.passwordHash)
                && Objects.equals(passwordSalt, other.passwordSalt)
                && Objects.equals(passwordAlgorithm, other.passwordAlgorithm)
                && passwordIterations == other.passwordIterations;
    }

    public User afterLoginAttempt(boolean successful) {
        if (locked) return this;
        int attempts = successful ? 0 : Math.min(MAX_FAILED_LOGIN_ATTEMPTS, failedLoginAttempts + 1);
        return attempts == failedLoginAttempts ? this
                : withLoginState(attempts, attempts == MAX_FAILED_LOGIN_ATTEMPTS);
    }


    /** Returns true if this user has administrator privileges. */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
