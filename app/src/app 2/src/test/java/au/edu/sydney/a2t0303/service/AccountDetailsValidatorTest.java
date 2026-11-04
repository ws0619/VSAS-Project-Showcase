package au.edu.sydney.a2t0303.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class AccountDetailsValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"alice@example.org", "a.b-c@uni.sydney.edu.au", "x@y.io"})
    void validEmailsAreAccepted(String email) {
        assertEquals(email, AccountDetailsValidator.email(email));
    }

    @ParameterizedTest
    @ValueSource(strings = {"alice", "alice@example", "@example.org", "alice@.org.",
            "ali ce@example.org", "alice@@example.org"})
    void invalidEmailsAreRejected(String email) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AccountDetailsValidator.email(email));
        assertEquals("Email must look like name@example.com.", e.getMessage());
    }

    @Test
    void emailIsStrippedAndRequired() {
        assertEquals("alice@example.org", AccountDetailsValidator.email("  alice@example.org "));
        assertEquals("Email is required.", assertThrows(IllegalArgumentException.class,
                () -> AccountDetailsValidator.email("   ")).getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"04000000", "0400000000", "+61400000000", "123456789012345"})
    void validPhoneNumbersAreAccepted(String phone) {
        assertEquals(phone, AccountDetailsValidator.phone(phone));
    }

    @Test
    void phoneSpacesAndHyphensAreRemoved() {
        assertEquals("0400000000", AccountDetailsValidator.phone(" 0400 000-000 "));
        assertEquals("+61400000000", AccountDetailsValidator.phone("+61 400 000 000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0400000", "1234567890123456", "04000abc00", "04+00000000", "++61400000000"})
    void invalidPhoneNumbersAreRejected(String phone) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AccountDetailsValidator.phone(phone));
        assertEquals("Phone number must have 8 to 15 digits and may start with +.", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"U01", "U001", "user_01-a", "ABCDEFGHIJ0123456789"})
    void validIdKeysAreAccepted(String idKey) {
        assertEquals(idKey, AccountDetailsValidator.idKey(idKey));
    }

    @ParameterizedTest
    @ValueSource(strings = {"U1", "ABCDEFGHIJ01234567890", "U 001", "U@01", "用户01"})
    void invalidIdKeysAreRejected(String idKey) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AccountDetailsValidator.idKey(idKey));
        assertEquals("ID key must be 3 to 20 characters using only letters, digits, - or _.",
                e.getMessage());
    }

    @Test
    void missingValuesAreReportedAsRequired() {
        assertEquals("ID key is required.", assertThrows(IllegalArgumentException.class,
                () -> AccountDetailsValidator.idKey(null)).getMessage());
        assertEquals("Phone number is required.", assertThrows(IllegalArgumentException.class,
                () -> AccountDetailsValidator.phone("")).getMessage());
    }
}
