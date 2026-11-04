package au.edu.sydney.a2t0303;

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

class AppAccountLockoutIntegrationTest {
    @TempDir Path directory;
    private JsonUserRepository users;
    @BeforeEach void setUp() throws IOException {
        users=new JsonUserRepository(directory.resolve("users.json"));
        users.save(TestAccounts.user("U001","alice","Alice",Role.MEMBER,"secret"));
        users.save(TestAccounts.user("A001","reviewadmin","Admin",Role.ADMIN,"admin-secret"));
    }
    private String run(String input) throws IOException {
        var bytes=new ByteArrayOutputStream();
        App.run(directory,new Scanner(input),new PrintStream(bytes,true,StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }
    private String login(String username,String password) { return "2\n"+username+"\n"+password+"\n"; }
    private String failures() { return login("alice","wrong").repeat(3); }

    @Test void thirdFailureShowsClearMessageAndCorrectPasswordIsRejectedAfterRestart() throws IOException {
        String output=run(failures()+"0\n");
        assertEquals(2,output.split("incorrect username or password",-1).length-1);
        assertTrue(output.contains("Account is locked after 3 failed login attempts."));
        assertTrue(output.contains("contact an administrator"));assertFalse(output.contains("Login successful."));
        assertTrue(users.findById("U001").orElseThrow().locked());
        String restarted=run(login("alice","secret")+"0\n");
        assertTrue(restarted.contains("Account is locked"));assertFalse(restarted.contains("Login successful."));
    }

    @Test void fullAdminMenuUnlockJourneyAllowsSubsequentUserLoginAndPersistsReset() throws IOException {
        run(failures()+"0\n");
        String output=run(login("reviewadmin","admin-secret")+"7\n5\nU001\n0\n3\n"+login("alice","secret")+"0\n");
        assertTrue(output.contains("5. Unlock a user account"));
        assertTrue(output.contains("3. Delete a user"));assertTrue(output.contains("4. View scroll statistics"));
        assertTrue(output.contains("Account unlocked successfully. User ID: U001"));
        assertTrue(output.contains("User: Alice | Type: User"));
        var saved=new JsonUserRepository(directory.resolve("users.json")).findById("U001").orElseThrow();
        assertFalse(saved.locked());assertEquals(0,saved.failedLoginAttempts());
        assertTrue(run(login("alice","secret")+"0\n").contains("Login successful."));
    }

    @Test void successfulLoginBreaksTheFailureSequenceAcrossApplicationRuns() throws IOException {
        run(login("alice","wrong").repeat(2)+"0\n");
        assertTrue(run(login("alice","secret")+"0\n").contains("Login successful."));
        run(login("alice","wrong")+"0\n");
        var saved=users.findById("U001").orElseThrow();
        assertEquals(1,saved.failedLoginAttempts());assertFalse(saved.locked());
    }

    @Test void memberAndGuestCannotReachAdminUnlockScreen() throws IOException {
        String output=run(login("alice","secret")+"7\n3\n3\n7\n0\n");
        assertFalse(output.contains("=== Unlock User Account ==="));
        assertFalse(output.contains("5. Unlock a user account"));
        assertTrue(output.contains("Only administrators"));
    }

    @Test void bootstrapAdminIsNotSilentlyUnlockedOnRestart() throws IOException {
        users.deleteById("A001");
        run("0\n");
        users.save(TestAccounts.user("A001","reviewadmin","Admin",Role.ADMIN,"admin-secret"));
        String name=DefaultAdminService.DEFAULT_USERNAME;
        run(login(name,"wrong").repeat(3)+"0\n");
        String output=run(login(name,DefaultAdminService.DEFAULT_PASSWORD)+"0\n");
        assertTrue(output.contains("Account is locked"));assertFalse(output.contains("Login successful."));
        String unlocked=run(login("reviewadmin","admin-secret")+"7\n5\n"+DefaultAdminService.DEFAULT_ID+"\n0\n3\n"
                +login(name,DefaultAdminService.DEFAULT_PASSWORD)+"0\n");
        assertTrue(unlocked.contains("Account unlocked successfully."));
        assertTrue(unlocked.contains("Login successful."));
    }
}
