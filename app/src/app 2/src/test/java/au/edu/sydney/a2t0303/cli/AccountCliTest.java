package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.service.AuthenticationService;
import au.edu.sydney.a2t0303.service.PasswordHasher;
import au.edu.sydney.a2t0303.service.RegistrationService;
import au.edu.sydney.a2t0303.service.ScrollUploadService;
import au.edu.sydney.a2t0303.service.ScrollUploadValidator;
import au.edu.sydney.a2t0303.storage.JsonScrollCatalog;
import au.edu.sydney.a2t0303.storage.LocalScrollFileStorage;
import au.edu.sydney.a2t0303.storage.ScrollRepository;
import au.edu.sydney.a2t0303.storage.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class AccountCliTest {
    @TempDir Path directory;
    private final TestAccounts.MemoryUsers users = new TestAccounts.MemoryUsers();
    private AuthenticationService authentication;

    private String run(String input) throws IOException {
        return run(users, new JsonScrollCatalog(directory.resolve("scrolls.json")), input);
    }

    private String run(UserRepository repository, ScrollRepository scrolls, String typed) {
        var hasher = new PasswordHasher(1_000);
        authentication = new AuthenticationService(repository, hasher);
        var input = new Scanner(typed);
        var bytes = new ByteArrayOutputStream();
        var output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        var registration = new RegistrationCli(new RegistrationService(repository, hasher,
                Clock.systemUTC()), input, output);
        var upload = new ScrollUploadCli(new ScrollUploadService(new ScrollUploadValidator(),
                new LocalScrollFileStorage(directory), scrolls, Clock.systemUTC()), input, output);
        new AccountCli(authentication, registration, new ScrollBrowserCli(scrolls), upload,
                input, output).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private void addMember() {
        users.save(TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, "secret123"));
    }

    @Test
    void loginDisplaysMemberIdentityAndLogoutReturnsToStartMenu() throws IOException {
        addMember();
        String result = run("2\nalice\nsecret123\n3\n0\n");
        assertTrue(result.contains("Login successful."));
        assertTrue(result.contains("User: Alice Example | Type: User"));
        int logout = result.indexOf("You have logged out.");
        assertTrue(logout >= 0);
        assertTrue(result.indexOf("=== VSAS Start Menu ===", logout) > logout);
        assertTrue(authentication.currentUser().isEmpty());
        assertFalse(result.contains("secret123"));
    }

    @Test
    void administratorIdentityUsesAdminDisplayLabel() throws IOException {
        users.save(TestAccounts.user("A001", "admin", "Library Admin", Role.ADMIN, "admin123"));
        String result = run("2\nadmin\nadmin123\n0\n");
        assertTrue(result.contains("User: Library Admin | Type: Admin"));
        assertTrue(authentication.currentUser().isEmpty());
    }

    @Test
    void invalidCredentialsShowSameErrorAndNeverOpenMainMenu() throws IOException {
        addMember();
        String result = run("2\nalice\nwrong\n2\nunknown\nsecret123\n0\n");
        assertEquals(2, result.split("Login failed: incorrect username or password\\.", -1).length - 1);
        assertFalse(result.contains("=== VSAS Main Menu ==="));
        assertTrue(authentication.currentUser().isEmpty());
    }

    @Test
    void afterLogoutAuthenticatedOptionIsNotStillAccessible() throws IOException {
        addMember();
        String result = run("2\nalice\nsecret123\n3\n3\n0\n");
        assertTrue(result.contains("User: Guest | Type: Guest"));
        assertFalse(result.contains("Enter Scroll ID:"));
        assertTrue(authentication.currentUser().isEmpty());
        assertEquals(1, result.split("=== VSAS Main Menu ===", -1).length - 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2\n", "2\nalice\n"})
    void interruptedLoginCancelsCleanly(String input) throws IOException {
        addMember();
        String result = run(input);
        assertTrue(result.contains("Login cancelled."));
        assertTrue(result.contains("Goodbye!"));
        assertTrue(authentication.currentUser().isEmpty());
    }

    @Test
    void endOfInputClearsAuthenticatedSession() throws IOException {
        addMember();
        String result = run("2\nalice\nsecret123\n");
        assertTrue(result.contains("Login successful."));
        assertTrue(result.contains("Goodbye!"));
        assertTrue(authentication.currentUser().isEmpty());
    }

    @Test
    void invalidAuthenticatedOptionDoesNotLogOutAndBrowsingWorks() throws IOException {
        addMember();
        String result = run("2\nalice\nsecret123\ninvalid\n1\n3\n0\n");
        assertTrue(result.contains("Invalid option. Please choose 0, 1, 2 or 3."));
        assertTrue(result.contains("The library is currently empty."));
    }

    @Test
    void loginStorageErrorShowsFriendlyMessageWithoutStackTrace() throws IOException {
        UserRepository failing = new UserRepository() {
            public List<au.edu.sydney.a2t0303.model.User> findAll() throws IOException {
                throw new IOException("internal failure");
            }
            public void save(au.edu.sydney.a2t0303.model.User user) {}
            public void deleteById(String id) {}
        };
        String result = run(failing, new JsonScrollCatalog(directory.resolve("scrolls.json")),
                "2\nalice\nsecret123\n0\n");
        assertTrue(result.contains("user data could not be read"));
        assertFalse(result.contains("internal failure"));
        assertFalse(result.contains("Exception"));
        assertTrue(authentication.currentUser().isEmpty());
    }

    @Test
    void browserReadFailureDoesNotCrashAuthenticatedMenu() {
        addMember();
        ScrollRepository failing = new ScrollRepository() {
            public List<Scroll> findAll() throws IOException { throw new IOException("unavailable"); }
            public void save(Scroll scroll) {}
        };
        String result = run(users, failing, "2\nalice\nsecret123\n1\n3\n0\n");
        assertTrue(result.contains("The scroll list could not be read."));
        assertTrue(result.contains("You have logged out."));
    }

    @Test
    void uploadUsesAuthenticatedIdentityRatherThanRequestingUploaderId() throws IOException {
        addMember();
        Path source = directory.resolve("notes.txt");
        Files.writeString(source, "Example scroll");
        String result = run("2\nalice\nsecret123\n2\nS001\nNotes\n" + source + "\n3\n0\n");
        assertTrue(result.contains("Scroll uploaded successfully!"));
        var catalog = new JsonScrollCatalog(directory.resolve("scrolls.json"));
        assertEquals("U001", catalog.findAll().get(0).uploaderId());
    }

    @Test
    void registrationFromStartMenuCanBeUsedForImmediateLogin() throws IOException {
        String result = run("1\nalice\nsecret123\nAlice Example\nalice@example.org\n0400000000\nU001\n"
                + "2\nalice\nsecret123\n3\n0\n");
        assertTrue(result.contains("Registration successful!"));
        assertTrue(result.contains("Login successful."));
        assertTrue(result.contains("User: Alice Example | Type: User"));
    }
}
