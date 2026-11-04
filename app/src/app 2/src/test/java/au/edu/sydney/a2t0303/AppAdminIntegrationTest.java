package au.edu.sydney.a2t0303;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.service.DefaultAdminService;
import au.edu.sydney.a2t0303.storage.JsonUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class AppAdminIntegrationTest {
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

    @Test
    void firstRunAdminCanViewRegisteredMembersAndRestartPreservesAccounts() throws IOException {
        String first = run("1\nalice\nmemberSecret\nAlice Example\nalice@example.org\n0412345678\nU001\n"
                + adminLogin() + "7\n1\n0\n3\n0\n");
        assertTrue(first.contains("Login successful."));
        assertTrue(first.contains("7. Administrator functions"));
        assertTrue(first.contains("=== All Users ==="));
        assertTrue(first.contains("Alice Example"));
        assertTrue(first.contains("alice@example.org"));
        assertTrue(first.contains("0412345678"));
        assertTrue(first.contains("U001"));
        assertTrue(first.contains("Total users: 2"));
        assertTrue(first.contains("You have logged out."));
        assertFalse(first.contains("memberSecret"));
        assertFalse(first.contains(DefaultAdminService.DEFAULT_PASSWORD));
        var users = new JsonUserRepository(directory.resolve("users.json"));
        for (var user : users.findAll()) {
            assertFalse(first.contains(user.passwordHash()));
            assertFalse(first.contains(user.passwordSalt()));
        }
        String saved = Files.readString(directory.resolve("users.json"));
        String second = run(adminLogin() + "7\n1\n0\n3\n0\n");
        assertTrue(second.contains("Total users: 2"));
        assertEquals(saved, Files.readString(directory.resolve("users.json")));
    }

    @Test
    void memberAndGuestCannotUseSevenAndAdminAccessDoesNotSurviveLogout() throws IOException {
        var users = new JsonUserRepository(directory.resolve("users.json"));
        users.save(TestAccounts.user("U001", "alice", "Alice Example", Role.MEMBER, "memberSecret"));
        String output = run(adminLogin() + "3\n2\nalice\nmemberSecret\n7\n3\n3\n7\n3\n0\n");
        int logout = output.indexOf("You have logged out.");
        String afterLogout = output.substring(logout);
        assertFalse(afterLogout.contains("7. Administrator functions"));
        assertTrue(afterLogout.contains("Only administrators can view all users."));
        assertTrue(afterLogout.contains("User: Guest | Type: Guest"));
        assertFalse(output.contains("=== All Users ==="));
        assertFalse(output.contains("=== Administrator Functions ==="));
        assertFalse(output.contains("admin@vsas.local"));
    }

    @Test
    void agreedMainMenuNumbersRemainStableWithProfileAndAdminFeatures()
            throws IOException {

        String output = run(
                adminLogin() + "invalid\n7\n0\n3\n0\n"
        );

        assertTrue(output.contains("1. View all scrolls"));
        assertTrue(output.contains("2. Upload a new scroll"));
        assertTrue(output.contains("3. Log out"));
        assertTrue(output.contains("4. Edit a scroll"));
        assertTrue(output.contains("5. Remove a scroll"));
        assertTrue(output.contains("6. Update my profile"));
        assertTrue(output.contains("7. Administrator functions"));
        assertTrue(output.contains("8. View my scroll version history"));

        assertTrue(output.contains(
                "Invalid option. Please choose 0, 1, 2, 3, 4, 5, 6, 7 or 8."
        ));

        assertTrue(output.contains("=== Administrator Functions ==="));
        assertTrue(output.contains("0. Exit"));
        assertTrue(output.contains("You have logged out."));
    }
    
    @Test
    void memberNamedAdminIsPreservedAndNeverGrantedAdministratorAccess() throws IOException {
        Path file = directory.resolve("users.json");
        var users = new JsonUserRepository(file);
        users.save(TestAccounts.user("U001", "admin", "Member Named Admin", Role.MEMBER, "memberSecret"));
        String before = Files.readString(file);
        String output = run("2\nadmin\nmemberSecret\n7\n3\n0\n");
        assertTrue(output.contains("The default admin account could not be initialised."));
        assertTrue(output.contains("User: Member Named Admin | Type: User"));
        assertFalse(output.contains("7. Administrator functions"));
        assertFalse(output.contains("=== All Users ==="));
        assertEquals(before, Files.readString(file));
    }
}
