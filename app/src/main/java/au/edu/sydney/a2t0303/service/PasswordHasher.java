package au.edu.sydney.a2t0303.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Turns passwords into salted PBKDF2 hashes and checks passwords against them.
 * Plain-text passwords are never stored.
 */
public class PasswordHasher {

    /** Recommended work factor for PBKDF2-HMAC-SHA256. */
    public static final int DEFAULT_ITERATIONS = 600_000;

    /** Stored in each User record as passwordAlgorithm. */
    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    /** A hash and the salt that produced it, both Base64-encoded. */
    public record HashedPassword(String hash, String salt) {}

    public PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    /** Tests can use fewer iterations so they run quickly. */
    public PasswordHasher(int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("Iterations must be positive.");
        }
        this.iterations = iterations;
    }

    /** The work factor this hasher uses; stored in each User record as passwordIterations. */
    public int getIterations() {
        return iterations;
    }

    /** Hashes a new password with a fresh random salt. */
    public HashedPassword hash(String password) {
        requirePassword(password);

        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);

        byte[] hash = pbkdf2(password, salt, iterations);
        Base64.Encoder encoder = Base64.getEncoder();
        return new HashedPassword(encoder.encodeToString(hash), encoder.encodeToString(salt));
    }

    /**
     * Returns true if the password matches the stored hash and salt,
     * using this hasher's own iteration count.
     * Prefer the four-argument version when checking a stored user,
     * so the user's saved passwordIterations is used.
     */
    public boolean verify(String password, String storedHash, String storedSalt) {
        return verify(password, storedHash, storedSalt, iterations);
    }

    /**
     * Returns true if the password matches the stored hash and salt,
     * using the iteration count that was saved with the hash
     * (User.passwordIterations()). Never throws: invalid stored data
     * simply fails verification.
     */
    public boolean verify(String password, String storedHash, String storedSalt, int storedIterations) {
        if (password == null || storedHash == null || storedSalt == null || storedIterations < 1) {
            return false;
        }

        byte[] expected;
        byte[] salt;
        try {
            expected = Base64.getDecoder().decode(storedHash);
            salt = Base64.getDecoder().decode(storedSalt);
        } catch (IllegalArgumentException e) {
            return false; // stored data is not valid Base64
        }
        if (expected.length == 0 || salt.length == 0) {
            return false; // PBKDF2 rejects an empty salt, and an empty hash can never match
        }

        byte[] actual = pbkdf2(password, salt, storedIterations);
        // Constant-time comparison, so timing does not reveal how much matched
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] pbkdf2(String password, byte[] salt, int rounds) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, rounds, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " is not available.", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static void requirePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }
    }
}
