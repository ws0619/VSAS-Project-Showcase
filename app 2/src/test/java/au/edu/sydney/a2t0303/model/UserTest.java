package au.edu.sydney.a2t0303.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private User userWithRole(Role role) {
        Instant now = Instant.parse("2026-10-13T00:00:00Z");
        return new User("U001", "alice", "Alice Example", "alice@example.org",
                "0400000000", role, "hash", "salt",
                "PBKDF2WithHmacSHA256", 1000, now, now);
    }

    @Test
    void adminIsRecognised() {
        assertTrue(userWithRole(Role.ADMIN).isAdmin());
    }

    @Test
    void memberIsNotAdmin() {
        assertFalse(userWithRole(Role.MEMBER).isAdmin());
    }
}
