package au.edu.sydney.a2t0303.storage;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonAccountLockoutTest {
    @TempDir Path directory;
    private User user() { return TestAccounts.user("U001","alice","Alice",Role.MEMBER,"secret"); }
    private JsonUserRepository repository() throws IOException { return new JsonUserRepository(directory.resolve("users.json")); }

    @Test void legacyJsonWithoutSecurityFieldsLoadsUnlockedAndPersistsNewFailures() throws IOException {
        var users=repository();users.save(user());
        var mapper=new ObjectMapper();var document=mapper.readTree(directory.resolve("users.json").toFile());
        ObjectNode node=(ObjectNode)document.get("users").get(0);node.remove("failedLoginAttempts");node.remove("locked");
        mapper.writeValue(directory.resolve("users.json").toFile(),document);
        byte[] legacy=Files.readAllBytes(directory.resolve("users.json"));
        var auth=new AuthenticationService(repository(),new PasswordHasher(1000));
        assertTrue(auth.login("alice","secret"));
        assertArrayEquals(legacy,Files.readAllBytes(directory.resolve("users.json")));
        assertFalse(auth.login("alice","wrong"));
        var saved=repository().findById("U001").orElseThrow();
        assertEquals(1,saved.failedLoginAttempts());assertFalse(saved.locked());
        assertTrue(Files.readString(directory.resolve("users.json")).contains("failedLoginAttempts"));
    }

    @Test void ordinaryUpdatesCannotResetFailuresOrLockState() throws IOException {
        var users=repository();var original=user();users.save(original);
        users.recordLoginResult(original,false);
        assertThrows(IllegalArgumentException.class,()->users.update(original));
        users.recordLoginResult(original,false);users.recordLoginResult(original,false);
        assertThrows(IllegalArgumentException.class,()->users.update(original));
        assertTrue(repository().findById("U001").orElseThrow().locked());
    }

    @Test void invalidFailureCountsAndInconsistentFlagsAreRejected() throws IOException {
        var users=repository();var original=user();
        for(var invalid:new User[]{original.withLoginState(-1,false),original.withLoginState(4,true),
                original.withLoginState(3,false),original.withLoginState(0,true)}) {
            assertThrows(IllegalArgumentException.class,()->users.save(invalid));
        }
        assertTrue(users.findAll().isEmpty());
    }

    @Test void loginStateMutationsPreserveCredentialsRolesAndProfileTimestamps() throws IOException {
        var users=repository();var original=user();users.save(original);
        for(int i=1;i<=3;i++) assertEquals(original.withLoginState(i,i==3),users.recordLoginResult(original,false));
        assertEquals(original.withLoginState(3,true),users.recordLoginResult(original,true));
        assertEquals(original,users.unlockAccount("U001"));
        assertEquals(original,repository().findById("U001").orElseThrow());
    }

    @Test void missingTargetsAndChangedCredentialsCannotCreateLoginStateRecords() throws IOException {
        var users=repository();var original=user();
        assertThrows(SecurityException.class,()->users.recordLoginResult(original,false));
        assertThrows(IllegalArgumentException.class,()->users.unlockAccount("UNKNOWN"));
        assertTrue(users.findAll().isEmpty());
        users.save(original);
        assertThrows(SecurityException.class,()->users.recordLoginResult(
                TestAccounts.user("U001","alice","Alice",Role.MEMBER,"other"),true));
        assertEquals(original,users.findById("U001").orElseThrow());
    }

    @Test void invalidPersistedLockStateFailsClosedWithoutRewritingData() throws IOException {
        var users=repository();users.save(user());
        var mapper=new ObjectMapper();var document=mapper.readTree(directory.resolve("users.json").toFile());
        ((ObjectNode)document.get("users").get(0)).put("failedLoginAttempts",3).put("locked",false);
        mapper.writeValue(directory.resolve("users.json").toFile(),document);
        byte[] invalid=Files.readAllBytes(directory.resolve("users.json"));
        assertThrows(IOException.class,users::findAll);
        assertArrayEquals(invalid,Files.readAllBytes(directory.resolve("users.json")));
    }
}
