package au.edu.sydney.a2t0303.fixtures;

import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.PasswordHasher;
import au.edu.sydney.a2t0303.storage.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Low-cost hashes and repositories for tests only; production keeps its normal work factor. */
public final class TestAccounts {
    private TestAccounts() {}

    public static User user(String id, String username, String name, Role role, String password) {
        PasswordHasher hasher = new PasswordHasher(1_000);
        var hash = hasher.hash(password);
        Instant now = Instant.parse("2026-10-14T00:00:00Z");
        return new User(id, username, name, username + "@example.org", "0400000000", role,
                hash.hash(), hash.salt(), PasswordHasher.ALGORITHM, hasher.getIterations(), now, now);
    }

    public static final class MemoryUsers implements UserRepository {
        private final List<User> users = new ArrayList<>();

        @Override
        public List<User> findAll() {
            return List.copyOf(users);
        }

        @Override
        public void save(User user) {
            users.add(user);
        }

        @Override
        public void update(User user) {
            for (int i = 0; i < users.size(); i++) {
                if (users.get(i).id().equals(user.id())) {
                    users.set(i, user);
                    return;
                }
            }
            throw new IllegalArgumentException("User not found.");
        }

        @Override
        public void deleteById(String id) {
            users.removeIf(user -> user.id().equals(id));
        }
    }
}
