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
import java.time.Instant;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class GuestAccountCliTest {
    @TempDir Path directory;
    private final TestAccounts.MemoryUsers users = new TestAccounts.MemoryUsers();
    private AuthenticationService authentication;

    private String run(String typed) throws IOException {
        return run(new JsonScrollCatalog(directory.resolve("scrolls.json")), typed);
    }

    private String run(ScrollRepository scrolls, String typed) {
        var hasher = new PasswordHasher(1_000);
        authentication = new AuthenticationService(users, hasher);
        var input = new Scanner(typed);
        var bytes = new ByteArrayOutputStream();
        var output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        var registration = new RegistrationCli(new RegistrationService(users, hasher,
                Clock.systemUTC()), input, output);
        var upload = new ScrollUploadCli(new ScrollUploadService(new ScrollUploadValidator(),
                new LocalScrollFileStorage(directory), scrolls, Clock.systemUTC(), authentication), input, output);
        new AccountCli(authentication, registration, new ScrollBrowserCli(scrolls), upload,
                input, output).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void startMenuOffersGuestEntryWithoutAnyCredentialsOrRegistration() throws IOException {
        String output = run("3\n0\n");
        assertTrue(output.contains("3. Continue as guest"));
        assertTrue(output.contains("User: Guest | Type: Guest"));
        assertFalse(output.contains("Username:"));
        assertFalse(output.contains("Password:"));
        assertFalse(output.contains("Registration successful!"));
        assertTrue(users.findAll().isEmpty());
        assertFalse(authentication.isGuest());
        assertTrue(authentication.currentUser().isEmpty());
    }

    @Test
    void guestCanBrowseAnEmptyLibrary() throws IOException {
        String output = run("3\n1\n0\n");
        assertTrue(output.contains("The library is currently empty. No scrolls are available yet."));
        assertTrue(output.contains("Goodbye!"));
    }

    @Test
    void guestCanBrowseSavedScrollMetadataWithoutReadingUploadedContents() throws IOException {
        var catalog = new JsonScrollCatalog(directory.resolve("scrolls.json"));
        var now = Instant.parse("2026-10-14T00:00:00Z");
        catalog.save(new Scroll("S001", "Java Notes", "U001", now, now,
                "scroll-files/missing.bin", "notes.txt", 12L, "test-checksum"));
        String output = run(catalog, "3\n1\n0\n");
        assertTrue(output.contains("=== Available Scrolls ==="));
        assertTrue(output.contains("S001"));
        assertTrue(output.contains("Java Notes"));
        assertTrue(output.contains("U001"));
        assertTrue(output.contains("2026-10-14"));
        assertFalse(output.contains("Preview"));
        assertEquals(1, catalog.findAll().size());
        assertFalse(Files.exists(directory.resolve("scroll-files")));
    }

    @Test
    void guestUploadAndDownloadAttemptsAskForLoginWithoutOpeningFilePrompts() throws IOException {
        String output = run("3\n2\n4\n1\n0\n");
        assertTrue(output.contains("Please log in to upload a scroll."));
        assertTrue(output.contains("Please log in to download a scroll."));
        assertTrue(output.contains("The library is currently empty."));
        assertFalse(output.contains("Enter Scroll ID:"));
        assertFalse(output.contains("Enter File Path:"));
        assertFalse(output.contains("Scroll uploaded successfully!"));
        assertFalse(Files.exists(directory.resolve("scroll-files")));
        assertTrue(users.findAll().isEmpty());
    }

    @Test
    void guestReturnOpensStartMenuAndAllowsRegistrationThenLogin() throws IOException {
        String output = run("3\n3\n1\nalice\nsecret123\nAlice Example\nalice@example.org\n0400000000\nU001\n"
                + "2\nalice\nsecret123\n3\n0\n");
        int returned = output.indexOf("You have returned to the start menu.");
        assertTrue(returned >= 0);
        assertTrue(output.indexOf("=== VSAS Start Menu ===", returned) > returned);
        assertTrue(output.contains("Registration successful!"));
        assertTrue(output.contains("User: Alice Example | Type: User"));
        assertEquals(1, users.findAll().size());
        assertFalse(authentication.isGuest());
    }

    @Test
    void loggingOutThenEnteringGuestDoesNotRetainTheRegisteredIdentity() throws IOException {
        users.save(TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, "secret123"));
        String output = run("2\nalice\nsecret123\n3\n3\n2\n4\n0\n");
        int guest = output.indexOf("=== VSAS Guest Menu ===");
        assertTrue(guest >= 0);
        String guestOutput = output.substring(guest);
        assertFalse(guestOutput.contains("Alice Example"));
        assertTrue(guestOutput.contains("Please log in to upload a scroll."));
        assertTrue(guestOutput.contains("Please log in to download a scroll."));
    }

    @Test
    void guestCanReturnAndLogInToAnExistingAccount() throws IOException {
        users.save(TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, "secret123"));
        String output = run("3\n3\n2\nalice\nsecret123\n1\n3\n0\n");
        assertTrue(output.contains("Login successful."));
        assertTrue(output.contains("User: Alice Example | Type: User"));
        assertTrue(output.contains("The library is currently empty."));
        assertTrue(authentication.currentUser().isEmpty());
        assertFalse(authentication.isGuest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"3\n", "3\n1\n", "3\n3\n3\n"})
    void guestEndOfInputAndRepeatedVisitsExitCleanly(String typed) throws IOException {
        String output = run(typed);
        assertTrue(output.contains("Goodbye!"));
        assertTrue(authentication.currentUser().isEmpty());
        assertFalse(authentication.isGuest());
    }

    @Test
    void invalidGuestChoiceKeepsTheGuestMenuUsable() throws IOException {
        String output = run("3\ninvalid\n1\n3\n0\n");
        assertTrue(output.contains("Invalid option. Please choose 0, 1, 2, 3 or 4."));
        assertTrue(output.contains("The library is currently empty."));
        assertTrue(output.contains("You have returned to the start menu."));
    }

    @Test
    void invalidStartChoiceExplainsTheNewGuestOption() throws IOException {
        String output = run("invalid\n3\n0\n");
        assertTrue(output.contains("Invalid option. Please choose 0, 1, 2 or 3."));
        assertTrue(output.contains("User: Guest | Type: Guest"));
    }

    @Test
    void scrollStorageFailureDoesNotCrashGuestNavigation() {
        ScrollRepository failing = new ScrollRepository() {
            public List<Scroll> findAll() throws IOException { throw new IOException("internal failure"); }
            public void save(Scroll scroll) {}
        };
        String output = run(failing, "3\n1\n3\n0\n");
        assertTrue(output.contains("The scroll list could not be read. Please try again."));
        assertTrue(output.contains("You have returned to the start menu."));
        assertTrue(output.contains("Goodbye!"));
        assertFalse(output.contains("internal failure"));
    }
}
