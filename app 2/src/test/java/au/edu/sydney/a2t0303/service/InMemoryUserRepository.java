package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.UserRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-only repository that keeps users in memory instead of a JSON file.
 * Replace with the JSON implementation (US06) in the real application.
 */
class InMemoryUserRepository implements UserRepository {

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
    public void deleteById(String id) {
        users.removeIf(user -> user.id().equals(id));
    }

    /** Follows the same rules as JsonUserRepository.update(), so tests behave like the real app. */
    @Override
    public void update(User user) {
        int index = -1;
        for (int i = 0; i < users.size(); i++) {
            User existing = users.get(i);
            if (existing.id().equals(user.id())) {
                index = i;
            } else if (existing.username().equals(user.username())) {
                throw new IllegalArgumentException("Username already exists.");
            }
        }
        if (index == -1) {
            throw new IllegalArgumentException("User not found.");
        }
        User stored = users.get(index);
        if (!stored.createdAt().equals(user.createdAt())) {
            throw new IllegalArgumentException("Account creation date cannot change.");
        }
        if (stored.role() != user.role()) {
            throw new IllegalArgumentException("Account role cannot be changed by an update.");
        }
        if (user.updatedAt().isBefore(stored.updatedAt())) {
            throw new IllegalArgumentException("Last-updated time cannot go backwards.");
        }
        users.set(index, user);
    }
}
