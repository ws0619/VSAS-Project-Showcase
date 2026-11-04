package au.edu.sydney.a2t0303.storage;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.AuthenticationService;
import au.edu.sydney.a2t0303.service.PasswordHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JsonUserRepositoryTest {

    @TempDir
    Path directory;

    private User member() {
        return TestAccounts.user(
                "U001",
                "alice",
                "Alice Example",
                Role.MEMBER,
                "secret123");
    }

    @Test
    void createsDataFolderOnFirstRun() throws IOException {
        Path file = directory.resolve("data/users.json");

        assertFalse(Files.exists(file.getParent()));

        var repository = new JsonUserRepository(file);

        assertTrue(Files.isDirectory(file.getParent()));
        assertTrue(Files.isRegularFile(file));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void userSurvivesRestart() throws IOException {
        Path file = directory.resolve("data/users.json");
        var firstRun = new JsonUserRepository(file);

        User original = member();
        firstRun.save(original);

        // Recreate the repository to simulate an application restart.
        var secondRun = new JsonUserRepository(file);

        assertEquals(1, secondRun.findAll().size());
        assertEquals(original, secondRun.findAll().get(0));
    }

    @Test
    void restartDoesNotOverwriteUsers() throws IOException {
        Path file = directory.resolve("data/users.json");
        var firstRun = new JsonUserRepository(file);

        firstRun.save(member());

        new JsonUserRepository(file);

        var thirdRun = new JsonUserRepository(file);

        assertEquals(1, thirdRun.findAll().size());
        assertEquals("alice", thirdRun.findAll().get(0).username());
    }

    @Test
    void findUserByIdAndUsername() throws IOException {
        var repository = new JsonUserRepository(
                directory.resolve("data/users.json"));

        repository.save(member());

        assertTrue(repository.findById("U001").isPresent());
        assertTrue(repository.findByUsername("alice").isPresent());

        assertTrue(repository.findById("U999").isEmpty());
        assertTrue(repository.findByUsername("unknown").isEmpty());
    }

    @Test
    void duplicateUserIsRejected() throws IOException {
        var repository = new JsonUserRepository(
                directory.resolve("data/users.json"));

        User user = member();
        repository.save(user);

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save(user));

        assertEquals(1, repository.findAll().size());
    }

    @Test
    void deleteUserPersistsAfterRestart() throws IOException {
        Path file = directory.resolve("data/users.json");
        var repository = new JsonUserRepository(file);

        repository.save(member());
        repository.deleteById("U001");

        var restarted = new JsonUserRepository(file);

        assertTrue(restarted.findAll().isEmpty());
    }

    @Test
    void corruptedJsonIsNotSilentlyReset() throws IOException {
        Path file = directory.resolve("data/users.json");

        Files.createDirectories(file.getParent());
        Files.writeString(file, "{invalid json");

        var repository = new JsonUserRepository(file);

        assertThrows(IOException.class, repository::findAll);
        assertEquals("{invalid json", Files.readString(file));
    }

    @Test
    void createsStorageAndPersistsOnlySaltedPasswordDataAcrossRestart()
            throws IOException {

        Path file = directory.resolve("data/users.json");
        var repository = new JsonUserRepository(file);

        assertTrue(repository.findAll().isEmpty());

        User user = member();
        repository.save(user);

        String json = Files.readString(file);

        assertFalse(json.contains("secret123"));
        assertTrue(json.contains(user.passwordHash()));
        assertTrue(json.contains(user.passwordSalt()));

        assertFalse(
                json.contains("\"admin\""),
                "Derived getters must not alter the JSON schema");

        var restarted = new JsonUserRepository(file);

        assertEquals(
                user,
                restarted.findByUsername("alice").orElseThrow());

        // Authentication must use the hashing parameters saved with the user.
        var authentication = new AuthenticationService(
                restarted,
                new PasswordHasher(2_000));

        assertTrue(authentication.login("alice", "secret123"));
    }

    @Test
    void duplicateUsernameOrIdIsRejectedWithoutChangingSavedAccount()
            throws IOException {

        var repository = new JsonUserRepository(
                directory.resolve("users.json"));

        repository.save(member());

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save(TestAccounts.user(
                        "U002",
                        "alice",
                        "Another Alice",
                        Role.MEMBER,
                        "different")));

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save(TestAccounts.user(
                        "U001",
                        "bob",
                        "Bob Example",
                        Role.MEMBER,
                        "different")));

        assertEquals(1, repository.findAll().size());

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save(null));
    }

    @Test
    void deletingAnAccountPreventsLoginAfterRestart() throws IOException {
        Path file = directory.resolve("users.json");
        var repository = new JsonUserRepository(file);

        repository.save(member());

        repository.deleteById("missing");
        assertEquals(1, repository.findAll().size());

        repository.deleteById("U001");

        var restarted = new JsonUserRepository(file);
        var authentication = new AuthenticationService(
                restarted,
                new PasswordHasher(1_000));

        assertFalse(authentication.login("alice", "secret123"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{broken",
            "null",
            "{\"schemaVersion\":99,\"users\":[]}",
            "{\"schemaVersion\":1,\"users\":null}",
            "{\"schemaVersion\":1,\"users\":[null]}"
    })
    void damagedStorageIsRejectedAndNeverSilentlyOverwritten(String json)
            throws IOException {

        Path file = directory.resolve("users.json");
        Files.writeString(file, json);

        var repository = new JsonUserRepository(file);

        assertThrows(IOException.class, repository::findAll);

        assertThrows(
                IOException.class,
                () -> repository.save(member()));

        assertEquals(json, Files.readString(file));
    }

    @Test
    void incompleteOrDuplicateRecordsInTheFileAreRejected()
            throws IOException {

        Path file = directory.resolve("users.json");
        var repository = new JsonUserRepository(file);

        repository.save(member());

        String valid = Files.readString(file);
        ObjectMapper mapper = new ObjectMapper();

        String[] requiredFields = {
                "id",
                "username",
                "fullName",
                "email",
                "phone",
                "role",
                "passwordHash",
                "passwordSalt",
                "passwordAlgorithm",
                "createdAt",
                "updatedAt"
        };

        for (String field : requiredFields) {
            ObjectNode root = (ObjectNode) mapper.readTree(valid);
            ObjectNode user = (ObjectNode) root.withArray("users").get(0);

            user.putNull(field);
            Files.writeString(file, root.toString());

            assertThrows(IOException.class, repository::findAll, field);
        }

        // Reject an invalid password work factor.
        ObjectNode root = (ObjectNode) mapper.readTree(valid);
        ObjectNode user = (ObjectNode) root.withArray("users").get(0);

        user.put("passwordIterations", 0);
        Files.writeString(file, root.toString());

        assertThrows(IOException.class, repository::findAll);

        // Reject duplicate records already present in stored JSON.
        root = (ObjectNode) mapper.readTree(valid);
        root.withArray("users").add(
                root.withArray("users").get(0).deepCopy());

        Files.writeString(file, root.toString());

        assertThrows(IOException.class, repository::findAll);
    }

    // ----- update (US07) -----

    private static User withDetails(User user, String username, String fullName, String email) {
        return new User(user.id(), username, fullName, email, user.phone(), user.role(),
                user.passwordHash(), user.passwordSalt(), user.passwordAlgorithm(),
                user.passwordIterations(), user.createdAt(),
                user.updatedAt().plusSeconds(60));
    }

    @Test
    void updatedUserSurvivesRestart() throws IOException {
        Path file = directory.resolve("users.json");
        var repository = new JsonUserRepository(file);
        User original = member();
        repository.save(original);

        User changed = withDetails(original, "alice", "Alice Updated", "new@example.org");
        repository.update(changed);

        var restarted = new JsonUserRepository(file);
        assertEquals(1, restarted.findAll().size());
        assertEquals(changed, restarted.findById("U001").orElseThrow());
    }

    @Test
    void updatingUnknownUserIsRejectedWithoutChangingFile() throws IOException {
        Path file = directory.resolve("users.json");
        var repository = new JsonUserRepository(file);
        repository.save(member());
        String before = Files.readString(file);

        User stranger = TestAccounts.user("U999", "zoe", "Zoe", Role.MEMBER, "secret123");
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> repository.update(stranger));

        assertEquals("User not found.", e.getMessage());
        assertEquals(before, Files.readString(file));
    }

    @Test
    void updatingToAnotherUsersUsernameIsRejected() throws IOException {
        Path file = directory.resolve("users.json");
        var repository = new JsonUserRepository(file);
        repository.save(member());
        User bob = TestAccounts.user("U002", "bob", "Bob Example", Role.MEMBER, "secret123");
        repository.save(bob);
        String before = Files.readString(file);

        User clash = withDetails(bob, "alice", bob.fullName(), bob.email());
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> repository.update(clash));

        assertEquals("Username already exists.", e.getMessage());
        assertEquals(before, Files.readString(file));
    }

    @Test
    void incompleteUpdateIsRejectedWithoutChangingFile() throws IOException {
        Path file = directory.resolve("users.json");
        var repository = new JsonUserRepository(file);
        User original = member();
        repository.save(original);
        String before = Files.readString(file);

        User blankName = withDetails(original, "alice", "   ", original.email());
        assertThrows(IllegalArgumentException.class, () -> repository.update(blankName));
        assertThrows(IllegalArgumentException.class, () -> repository.update(null));

        assertEquals(before, Files.readString(file));
    }

    @Test
    void creationDateCannotBeChangedByUpdate() throws IOException {
        Path file = directory.resolve("users.json");
        var repository = new JsonUserRepository(file);
        User original = member();
        repository.save(original);

        User rewritten = new User(original.id(), original.username(), original.fullName(),
                original.email(), original.phone(), original.role(), original.passwordHash(),
                original.passwordSalt(), original.passwordAlgorithm(),
                original.passwordIterations(), original.createdAt().minusSeconds(3600),
                original.updatedAt());

        assertThrows(IllegalArgumentException.class, () -> repository.update(rewritten));
        assertEquals(original, repository.findById("U001").orElseThrow());
    }

    private static User withRoleAndTimes(User user, Role role, java.time.Instant updatedAt) {
        return new User(user.id(), user.username(), user.fullName(), user.email(), user.phone(),
                role, user.passwordHash(), user.passwordSalt(), user.passwordAlgorithm(),
                user.passwordIterations(), user.createdAt(), updatedAt);
    }

    @Test
    void passwordChangeSurvivesRestartAndOldPasswordStopsWorking() throws IOException {
        Path file = directory.resolve("users.json");
        var repository = new JsonUserRepository(file);
        User original = member();
        repository.save(original);

        PasswordHasher hasher = new PasswordHasher(1_000);
        var newPassword = hasher.hash("newSecret456");
        User changed = new User(original.id(), original.username(), original.fullName(),
                original.email(), original.phone(), original.role(), newPassword.hash(),
                newPassword.salt(), PasswordHasher.ALGORITHM, hasher.getIterations(),
                original.createdAt(), original.updatedAt().plusSeconds(60));
        repository.update(changed);

        var restarted = new JsonUserRepository(file);
        assertTrue(new AuthenticationService(restarted, hasher).login("alice", "newSecret456"));
        assertFalse(new AuthenticationService(restarted, hasher).login("alice", "secret123"));
    }

    @Test
    void changedUsernameIsFoundUnderTheNewNameOnly() throws IOException {
        Path file = directory.resolve("users.json");
        var repository = new JsonUserRepository(file);
        User original = member();
        repository.save(original);

        repository.update(withDetails(original, "alicia", original.fullName(), original.email()));

        var restarted = new JsonUserRepository(file);
        assertEquals("U001", restarted.findByUsername("alicia").orElseThrow().id());
        assertTrue(restarted.findByUsername("alice").isEmpty());
    }

    @Test
    void repeatedUpdatesKeepTheLatestValues() throws IOException {
        Path file = directory.resolve("users.json");
        var repository = new JsonUserRepository(file);
        User original = member();
        repository.save(original);

        User first = withDetails(original, "alice", "First Name", original.email());
        repository.update(first);
        User second = withDetails(first, "alice", "Second Name", "second@example.org");
        repository.update(second);

        var restarted = new JsonUserRepository(file);
        assertEquals(1, restarted.findAll().size());
        assertEquals(second, restarted.findById("U001").orElseThrow());
    }

    @Test
    void roleCannotBeChangedByUpdate() throws IOException {
        Path file = directory.resolve("users.json");
        var repository = new JsonUserRepository(file);
        User original = member();
        repository.save(original);
        String before = Files.readString(file);

        User promoted = withRoleAndTimes(original, Role.ADMIN, original.updatedAt().plusSeconds(60));

        assertThrows(IllegalArgumentException.class, () -> repository.update(promoted));
        assertEquals(before, Files.readString(file));
    }

    @Test
    void lastUpdatedTimeCannotGoBackwards() throws IOException {
        Path file = directory.resolve("users.json");
        var repository = new JsonUserRepository(file);
        User original = member();
        repository.save(original);
        String before = Files.readString(file);

        User older = withRoleAndTimes(original, original.role(), original.updatedAt().minusSeconds(60));

        assertThrows(IllegalArgumentException.class, () -> repository.update(older));
        assertEquals(before, Files.readString(file));
    }
}
