package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;

import java.io.IOException;
import java.util.Objects;

/** US13: only the current authenticated administrator may create members or admins. */
public final class AdminAccountCreationService {
    private final RegistrationService registration;
    private final AuthenticationService authentication;

    public AdminAccountCreationService(RegistrationService registration,
                                       AuthenticationService authentication) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.authentication = Objects.requireNonNull(authentication, "authentication");
    }

    public void requireAdmin() {
        authentication.currentUser().filter(User::isAdmin)
                .orElseThrow(() -> new SecurityException(
                        "Only administrators can create user accounts."));
    }

    /** Saves immediately; creating an account never replaces the administrator's session. */
    public User createAccount(RegistrationRequest request, Role role) throws IOException {
        requireAdmin();
        if (role == null) {
            throw new IllegalArgumentException("Account type is required.");
        }
        return registration.createAccount(request, role);
    }

    // US21's existing field checks are reused, including uniqueness and normalization.
    // Check permissions before each field lookup as well as before the final save.
    public String checkUsername(String value) throws IOException {
        requireAdmin();
        return registration.checkUsername(value);
    }

    public String checkPassword(String value) {
        requireAdmin();
        return registration.checkPassword(value);
    }

    public String checkFullName(String value) {
        requireAdmin();
        return registration.checkFullName(value);
    }

    public String checkEmail(String value) {
        requireAdmin();
        return registration.checkEmail(value);
    }

    public String checkPhone(String value) {
        requireAdmin();
        return registration.checkPhone(value);
    }

    public String checkIdKey(String value) throws IOException {
        requireAdmin();
        return registration.checkIdKey(value);
    }
}
