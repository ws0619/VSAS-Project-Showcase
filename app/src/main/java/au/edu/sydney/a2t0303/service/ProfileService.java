package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.UserRepository;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Business rules for US07: a logged-in user updates their own details or password.
 * Every change starts from the latest stored record, so an older copy of the user
 * (for example the one kept by the login session) can never overwrite newer data.
 */
public final class ProfileService {

    private final UserRepository repository;
    private final PasswordHasher hasher;
    private final Clock clock;

    public ProfileService(UserRepository repository, PasswordHasher hasher, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Changes the user's full name, email and phone number.
     * A null value keeps the current value. The ID key, username and role never change here.
     *
     * @return the saved user (unchanged if every value matches the stored one)
     * @throws IllegalArgumentException if the user does not exist or a value is blank
     */
    public User updateDetails(String userId, String fullName, String email, String phone)
            throws IOException {

        User stored = load(userId);

        String newFullName = valueOrCurrent(fullName, stored.fullName(), "Full name");
        // A changed email or phone number must follow the same format rules as registration (US21).
        String newEmail = email == null ? stored.email()
                : AccountDetailsValidator.email(valueOrCurrent(email, stored.email(), "Email"));
        String newPhone = phone == null ? stored.phone()
                : AccountDetailsValidator.phone(valueOrCurrent(phone, stored.phone(), "Phone number"));

        if (newFullName.equals(stored.fullName())
                && newEmail.equals(stored.email())
                && newPhone.equals(stored.phone())) {
            return stored;
        }

        User updated = new User(
                stored.id(),
                stored.username(),
                newFullName,
                newEmail,
                newPhone,
                stored.role(),
                stored.passwordHash(),
                stored.passwordSalt(),
                stored.passwordAlgorithm(),
                stored.passwordIterations(),
                stored.createdAt(),
                nextUpdatedAt(stored),
                stored.failedLoginAttempts(),
                stored.locked());

        repository.update(updated);
        return updated;
    }

    /**
     * Changes the user's password after checking the current one.
     * Only a new salted hash is stored, never the password itself.
     *
     * @throws IllegalArgumentException if the user does not exist, the current password is
     *                                  wrong, or the new password is blank or unchanged
     */
    public User changePassword(String userId, String currentPassword, String newPassword)
            throws IOException {

        User stored = load(userId);

        if (currentPassword == null
                || !hasher.verify(currentPassword, stored.passwordHash(),
                        stored.passwordSalt(), stored.passwordIterations())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password is required.");
        }
        if (newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException(
                    "New password must be different from the current password.");
        }

        PasswordHasher.HashedPassword hashed = hasher.hash(newPassword);

        User updated = new User(
                stored.id(),
                stored.username(),
                stored.fullName(),
                stored.email(),
                stored.phone(),
                stored.role(),
                hashed.hash(),
                hashed.salt(),
                PasswordHasher.ALGORITHM,
                hasher.getIterations(),
                stored.createdAt(),
                nextUpdatedAt(stored),
                stored.failedLoginAttempts(),
                stored.locked());

        repository.update(updated);
        return updated;
    }

    private User load(String userId) throws IOException {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User not found.");
        }
        return repository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
    }

    private static String valueOrCurrent(String value, String current, String fieldName) {
        if (value == null) {
            return current;
        }
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }
        return stripped;
    }

    // The repository rejects an updatedAt earlier than the stored one, so never go backwards.
    private Instant nextUpdatedAt(User stored) {
        Instant now = Instant.now(clock);
        return now.isBefore(stored.updatedAt()) ? stored.updatedAt() : now;
    }
}
