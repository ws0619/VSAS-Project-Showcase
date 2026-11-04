package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.JsonUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;
import java.util.ArrayList;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountLockoutServiceTest {
    @TempDir Path directory;
    private JsonUserRepository users;
    private AuthenticationService authentication;
    private User alice;
    @BeforeEach void setUp() throws IOException {
        users = new JsonUserRepository(directory.resolve("users.json"));
        alice = TestAccounts.user("U001", "alice", "Alice", Role.MEMBER, "secret");
        users.save(alice);
        users.save(TestAccounts.user("A001", "admin", "Admin", Role.ADMIN, "admin-secret"));
        authentication = session();
    }
    private AuthenticationService session() { return new AuthenticationService(users,new PasswordHasher(1000)); }
    private User current() throws IOException { return users.findById("U001").orElseThrow(); }
    private void lockAlice() throws IOException {
        assertFalse(authentication.login("alice","wrong"));
        assertFalse(authentication.login("alice","wrong"));
        assertThrows(AuthenticationService.AccountLockedException.class,()->authentication.login("alice","wrong"));
    }
    private AdminAccountUnlockService admin() throws IOException {
        var admin = session(); assertTrue(admin.login("admin","admin-secret"));
        return new AdminAccountUnlockService(users,admin);
    }

    @Test void exactlyThirdFailureLocksAndDisplaysActionableMessage() throws IOException {
        assertFalse(authentication.login("alice","wrong"));
        assertEquals(alice.withLoginState(1,false),current());
        assertFalse(authentication.login("alice","wrong"));
        assertEquals(2,current().failedLoginAttempts()); assertFalse(current().locked());
        var e=assertThrows(AuthenticationService.AccountLockedException.class,()->authentication.login("alice","wrong"));
        assertTrue(e.getMessage().contains("locked"));assertTrue(e.getMessage().contains("administrator"));
        assertEquals(alice.withLoginState(3,true),current());
        assertTrue(authentication.currentUser().isEmpty());assertFalse(authentication.isGuest());
    }

    @Test void lockedAccountRejectsCorrectPasswordWithoutFurtherHashingOrWrites() throws IOException {
        lockAlice(); byte[] json=Files.readAllBytes(directory.resolve("users.json"));
        var noHash = new PasswordHasher(1000) {
            @Override public boolean verify(String p,String h,String s,int i) { fail("Locked accounts must be rejected before hashing");return false; }
        };
        var lockedSession=new AuthenticationService(users,noHash);
        for(String password:new String[]{"secret","wrong",""}) {
            assertThrows(AuthenticationService.AccountLockedException.class,()->lockedSession.login("alice",password));
        }
        assertArrayEquals(json,Files.readAllBytes(directory.resolve("users.json")));
        assertEquals(3,current().failedLoginAttempts());assertTrue(lockedSession.currentUser().isEmpty());
    }

    @Test void twoFailuresThenSuccessResetTheConsecutiveCounter() throws IOException {
        authentication.login("alice","wrong");authentication.login("alice","wrong");
        assertTrue(authentication.login("alice","secret"));
        assertEquals(alice,current());assertEquals(alice,authentication.currentUser().orElseThrow());
        assertFalse(authentication.login("alice","wrong"));
        assertEquals(1,current().failedLoginAttempts());assertFalse(current().locked());
    }

    @Test void failuresArePerAccountAndUnknownUsersDoNotResetThem() throws IOException {
        users.save(TestAccounts.user("U002","bob","Bob",Role.MEMBER,"bob-secret"));
        authentication.login("alice","wrong");
        assertFalse(authentication.login("unknown","wrong"));
        assertTrue(authentication.login("bob","bob-secret"));
        authentication.enterGuest();authentication.logout();
        assertEquals(1,current().failedLoginAttempts());
        assertEquals(0,users.findById("U002").orElseThrow().failedLoginAttempts());
        assertFalse(authentication.login("alice","wrong"));
        assertThrows(AuthenticationService.AccountLockedException.class,()->authentication.login("alice","wrong"));
    }

    @Test void failedAttemptsAndLockSurviveRepositoryAndApplicationSessionRestart() throws IOException {
        assertFalse(authentication.login("alice","wrong"));
        var restarted=new AuthenticationService(new JsonUserRepository(directory.resolve("users.json")),new PasswordHasher(1000));
        assertFalse(restarted.login("alice","wrong"));
        assertThrows(AuthenticationService.AccountLockedException.class,()->restarted.login("alice","wrong"));
        var third=new AuthenticationService(new JsonUserRepository(directory.resolve("users.json")),new PasswordHasher(1000));
        assertThrows(AuthenticationService.AccountLockedException.class,()->third.login("alice","secret"));
    }

    @Test void blankSubmittedPasswordsCountButCancelledInputDoesNot() throws IOException {
        assertFalse(authentication.login("alice",null));assertEquals(0,current().failedLoginAttempts());
        assertFalse(authentication.login(" alice ",""));
        assertFalse(authentication.login("alice"," "));
        assertThrows(AuthenticationService.AccountLockedException.class,()->authentication.login("alice","\t"));
    }

    @Test void administratorUnlockClearsBothFieldsAndAllowsLogin() throws IOException {
        lockAlice();var unlock=admin();
        assertEquals(1,unlock.listLockedAccounts().size());
        var result=unlock.unlock(" U001 ");
        assertEquals(alice,result);assertEquals(alice,current());
        assertTrue(new AuthenticationService(new JsonUserRepository(directory.resolve("users.json")),new PasswordHasher(1000)).login("alice","secret"));
        assertFalse(authentication.login("alice","wrong"));assertEquals(1,current().failedLoginAttempts());
    }

    @Test void memberGuestAndLoggedOutSessionsCannotUnlockOrListLockedAccounts() throws IOException {
        var member=session();assertTrue(member.login("alice","secret"));lockAlice();
        var service=new AdminAccountUnlockService(users,member);
        assertThrows(SecurityException.class,()->service.unlock("U001"));
        assertThrows(SecurityException.class,service::listLockedAccounts);
        member.enterGuest();assertThrows(SecurityException.class,()->service.unlock("U001"));
        member.logout();assertThrows(SecurityException.class,()->service.unlock("U001"));
        assertTrue(current().locked());
    }

    @Test void deletedOrNowLockedAdminCannotUseAStaleSessionToUnlock() throws IOException {
        var admin=session();assertTrue(admin.login("admin","admin-secret"));
        var unlock=new AdminAccountUnlockService(users,admin);lockAlice();
        var attacker=session();attacker.login("admin","wrong");attacker.login("admin","wrong");
        assertThrows(AuthenticationService.AccountLockedException.class,()->attacker.login("admin","wrong"));
        assertThrows(SecurityException.class,()->unlock.unlock("U001"));
        users.deleteById("A001");assertThrows(SecurityException.class,unlock::listLockedAccounts);
        assertTrue(current().locked());
    }

    @Test void unlockedUnknownAndBlankTargetsAreRejectedWithoutModifyingAccounts() throws IOException {
        var unlock=admin();byte[] before=Files.readAllBytes(directory.resolve("users.json"));
        for(String id:new String[]{null,""," ","UNKNOWN","U001"}) {
            assertThrows(IllegalArgumentException.class,()->unlock.unlock(id));
        }
        assertArrayEquals(before,Files.readAllBytes(directory.resolve("users.json")));
    }

    @Test void profileAndPasswordChangesCannotAccidentallyUnlockAnAccount() throws IOException {
        lockAlice();var profile=new ProfileService(users,new PasswordHasher(1000),Clock.systemUTC());
        var changed=profile.updateDetails("U001","New Alice",null,null);
        assertTrue(changed.locked());assertEquals(3,changed.failedLoginAttempts());
        var password=profile.changePassword("U001","secret","new-secret");
        assertTrue(password.locked());assertEquals(3,password.failedLoginAttempts());
        assertThrows(AuthenticationService.AccountLockedException.class,()->authentication.login("alice","new-secret"));
        admin().unlock("U001");assertTrue(authentication.login("alice","new-secret"));
    }

    @Test void failedSecurityWriteClearsSessionAndLeavesStoredStateUnchanged() throws IOException {
        authentication.login("alice","wrong");authentication.login("alice","wrong");
        var failing=new JsonUserRepository(directory.resolve("users.json")) {
            @Override public synchronized User recordLoginResult(User u,boolean ok) throws IOException { throw new IOException("write unavailable"); }
        };
        var service=new AuthenticationService(failing,new PasswordHasher(1000));
        assertThrows(IOException.class,()->service.login("alice","wrong"));
        assertThrows(IOException.class,()->service.login("alice","secret"));
        assertEquals(2,current().failedLoginAttempts());assertFalse(current().locked());
        assertTrue(service.currentUser().isEmpty());
    }

    @Test void failedUnlockWriteDoesNotClearThePersistedLock() throws IOException {
        lockAlice();var actor=session();assertTrue(actor.login("admin","admin-secret"));
        var failing=new JsonUserRepository(directory.resolve("users.json")) {
            @Override public synchronized User unlockAccount(String id) throws IOException { throw new IOException("write unavailable"); }
        };
        var unlock=new AdminAccountUnlockService(failing,actor);
        assertThrows(IOException.class,()->unlock.unlock("U001"));
        assertTrue(current().locked());assertEquals(3,current().failedLoginAttempts());
    }

    @Test void concurrentFailuresWithinOneRepositoryStopAtThree() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(6);
        try {
            var tasks=new ArrayList<Future<?>>();
            for(int i=0;i<12;i++) tasks.add(pool.submit(()->{
                try { assertFalse(session().login("alice","wrong")); }
                catch(AuthenticationService.AccountLockedException expected) {}
                catch(IOException e) { throw new RuntimeException(e); }
            }));
            for(var task:tasks) task.get();
        } finally { pool.shutdownNow(); }
        assertEquals(3,current().failedLoginAttempts());assertTrue(current().locked());
    }

    @Test void lockAppliedDuringPasswordVerificationStillBlocksCorrectPassword() throws IOException {
        var hasher=new PasswordHasher(1000) {
            @Override public boolean verify(String p,String h,String s,int iterations) {
                boolean valid=super.verify(p,h,s,iterations);
                try { users.recordLoginResult(alice,false);users.recordLoginResult(alice,false);users.recordLoginResult(alice,false); }
                catch(IOException e) { throw new java.io.UncheckedIOException(e); }
                return valid;
            }
        };
        var service=new AuthenticationService(users,hasher);
        assertThrows(AuthenticationService.AccountLockedException.class,()->service.login("alice","secret"));
        assertTrue(service.currentUser().isEmpty());assertTrue(current().locked());
    }

    @Test void passwordChangedDuringVerificationCannotAuthenticateOldCredentials() throws IOException {
        var hasher=new PasswordHasher(1000) {
            @Override public boolean verify(String p,String h,String s,int iterations) {
                boolean valid=super.verify(p,h,s,iterations);
                try { new ProfileService(users,new PasswordHasher(1000),Clock.systemUTC()).changePassword("U001","secret","changed"); }
                catch(IOException e) { throw new java.io.UncheckedIOException(e); }
                return valid;
            }
        };
        var service=new AuthenticationService(users,hasher);
        assertThrows(SecurityException.class,()->service.login("alice","secret"));
        assertTrue(service.currentUser().isEmpty());assertTrue(session().login("alice","changed"));
    }
}
