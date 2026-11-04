package au.edu.sydney.a2t0303;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
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

class AppAuthenticationTest {
    @TempDir Path directory;

    private String run(String typed) throws IOException {
        var bytes = new ByteArrayOutputStream();
        App.run(directory, new Scanner(typed), new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void realEntryWiringSupportsRegistrationLogoutAndLoginAfterRestart() throws IOException {
        String first = run("1\nalice\nsecret123\nAlice Example\nalice@example.org\n0400000000\nU001\n"
                + "2\nalice\nsecret123\n3\n0\n");
        assertTrue(first.contains("Registration successful!"));
        assertTrue(first.contains("You have logged out."));
        String second = run("2\nalice\nsecret123\n1\n3\n0\n");
        assertTrue(second.contains("User: Alice Example | Type: User"));
        assertTrue(second.contains("The library is currently empty."));
        assertFalse(Files.readString(directory.resolve("users.json")).contains("secret123"));
    }

    @Test
    void persistedAdministratorIsDisplayedAsAdmin() throws IOException {
        new JsonUserRepository(directory.resolve("users.json")).save(
                TestAccounts.user("A001", "admin", "Library Admin", Role.ADMIN, "admin123"));
        assertTrue(run("2\nadmin\nadmin123\n3\n0\n")
                .contains("User: Library Admin | Type: Admin"));
    }

    @Test
    void corruptAccountDataCannotLogInAndIsNotReset() throws IOException {
        Path users = directory.resolve("users.json");
        Files.writeString(users, "{broken");
        String result = run("2\nalice\nsecret123\n0\n");
        assertTrue(result.contains("user data could not be read"));
        assertFalse(result.contains("Login successful."));
        assertEquals("{broken", Files.readString(users));
    }

    @Test
    void mainExplainsStartupFailureWithoutStackTrace() throws IOException {
        Path notDirectory = directory.resolve("file");
        Files.writeString(notDirectory, "keep");
        PrintStream original = System.out;
        var bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes, true, StandardCharsets.UTF_8));
            App.main(new String[] {notDirectory.toString()});
            App.main(new String[] {"one", "two"});
        } finally {
            System.setOut(original);
        }
        String result = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("VSAS could not open its data directory."));
        assertTrue(result.contains("Usage: VSAS [data-directory]"));
        assertFalse(result.contains("Exception"));
        assertEquals("keep", Files.readString(notDirectory));
    }
}
