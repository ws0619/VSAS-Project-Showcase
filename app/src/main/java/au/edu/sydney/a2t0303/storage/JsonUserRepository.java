package au.edu.sydney.a2t0303.storage;

import au.edu.sydney.a2t0303.model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * JSON account storage shared by registration, authentication,
 * and persistence workflows.
 */
public class JsonUserRepository implements UserRepository {

    private final Path file;

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
            .build();

    public record UserData(int schemaVersion, List<User> users) {}

    public JsonUserRepository(Path file) throws IOException {
        this.file = Objects.requireNonNull(file, "file")
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(this.file.getParent());

        // Initialise storage only when the file does not already exist.
        if (Files.notExists(this.file)) {
            write(new UserData(1, List.of()));
        }
    }

    @Override
    public synchronized List<User> findAll() throws IOException {
        return List.copyOf(read().users());
    }

    @Override
    public synchronized void save(User user) throws IOException {
        validateUser(user);

        List<User> users = new ArrayList<>(read().users());

        // Preserve distinct error messages for duplicate identifiers.
        for (User existing : users) {
            if (existing.id().equals(user.id())) {
                throw new IllegalArgumentException(
                        "User ID already exists.");
            }

            if (existing.username().equals(user.username())) {
                throw new IllegalArgumentException(
                        "Username already exists.");
            }
        }

        users.add(user);
        write(new UserData(1, users));
    }

    @Override
    public synchronized void deleteById(String id) throws IOException {
        List<User> users = new ArrayList<>(read().users());

        boolean removed = users.removeIf(user -> user.id().equals(id));

        if (removed) {
            write(new UserData(1, users));
        }
    }

    @Override
    public synchronized void update(User user) throws IOException {
        validateUser(user);

        List<User> users = new ArrayList<>(read().users());
        int index = -1;

        for (int i = 0; i < users.size(); i++) {
            User existing = users.get(i);

            if (existing.id().equals(user.id())) {
                index = i;
            } else if (existing.username().equals(user.username())) {
                // Usernames stay unique when another user already has this one.
                throw new IllegalArgumentException("Username already exists.");
            }
        }

        if (index == -1) {
            throw new IllegalArgumentException("User not found.");
        }

        checkUpdateRules(users.get(index), user);

        users.set(index, user);
        write(new UserData(1, users));
    }

    @Override
    public synchronized User recordLoginResult(User verifiedUser, boolean successful) throws IOException {
        return changeLoginState(verifiedUser.id(), successful, verifiedUser);
    }

    @Override
    public synchronized User unlockAccount(String id) throws IOException {
        return changeLoginState(id, null, null);
    }

    // Read-modify-write under the repository monitor; ordinary profile updates cannot unlock users.
    private User changeLoginState(String id, Boolean successful, User verifiedUser) throws IOException {
        List<User> users = new ArrayList<>(read().users());
        for (int i = 0; i < users.size(); i++) {
            User original = users.get(i);
            if (!original.id().equals(id)) continue;
            User updated;
            if (successful == null) {
                if (!original.locked()) throw new IllegalArgumentException("Account is not locked.");
                updated = original.withLoginState(0, false);
            } else {
                if (!original.locked() && !original.sameLoginCredentials(verifiedUser)) {
                    throw new SecurityException("Account credentials changed. Please log in again.");
                }
                updated = original.afterLoginAttempt(successful);
            }
            if (!updated.equals(original)) {
                validateUser(updated);
                users.set(i, updated);
                write(new UserData(1, users));
            }
            return updated;
        }
        if (verifiedUser != null) throw new SecurityException("Account changed. Please log in again.");
        throw new IllegalArgumentException("User not found.");
    }

    private static void validateUser(User user) {
        if (user == null
                || !present(user.id())
                || !present(user.username())
                || !present(user.fullName())
                || !present(user.email())
                || !present(user.phone())
                || user.role() == null
                || !present(user.passwordHash())
                || !present(user.passwordSalt())
                || !present(user.passwordAlgorithm())
                || user.passwordIterations() <= 0
                || user.createdAt() == null
                || user.updatedAt() == null
                || user.failedLoginAttempts() < 0
                || user.failedLoginAttempts() > User.MAX_FAILED_LOGIN_ATTEMPTS
                || user.locked() != (user.failedLoginAttempts() == User.MAX_FAILED_LOGIN_ATTEMPTS)) {

            throw new IllegalArgumentException("Invalid user record.");
        }
    }

    /**
     * Rules for replacing a stored account. The role is fixed so a profile update can never
     * grant admin rights, and the timestamps can only move forward.
     */
    private static void checkUpdateRules(User stored, User updated) {
        if (stored.failedLoginAttempts() != updated.failedLoginAttempts() || stored.locked() != updated.locked()) {
            throw new IllegalArgumentException("Login security state cannot be changed by update().");
        }
        if (!stored.createdAt().equals(updated.createdAt())) {
            throw new IllegalArgumentException("Account creation date cannot change.");
        }
        if (stored.role() != updated.role()) {
            throw new IllegalArgumentException("Account role cannot be changed by an update.");
        }
        if (updated.updatedAt().isBefore(stored.updatedAt())) {
            throw new IllegalArgumentException("Last-updated time cannot go backwards.");
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private UserData read() throws IOException {
        UserData data;

        try {
            data = mapper.readValue(file.toFile(), UserData.class);
        } catch (JsonProcessingException e) {
            throw new IOException("Invalid user metadata JSON.", e);
        }

        if (data == null
                || data.schemaVersion() != 1
                || data.users() == null) {

            throw new IOException(
                    "Invalid or unsupported user metadata schema.");
        }

        Set<String> ids = new HashSet<>();
        Set<String> usernames = new HashSet<>();

        for (User user : data.users()) {
            try {
                validateUser(user);
            } catch (IllegalArgumentException e) {
                throw new IOException("Incomplete user metadata.", e);
            }

            if (!ids.add(user.id())
                    || !usernames.add(user.username())) {

                throw new IOException("Duplicate users in metadata.");
            }
        }

        return data;
    }

    private void write(UserData data) throws IOException {
        Path temporary = Files.createTempFile(
                file.getParent(), "users-", ".tmp");

        try {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(temporary.toFile(), data);

            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}