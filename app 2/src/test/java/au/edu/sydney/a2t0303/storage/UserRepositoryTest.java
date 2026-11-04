package au.edu.sydney.a2t0303.storage;

import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryTest {

    // Simple in-memory repository, so the default methods can be tested without JSON files
    private UserRepository repositoryWith(User... users) {
        List<User> stored = new ArrayList<>(List.of(users));
        return new UserRepository() {
            @Override
            public List<User> findAll() {
                return List.copyOf(stored);
            }

            @Override
            public void save(User user) {
                stored.add(user);
            }

            @Override
            public void deleteById(String id) {
                stored.removeIf(user -> user.id().equals(id));
            }
        };
    }

    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");

    private final User alice = new User("U001", "alice", "Alice Example",
            "alice@example.org", "0400000000", Role.MEMBER, "hash", "salt",
            "PBKDF2WithHmacSHA256", 1000, NOW, NOW);

    @Test
    void findByIdReturnsMatchingUser() throws Exception {
        assertEquals(alice, repositoryWith(alice).findById("U001").orElseThrow());
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() throws Exception {
        assertTrue(repositoryWith(alice).findById("U999").isEmpty());
    }

    @Test
    void findByUsernameReturnsMatchingUser() throws Exception {
        assertEquals(alice, repositoryWith(alice).findByUsername("alice").orElseThrow());
    }

    @Test
    void findByUsernameReturnsEmptyWhenMissing() throws Exception {
        assertTrue(repositoryWith(alice).findByUsername("bob").isEmpty());
    }

    @Test
    void savedUserCanBeFound() throws Exception {
        UserRepository repository = repositoryWith();
        repository.save(alice);
        assertTrue(repository.findByUsername("alice").isPresent());
    }

    @Test
    void updateIsUnsupportedUnlessOverridden() {
        UserRepository repository = repositoryWith(alice);
        assertThrows(UnsupportedOperationException.class, () -> repository.update(alice));
    }

    @Test
    void deletedUserIsGone() throws Exception {
        UserRepository repository = repositoryWith(alice);
        repository.deleteById("U001");
        assertTrue(repository.findById("U001").isEmpty());
    }
}
