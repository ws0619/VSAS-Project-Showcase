package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.AdminAccountCreationService;
import au.edu.sydney.a2t0303.service.AdminUserService;
import au.edu.sydney.a2t0303.service.AuthenticationService;
import au.edu.sydney.a2t0303.service.PasswordHasher;
import au.edu.sydney.a2t0303.service.RegistrationService;
import au.edu.sydney.a2t0303.storage.JsonUserRepository;
import au.edu.sydney.a2t0303.storage.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class AdminCreateUserCliTest {
    @TempDir Path directory;
    private JsonUserRepository users;
    private AuthenticationService authentication;
    private final PasswordHasher hasher = new PasswordHasher(1_000);
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    @BeforeEach
    void setup() throws IOException {
        users = new JsonUserRepository(directory.resolve("users.json"));
        users.save(TestAccounts.user("A001", "admin", "Existing Admin", Role.ADMIN, "adminSecret"));
        users.save(TestAccounts.user("U001", "alice", "Existing Member", Role.MEMBER, "memberSecret"));
        authentication = new AuthenticationService(users, hasher);
        assertTrue(authentication.login("admin", "adminSecret"));
    }

    private AdminCreateUserCli cli(UserRepository repository, Scanner input) {
        return new AdminCreateUserCli(new AdminAccountCreationService(new RegistrationService(
                repository, hasher, Clock.systemUTC()), authentication), input, output);
    }

    private String details() {
        return "bob\nnewSecret\nBob Example\nbob@example.org\n0400 000-001\nU002\n";
    }

    private String text() { return bytes.toString(StandardCharsets.UTF_8); }

    @ParameterizedTest
    @CsvSource({"1,MEMBER", "2,ADMIN"})
    void adminSelectsTypeAndCredentialsNeverAppearInOutput(String choice, Role role) throws IOException {
        User created = cli(users, new Scanner(details() + choice + "\n")).create().orElseThrow();
        assertEquals(role, created.role());
        assertEquals("0400000001", created.phone());
        assertTrue(text().contains("Account created successfully."));
        assertTrue(text().contains(role == Role.ADMIN ? "Type: Admin" : "Type: User"));
        assertFalse(text().contains("newSecret"));
        assertFalse(text().contains(created.passwordHash()));
        assertFalse(text().contains(created.passwordSalt()));
        assertEquals("admin", authentication.currentUser().orElseThrow().username());
        assertEquals(3, users.findAll().size());
    }

    @Test
    void invalidFieldsAreRetriedIndividuallyAndEarlierAnswersAreKept() throws IOException {
        String input = "admin\nbob\n \nnewSecret\n\nBob Example\ninvalid\nbob@example.org\n"
                + "123\n0400 000-001\nU001\nU 2\nU002\n9\n2\n";
        User created = cli(users, new Scanner(input)).create().orElseThrow();
        assertEquals("bob", created.username());
        assertEquals("Bob Example", created.fullName());
        assertEquals(Role.ADMIN, created.role());
        assertEquals(2, count("Username: "));
        assertEquals(2, count("Password: "));
        assertEquals(2, count("Full name: "));
        assertEquals(2, count("Email: "));
        assertEquals(2, count("Phone number: "));
        assertEquals(3, count("ID key: "));
        assertTrue(text().contains("Invalid account type."));
        assertEquals(3, users.findAll().size());
    }

    private int count(String prompt) {
        String form = text().substring(text().indexOf("Username: "),
                text().indexOf("Account created successfully."));
        return form.split(java.util.regex.Pattern.quote(prompt), -1).length - 1;
    }

    @Test
    void cancellingAtTypeSelectionDoesNotSaveAnAccount() throws IOException {
        assertTrue(cli(users, new Scanner(details() + "0\n")).create().isEmpty());
        assertTrue(text().contains("Account creation cancelled."));
        assertEquals(2, users.findAll().size());
        assertEquals("admin", authentication.currentUser().orElseThrow().username());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "bob\n", "bob\nsecret\n", "bob\nsecret\nBob\n",
            "bob\nsecret\nBob\nbob@example.org\n", "bob\nsecret\nBob\nbob@example.org\n0400000000\n",
            "bob\nsecret\nBob\nbob@example.org\n0400000000\nU002\n"})
    void endOfInputAtAnyStageCancelsWithoutPartialRecords(String typed) throws IOException {
        assertTrue(cli(users, new Scanner(typed)).create().isEmpty());
        assertTrue(text().contains("Account creation cancelled."));
        assertEquals(2, users.findAll().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"anonymous", "guest", "member"})
    void directFormAccessIsDeniedBeforeAnyFieldPrompt(String mode) throws IOException {
        if (mode.equals("guest")) authentication.enterGuest();
        else if (mode.equals("member")) authentication.login("alice", "memberSecret");
        else authentication.logout();
        assertTrue(cli(users, new Scanner(details() + "2\n")).create().isEmpty());
        assertTrue(text().contains("Only administrators can create user accounts."));
        assertFalse(text().contains("=== Create User Account ==="));
        assertFalse(text().contains("Username: "));
        assertEquals(2, users.findAll().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void storageReadAndWriteFailuresAreReportedWithoutLeakingInternalDetails(boolean failOnRead)
            throws IOException {
        UserRepository failing = new UserRepository() {
            public List<User> findAll() throws IOException {
                if (failOnRead) throw new IOException("internal-secret-path");
                return users.findAll();
            }
            public void save(User user) throws IOException { throw new IOException("internal-secret-path"); }
            public void deleteById(String id) {}
        };
        Optional<User> result = cli(failing, new Scanner(details() + "1\n")).create();
        assertTrue(result.isEmpty());
        assertTrue(text().contains("user data could not be read or saved"));
        assertFalse(text().contains("internal-secret-path"));
        assertFalse(text().contains("Account created successfully."));
        assertEquals(2, users.findAll().size());
        assertEquals("admin", authentication.currentUser().orElseThrow().username());
    }

    @Test
    void submenuCreatesAnAccountThenCanListItAndReturn() throws IOException {
        var input = new Scanner("invalid\n2\n" + details() + "1\n1\n0\n");
        new AdminCli(new AdminUserService(users, authentication), cli(users, input), input, output).run();
        assertTrue(text().contains("2. Create a user account"));
        assertTrue(text().contains("Invalid option. Please choose 0, 1 or 2."));
        assertTrue(text().contains("Account created successfully."));
        assertTrue(text().contains("Total users: 3"));
        assertTrue(authentication.currentUser().orElseThrow().isAdmin());
    }

    @Test
    void originalUs12ConstructorStillRejectsUnconfiguredCreationOption() {
        var input = new Scanner("2\n0\n");
        new AdminCli(new AdminUserService(users, authentication), input, output).run();
        assertFalse(text().contains("2. Create a user account"));
        assertTrue(text().contains("Invalid option. Please choose 0 or 1."));
    }
}
