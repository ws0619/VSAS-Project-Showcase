package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.service.*;
import au.edu.sydney.a2t0303.storage.JsonUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Scanner;
import static org.junit.jupiter.api.Assertions.*;

class AdminUnlockUserCliTest {
    @TempDir Path directory;
    private JsonUserRepository users;
    private AuthenticationService admin;
    @BeforeEach void setUp() throws IOException {
        users=new JsonUserRepository(directory.resolve("users.json"));
        users.save(TestAccounts.user("U001","alice","Alice",Role.MEMBER,"secret").withLoginState(3,true));
        users.save(TestAccounts.user("A001","admin","Admin",Role.ADMIN,"admin-secret"));
        admin=new AuthenticationService(users,new PasswordHasher(1000));assertTrue(admin.login("admin","admin-secret"));
    }
    private String run(AuthenticationService session,String input) {
        var bytes=new ByteArrayOutputStream();
        new AdminUnlockUserCli(new AdminAccountUnlockService(users,session),new Scanner(input),
                new PrintStream(bytes,true,StandardCharsets.UTF_8)).unlock();
        return bytes.toString(StandardCharsets.UTF_8);
    }
    @Test void adminCanListAndUnlockAnAccountWithoutExposingPasswordData() throws IOException {
        String output=run(admin," U001 \n");
        assertTrue(output.contains("Username: alice"));assertTrue(output.contains("Failed attempts: 3"));
        assertTrue(output.contains("Account unlocked successfully. User ID: U001"));
        assertFalse(output.contains("passwordHash"));assertFalse(output.contains("PBKDF2"));
        assertFalse(output.contains(users.findById("U001").orElseThrow().passwordHash()));
        assertFalse(users.findById("U001").orElseThrow().locked());
    }
    @Test void blankAndEofCancelWithoutChangingLockState() throws IOException {
        assertTrue(run(admin,"\n").contains("Unlock cancelled."));
        assertTrue(run(admin,"").contains("Unlock cancelled."));
        assertTrue(users.findById("U001").orElseThrow().locked());
    }
    @Test void invalidIdGivesAnErrorAndPreservesLock() throws IOException {
        assertTrue(run(admin,"UNKNOWN\n").contains("Unlock failed: User not found."));
        assertTrue(users.findById("U001").orElseThrow().locked());
    }
    @Test void guestsAreRejectedBeforeListingOrPrompting() {
        admin.enterGuest();String output=run(admin,"U001\n");
        assertTrue(output.contains("Only administrators can unlock accounts."));
        assertFalse(output.contains("Username: alice"));assertFalse(output.contains("Enter User ID"));
    }
    @Test void noLockedAccountsReturnsWithoutPrompt() throws IOException {
        users.unlockAccount("U001");String output=run(admin,"U001\n");
        assertTrue(output.contains("No locked accounts."));assertFalse(output.contains("Enter User ID"));
    }
    @Test void repositoryFailureIsReportedWithoutAStackTrace() throws IOException {
        Files.writeString(directory.resolve("users.json"),"invalid");String output=run(admin,"U001\n");
        assertTrue(output.contains("Unlock failed:"));assertFalse(output.contains("Exception"));
    }
}
