package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.AdminUserService;
import au.edu.sydney.a2t0303.service.AuthenticationService;
import au.edu.sydney.a2t0303.service.PasswordHasher;
import au.edu.sydney.a2t0303.storage.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class AdminCliTest {
    private final TestAccounts.MemoryUsers users = new TestAccounts.MemoryUsers();
    private final AuthenticationService authentication =
            new AuthenticationService(users, new PasswordHasher(1_000));
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    @BeforeEach
    void seedAccounts() {
        users.save(TestAccounts.user("A001", "admin", "Library Admin", Role.ADMIN, "adminSecret"));
        users.save(TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, "memberSecret"));
    }

    private AdminCli cli(UserRepository repository, String input) {
        return new AdminCli(new AdminUserService(repository, authentication), new Scanner(input), output);
    }

    private String result() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void adminCanListUsersAndReturnWithoutLoggingOutOrLeakingCredentials() throws IOException {
        authentication.login("admin", "adminSecret");
        cli(users, "1\n0\n").run();
        String result = result();
        for (String required : List.of("=== All Users ===", "Username", "Full name", "Email",
                "Phone", "ID key", "Type", "admin", "Library Admin", "admin@example.org",
                "A001", "Admin", "alice", "Alice Example", "alice@example.org",
                "0400000000", "U001", "User", "Total users: 2")) {
            assertTrue(result.contains(required), required);
        }
        for (var user : users.findAll()) {
            assertFalse(result.contains(user.passwordHash()));
            assertFalse(result.contains(user.passwordSalt()));
        }
        assertFalse(result.contains("adminSecret"));
        assertFalse(result.contains("memberSecret"));
        assertFalse(result.contains("passwordHash"));
        assertFalse(result.contains(PasswordHasher.ALGORITHM));
        assertTrue(authentication.currentUser().orElseThrow().isAdmin());
    }

    @Test
    void directScreenAndSubmenuCallsRejectNonAdminsBeforeDisplayingProfiles() throws IOException {
        var cli = cli(users, "1\n0\n");
        cli.run();
        authentication.enterGuest();
        cli.run();
        authentication.login("alice", "memberSecret");
        cli.run();
        cli.showAllUsers();
        assertTrue(result().contains("Only administrators can view all users."));
        assertFalse(result().contains("=== Administrator Functions ==="));
        assertFalse(result().contains("=== All Users ==="));
        assertFalse(result().contains("admin@example.org"));
    }

    @Test
    void badInputAndEndOfInputReturnCleanly() throws IOException {
        authentication.login("admin", "adminSecret");
        cli(users, "bad\n\n1\n").run();
        assertTrue(result().contains("Invalid option. Please choose 0 or 1."));
        assertTrue(result().contains("Total users: 2"));
        assertTrue(authentication.currentUser().isPresent());
    }

    @Test
    void storageFailureDoesNotLeakInternalDetailsAndMenuStillAllowsReturn() throws IOException {
        authentication.login("admin", "adminSecret");
        UserRepository failing = new UserRepository() {
            public List<User> findAll() throws IOException { throw new IOException("secret-disk-path"); }
            public void save(User user) {}
            public void deleteById(String id) {}
        };
        cli(failing, "1\n0\n").run();
        assertTrue(result().contains("The user list could not be read. Please try again."));
        assertFalse(result().contains("secret-disk-path"));
        assertFalse(result().contains("=== All Users ==="));
        assertFalse(result().contains("Exception"));
        assertTrue(authentication.currentUser().isPresent());
    }
}
