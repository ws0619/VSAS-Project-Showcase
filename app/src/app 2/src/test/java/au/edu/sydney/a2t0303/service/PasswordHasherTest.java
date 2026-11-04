package au.edu.sydney.a2t0303.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    // Few iterations so the tests run fast; the real application uses DEFAULT_ITERATIONS
    private final PasswordHasher hasher = new PasswordHasher(1_000);

    @Test
    void hashIsNotThePlainPassword() {
        PasswordHasher.HashedPassword result = hasher.hash("secret123");
        assertNotEquals("secret123", result.hash());
        assertFalse(result.hash().isBlank());
        assertFalse(result.salt().isBlank());
    }

    @Test
    void correctPasswordIsVerified() {
        PasswordHasher.HashedPassword result = hasher.hash("secret123");
        assertTrue(hasher.verify("secret123", result.hash(), result.salt()));
    }

    @Test
    void wrongPasswordIsRejected() {
        PasswordHasher.HashedPassword result = hasher.hash("secret123");
        assertFalse(hasher.verify("wrong", result.hash(), result.salt()));
    }

    @Test
    void samePasswordGetsDifferentSaltAndHash() {
        PasswordHasher.HashedPassword first = hasher.hash("secret123");
        PasswordHasher.HashedPassword second = hasher.hash("secret123");
        assertNotEquals(first.salt(), second.salt());
        assertNotEquals(first.hash(), second.hash());
    }

    @Test
    void emptyOrNullPasswordCannotBeHashed() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(""));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(null));
    }

    @Test
    void missingOrCorruptedDataFailsVerification() {
        PasswordHasher.HashedPassword result = hasher.hash("secret123");
        assertFalse(hasher.verify(null, result.hash(), result.salt()));
        assertFalse(hasher.verify("secret123", null, result.salt()));
        assertFalse(hasher.verify("secret123", result.hash(), null));
        assertFalse(hasher.verify("secret123", "not base64!", result.salt()));
    }

    @Test
    void storedIterationsAreUsedForVerification() {
        // Hash created with 1,000 iterations, checked by a hasher configured for 2,000
        PasswordHasher.HashedPassword result = hasher.hash("password123");
        PasswordHasher otherHasher = new PasswordHasher(2_000);

        assertFalse(otherHasher.verify("password123", result.hash(), result.salt()));
        assertTrue(otherHasher.verify("password123", result.hash(), result.salt(), 1_000));
        assertFalse(otherHasher.verify("wrong", result.hash(), result.salt(), 1_000));
    }

    @Test
    void emptySaltOrHashFailsWithoutThrowing() {
        PasswordHasher.HashedPassword result = hasher.hash("password123");
        assertFalse(hasher.verify("password123", result.hash(), ""));
        assertFalse(hasher.verify("password123", "", result.salt()));
    }

    @Test
    void invalidStoredIterationsFailVerification() {
        PasswordHasher.HashedPassword result = hasher.hash("password123");
        assertFalse(hasher.verify("password123", result.hash(), result.salt(), 0));
    }

    @Test
    void iterationsAndAlgorithmAreExposedForStorage() {
        assertEquals(1_000, hasher.getIterations());
        assertEquals("PBKDF2WithHmacSHA256", PasswordHasher.ALGORITHM);
    }

    @Test
    void iterationsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(0));
    }

    @Test
    void defaultConstructorProducesVerifiableHash() {
        PasswordHasher defaultHasher = new PasswordHasher();
        PasswordHasher.HashedPassword result = defaultHasher.hash("secret123");
        assertTrue(defaultHasher.verify("secret123", result.hash(), result.salt()));
    }
}
