package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.fixtures.TestAccounts;
import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminUserServiceTest {
    private final TestAccounts.MemoryUsers users = new TestAccounts.MemoryUsers();
    private final AuthenticationService authentication =
            new AuthenticationService(users, new PasswordHasher(1_000));
    private final AdminUserService service = new AdminUserService(users, authentication);
    private final User admin = TestAccounts.user("A001", "admin", "Library Admin",
            Role.ADMIN, "adminSecret");
    private final User member = TestAccounts.user("U001", "alice", "Alice Example",
            Role.MEMBER, "memberSecret");

    @BeforeEach
    void seedAccounts() {
        users.save(admin);
        users.save(member);
    }

    @Test
    void adminReceivesEveryAccountsSixProfileFieldsIncludingOtherAdmins() throws IOException {
        var secondAdmin = TestAccounts.user("A002", "librarian", "Second Admin",
                Role.ADMIN, "anotherSecret");
        users.save(secondAdmin);
        authentication.login("admin", "adminSecret");
        var profiles = service.listUsers();
        assertEquals(List.of(
                new AdminUserService.UserProfile("admin", "Library Admin", "admin@example.org",
                        "0400000000", "A001", "Admin"),
                new AdminUserService.UserProfile("alice", "Alice Example", "alice@example.org",
                        "0400000000", "U001", "User"),
                new AdminUserService.UserProfile("librarian", "Second Admin", "librarian@example.org",
                        "0400000000", "A002", "Admin")), profiles);
        for (var account : users.findAll()) {
            assertFalse(profiles.toString().contains(account.passwordHash()));
            assertFalse(profiles.toString().contains(account.passwordSalt()));
        }
        assertFalse(profiles.toString().contains("adminSecret"));
        assertFalse(profiles.toString().contains("memberSecret"));
        assertThrows(UnsupportedOperationException.class, profiles::clear);
    }

    @Test
    void anonymousGuestAndMemberAreDeniedBeforeAnyProfileRead() throws IOException {
        UserRepository unreadable = new UserRepository() {
            public List<User> findAll() { fail("Unauthorized requests must not read profiles"); return List.of(); }
            public void save(User user) { fail("Viewing profiles must not write accounts"); }
            public void deleteById(String id) { fail("Viewing profiles must not delete accounts"); }
        };
        var guarded = new AdminUserService(unreadable, authentication);
        assertThrows(SecurityException.class, guarded::listUsers);
        authentication.enterGuest();
        assertThrows(SecurityException.class, guarded::listUsers);
        assertTrue(authentication.login("alice", "memberSecret"));
        assertThrows(SecurityException.class, guarded::listUsers);
    }

    @Test
    void logoutAndSwitchingToMemberRevokeAccessForTheSameService() throws IOException {
        authentication.login("admin", "adminSecret");
        assertEquals(2, service.listUsers().size());
        authentication.logout();
        assertThrows(SecurityException.class, service::listUsers);
        authentication.login("alice", "memberSecret");
        assertThrows(SecurityException.class, service::listUsers);
    }

    @Test
    void eachListingReflectsCurrentStoredProfilesRatherThanACachedSnapshot() throws IOException {
        authentication.login("admin", "adminSecret");
        assertEquals(2, service.listUsers().size());
        users.save(TestAccounts.user("U002", "bob", "Bob", Role.MEMBER, "bobSecret"));
        assertEquals(3, service.listUsers().size());
        users.deleteById("U001");
        assertEquals(List.of("admin", "bob"), service.listUsers().stream()
                .map(AdminUserService.UserProfile::username).toList());
    }
}
