package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.service.AuthenticationService;
import au.edu.sydney.a2t0303.service.PasswordHasher;
import au.edu.sydney.a2t0303.service.ProfileService;
import au.edu.sydney.a2t0303.service.RegistrationService;
import au.edu.sydney.a2t0303.service.ScrollUploadService;
import au.edu.sydney.a2t0303.service.ScrollUploadValidator;
import au.edu.sydney.a2t0303.storage.JsonScrollCatalog;
import au.edu.sydney.a2t0303.storage.JsonUserRepository;
import au.edu.sydney.a2t0303.storage.LocalScrollFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class ProfileCliTest {

    @TempDir
    Path directory;

    private final PasswordHasher hasher = new PasswordHasher(1_000);
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private JsonUserRepository users;
    private AuthenticationService authentication;

    @BeforeEach
    void logInAlice() throws IOException {
        users = new JsonUserRepository(directory.resolve("users.json"));
        users.save(TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, "secret123"));
        authentication = new AuthenticationService(users, hasher);
        assertTrue(authentication.login("alice", "secret123"));
    }

    private String open(String typed) {
        var output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        var cli = new ProfileCli(new ProfileService(users, hasher, Clock.systemUTC()),
                authentication, new Scanner(typed), output);
        cli.open(authentication.currentUser().orElseThrow());
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private boolean canLogIn(String password) throws IOException {
        return new AuthenticationService(users, hasher).login("alice", password);
    }

    @Test
    void profileShowsTheIdKeyAsReadOnly() {
        String result = open("0\n");

        assertTrue(result.contains("ID key: U001 (cannot be changed)"));
        assertTrue(result.contains("Full name: Alice Example"));
    }

    @Test
    void editingDetailsUpdatesStorageAndTheSession() throws IOException {
        String result = open("1\nAlice Updated\n\n0411111111\n");

        assertTrue(result.contains("Profile updated successfully."));
        var stored = users.findById("U001").orElseThrow();
        assertEquals("Alice Updated", stored.fullName());
        assertEquals("alice@example.org", stored.email());
        assertEquals("0411111111", stored.phone());
        assertEquals("Alice Updated", authentication.currentUser().orElseThrow().fullName());
    }

    @Test
    void leavingEveryFieldBlankMakesNoChanges() throws IOException {
        String result = open("1\n\n\n\n");

        assertTrue(result.contains("No changes made."));
        assertEquals("Alice Example", users.findById("U001").orElseThrow().fullName());
    }

    @Test
    void wrongCurrentPasswordIsReported() throws IOException {
        String result = open("2\nwrong\nnewSecret456\nnewSecret456\n");

        assertTrue(result.contains("Password change failed: Current password is incorrect."));
        assertTrue(canLogIn("secret123"));
    }

    @Test
    void mismatchedConfirmationKeepsTheOldPassword() throws IOException {
        String result = open("2\nsecret123\nnewSecret456\nsomethingElse\n");

        assertTrue(result.contains("the new passwords do not match"));
        assertTrue(canLogIn("secret123"));
    }

    @Test
    void changedPasswordWorksForTheNextLogin() throws IOException {
        String result = open("2\nsecret123\nnewSecret456\nnewSecret456\n");

        assertTrue(result.contains("Password changed successfully."));
        assertTrue(canLogIn("newSecret456"));
        assertFalse(canLogIn("secret123"));
    }

    @Test
    void endOfInputCancelsWithoutChanges() throws IOException {
        String result = open("1\nAlice Updated\n");

        assertTrue(result.contains("Profile update cancelled."));
        assertEquals("Alice Example", users.findById("U001").orElseThrow().fullName());
    }

    @Test
    void invalidProfileOptionIsExplained() {
        assertTrue(open("9\n").contains("Invalid option. Please choose 0, 1 or 2."));
    }

    @Test
    void mainMenuOffersProfileAsOptionSix() throws IOException {
        authentication.logout();
        var typed = new Scanner("2\nalice\nsecret123\n9\n6\n0\n0\n");
        var output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        var scrolls = new JsonScrollCatalog(directory.resolve("scrolls.json"));
        var registration = new RegistrationCli(
                new RegistrationService(users, hasher, Clock.systemUTC()), typed, output);
        var upload = new ScrollUploadCli(new ScrollUploadService(new ScrollUploadValidator(),
                new LocalScrollFileStorage(directory), scrolls, Clock.systemUTC()), typed, output);
        var profile = new ProfileCli(new ProfileService(users, hasher, Clock.systemUTC()),
                authentication, typed, output);

        new AccountCli(authentication, registration, new ScrollBrowserCli(scrolls), upload,
                null, null, null, null, profile, typed, output).run();

        String result = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("6. Update my profile"));
        assertTrue(result.contains("Invalid option. Please choose 0, 1, 2, 3 or 6."));
        assertTrue(result.contains("===== My Profile ====="));
    }
}
