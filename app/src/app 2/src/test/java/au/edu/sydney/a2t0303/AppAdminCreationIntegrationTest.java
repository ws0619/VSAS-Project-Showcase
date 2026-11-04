package au.edu.sydney.a2t0303;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.service.DefaultAdminService;
import au.edu.sydney.a2t0303.storage.JsonUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class AppAdminCreationIntegrationTest {
    @TempDir Path directory;

    private String run(String input) throws IOException {
        var bytes = new ByteArrayOutputStream();
        App.run(directory, new Scanner(input), new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private String adminLogin() {
        return "2\n" + DefaultAdminService.DEFAULT_USERNAME + "\n"
                + DefaultAdminService.DEFAULT_PASSWORD + "\n";
    }

    private String details(String username, String password, String id) {
        return username + "\n" + password + "\n" + username + " Person\n"
                + username + "@example.org\n+61 400-000-001\n" + id + "\n";
    }

    @ParameterizedTest
    @CsvSource({"1,MEMBER", "2,ADMIN"})
    void newlyCreatedAccountCanLogInInTheSameApplicationRun(String choice, Role role)
            throws IOException {
        String input = adminLogin() + "7\n2\n" + details("bob", "bobSecret", "U002") + choice
                + "\n1\n0\n3\n2\nbob\nbobSecret\n"
                + (role == Role.ADMIN ? "7\n1\n0\n" : "7\n") + "3\n0\n";
        String output = run(input);
        assertTrue(output.contains("Account created successfully. Username: bob"));
        assertTrue(output.contains("User: bob Person | Type: " + (role == Role.ADMIN ? "Admin" : "User")));
        assertEquals(2, output.split("Login successful\\.", -1).length - 1);
        assertTrue(output.contains("Total users: 2"));
        assertTrue(output.contains("6. Update my profile"));
        assertFalse(output.contains("bobSecret"));
        assertFalse(output.contains(DefaultAdminService.DEFAULT_PASSWORD));
        if (role == Role.MEMBER) {
            int newLogin = output.indexOf("User: bob Person");
            assertFalse(output.substring(newLogin).contains("7. Administrator functions"));
            assertTrue(output.contains("Only administrators can view all users."));
        }
        var users = new JsonUserRepository(directory.resolve("users.json"));
        assertEquals(role, users.findByUsername("bob").orElseThrow().role());
        assertEquals("+61400000001", users.findByUsername("bob").orElseThrow().phone());
        String stored = Files.readString(directory.resolve("users.json"));
        assertFalse(stored.contains("bobSecret"));
        assertTrue(run("2\nbob\nbobSecret\n3\n0\n").contains("Login successful."));
        assertEquals(stored, Files.readString(directory.resolve("users.json")));
    }

    @Test
    void newlyCreatedAdminCanCreateAnotherAdminAndBothSurviveRestart() throws IOException {
        String output = run(adminLogin() + "7\n2\n" + details("firstadmin", "firstSecret", "A002")
                + "2\n0\n3\n2\nfirstadmin\nfirstSecret\n7\n2\n"
                + details("secondadmin", "secondSecret", "A003") + "2\n1\n0\n3\n0\n");
        assertEquals(2, output.split("Account created successfully\\.", -1).length - 1);
        assertTrue(output.contains("Total users: 3"));
        var users = new JsonUserRepository(directory.resolve("users.json"));
        assertEquals(3, users.findAll().stream().filter(user -> user.isAdmin()).count());
        assertTrue(run("2\nsecondadmin\nsecondSecret\n7\n1\n0\n3\n0\n")
                .contains("Total users: 3"));
        assertEquals(3, users.findAll().size());
    }

    @Test
    void cancellingCreationReturnsToAdminMenuWithoutSavingEnteredDetails() throws IOException {
        String output = run(adminLogin() + "7\n2\n" + details("cancelled", "cancelSecret", "U002")
                + "0\n1\n0\n3\n0\n");
        assertTrue(output.contains("Account creation cancelled. No account was saved."));
        assertTrue(output.contains("Total users: 1"));
        assertFalse(output.contains("Account created successfully."));
        assertEquals(1, new JsonUserRepository(directory.resolve("users.json")).findAll().size());
    }

    @Test
    void membersAndGuestsCannotOpenTheAdministratorCreationMenu() throws IOException {
        var users = new JsonUserRepository(directory.resolve("users.json"));
        users.save(TestAccounts.user("U001", "alice", "Alice", Role.MEMBER, "memberSecret"));
        String output = run("2\nalice\nmemberSecret\n7\n3\n3\n7\n3\n0\n");
        assertFalse(output.contains("2. Create a user account"));
        assertFalse(output.contains("=== Create User Account ==="));
        assertTrue(output.contains("Only administrators can view all users."));
        assertEquals(2, users.findAll().size());
    }

    @Test
    void publicRegistrationStillCreatesAGeneralUserWithoutAnAccountTypePrompt() throws IOException {
        String output = run("1\n" + details("selfuser", "selfSecret", "U002") + "0\n");
        assertTrue(output.contains("Registration successful!"));
        assertFalse(output.contains("Choose account type:"));
        assertEquals(Role.MEMBER, new JsonUserRepository(directory.resolve("users.json"))
                .findByUsername("selfuser").orElseThrow().role());
    }
}
