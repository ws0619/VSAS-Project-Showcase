package au.edu.sydney.a2t0303.service;

import java.util.regex.Pattern;

/**
 * Format rules for account details (US21), shared by registration (US01) and profile
 * updates (US07). Rules agreed by the team on 18 October 2026.
 * Each method returns the cleaned value or throws IllegalArgumentException with a clear message.
 */
public final class AccountDetailsValidator {

    // Something before and after a single @, and a dot in the domain; no spaces.
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    // 8 to 15 digits with an optional leading +, after spaces and hyphens are removed.
    private static final Pattern PHONE = Pattern.compile("\\+?\\d{8,15}");

    // 3 to 20 letters, digits, hyphens or underscores.
    private static final Pattern ID_KEY = Pattern.compile("[A-Za-z0-9_-]{3,20}");

    private AccountDetailsValidator() {
    }

    /** Removes surrounding spaces; rejects a missing or blank value. */
    public static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.strip();
    }

    public static String email(String value) {
        String email = required(value, "Email");
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Email must look like name@example.com.");
        }
        return email;
    }

    /** Returns the number without spaces or hyphens, e.g. "0400 000-000" becomes "0400000000". */
    public static String phone(String value) {
        String phone = required(value, "Phone number").replaceAll("[\\s-]", "");
        if (!PHONE.matcher(phone).matches()) {
            throw new IllegalArgumentException(
                    "Phone number must have 8 to 15 digits and may start with +.");
        }
        return phone;
    }

    public static String idKey(String value) {
        String idKey = required(value, "ID key");
        if (!ID_KEY.matcher(idKey).matches()) {
            throw new IllegalArgumentException(
                    "ID key must be 3 to 20 characters using only letters, digits, - or _.");
        }
        return idKey;
    }
}
