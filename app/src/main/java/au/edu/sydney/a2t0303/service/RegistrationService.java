package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.UserRepository;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Business rules for US01: registering a new account.
 */
public class RegistrationService {

    private final UserRepository repository;
    private final PasswordHasher hasher;
    private final Clock clock;

    public RegistrationService(UserRepository repository, PasswordHasher hasher, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates and saves a new MEMBER account.
     *
     * @throws IllegalArgumentException if a field is missing or badly formatted, or the username or ID key is taken
     * @throws IOException              if the user data cannot be read or saved
     */
    public User register(RegistrationRequest request) throws IOException {
        return createAccount(request, Role.MEMBER);
    }

    /**
     * Shared account creation for self-registration and the authorized US13 service.
     * Package-private: the public registration API always creates a MEMBER.
     */
    User createAccount(RegistrationRequest request, Role role) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(role, "role");

        // 1. Every field is required and must have a valid format (US21);
        //    username and ID key must also be unique. Checked in the order the screen asks.
        String username = checkUsername(request.username());
        String password = checkPassword(request.password());
        String fullName = checkFullName(request.fullName());
        String email = checkEmail(request.email());
        String phone = checkPhone(request.phone());
        String idKey = checkIdKey(request.idKey());

        // 2. Store only a salted hash of the password, never the password itself
        PasswordHasher.HashedPassword hashed = hasher.hash(password);

        // 3. Self-registration fixes MEMBER; US13 supplies its authorized account type.
        Instant now = Instant.now(clock);
        User user = new User(
                idKey,
                username,
                fullName,
                email,
                phone,
                role,
                hashed.hash(),
                hashed.salt(),
                PasswordHasher.ALGORITHM,
                hasher.getIterations(),
                now,
                now
        );

        repository.save(user);
        return user;
    }

    // ----- Single-field checks, also used by the registration screen to re-ask one field -----

    /** @return the username without surrounding spaces */
    public String checkUsername(String value) throws IOException {
        String username = AccountDetailsValidator.required(value, "Username");
        if (repository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username \"" + username + "\" is already taken.");
        }
        return username;
    }

    /** Spaces inside a password are kept exactly as typed. */
    public String checkPassword(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }
        return value;
    }

    public String checkFullName(String value) {
        return AccountDetailsValidator.required(value, "Full name");
    }

    public String checkEmail(String value) {
        return AccountDetailsValidator.email(value);
    }

    /** @return the phone number without spaces or hyphens */
    public String checkPhone(String value) {
        return AccountDetailsValidator.phone(value);
    }

    public String checkIdKey(String value) throws IOException {
        String idKey = AccountDetailsValidator.idKey(value);
        if (repository.findById(idKey).isPresent()) {
            throw new IllegalArgumentException("ID key \"" + idKey + "\" is already taken.");
        }
        return idKey;
    }
}
