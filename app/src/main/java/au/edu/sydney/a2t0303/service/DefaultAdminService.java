package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.UserRepository;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;

/** Provisions the assignment's documented default administrator without replacing accounts. */
public final class DefaultAdminService {
    public static final String DEFAULT_ID = "ADMIN001";
    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "Admin123!";

    private final UserRepository repository;
    private final PasswordHasher hasher;
    private final Clock clock;

    public DefaultAdminService(UserRepository repository, PasswordHasher hasher, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates one default account if no administrator exists, including in older member-only data.
     * Repeated startup preserves existing passwords, profiles and timestamps.
     * Conflicting member accounts are never overwritten or promoted.
     *
     * @return true only when an account was created
     */
    public boolean createIfMissing() throws IOException {
        var users = repository.findAll();
        if (users.stream().anyMatch(User::isAdmin)) {
            return false;
        }
        if (users.stream().anyMatch(user -> DEFAULT_ID.equals(user.id())
                || DEFAULT_USERNAME.equals(user.username()))) {
            throw new IOException("Default administrator username or ID is already in use.");
        }

        var password = hasher.hash(DEFAULT_PASSWORD);
        var now = clock.instant();
        repository.save(new User(DEFAULT_ID, DEFAULT_USERNAME, "Library Administrator",
                "admin@vsas.local", "0000000000", Role.ADMIN,
                password.hash(), password.salt(), PasswordHasher.ALGORITHM,
                hasher.getIterations(), now, now));
        return true;
    }
}
