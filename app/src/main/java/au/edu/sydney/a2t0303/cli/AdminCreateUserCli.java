package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.model.Role;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.AdminAccountCreationService;
import au.edu.sydney.a2t0303.service.RegistrationRequest;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

/** US13: registration details followed by an explicit member/admin selection. */
public final class AdminCreateUserCli {
    private final AdminAccountCreationService service;
    private final Scanner input;
    private final PrintStream output;

    public AdminCreateUserCli(AdminAccountCreationService service,
                              Scanner input, PrintStream output) {
        this.service = Objects.requireNonNull(service, "service");
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
    }

    public Optional<User> create() {
        try {
            // Deny access before showing the form or asking for personal details.
            service.requireAdmin();
            output.println("=== Create User Account ===");
            output.println("All fields are required. ID key cannot be changed after creation.");
            output.println("ID key: 3-20 letters, digits, - or _. Phone number: 8-15 digits.");

            String username = ask("Username: ", service::checkUsername);
            String password = username == null ? null : ask("Password: ", service::checkPassword);
            String fullName = password == null ? null : ask("Full name: ", service::checkFullName);
            String email = fullName == null ? null : ask("Email: ", service::checkEmail);
            String phone = email == null ? null : ask("Phone number: ", service::checkPhone);
            String idKey = phone == null ? null : ask("ID key: ", service::checkIdKey);
            Role role = idKey == null ? null : chooseRole();

            if (role == null) {
                output.println("Account creation cancelled. No account was saved.");
                return Optional.empty();
            }

            User user = service.createAccount(new RegistrationRequest(
                    username, password, fullName, email, phone, idKey), role);
            output.println("Account created successfully. Username: " + user.username()
                    + " | ID key: " + user.id()
                    + " | Type: " + (user.isAdmin() ? "Admin" : "User"));
            output.println("The new account can log in immediately.");
            return Optional.of(user);
        } catch (SecurityException e) {
            output.println(e.getMessage());
        } catch (IllegalArgumentException e) {
            output.println("Account creation failed: " + e.getMessage());
        } catch (IOException e) {
            output.println("Account creation failed: user data could not be read or saved. "
                    + "Please try again.");
        }
        return Optional.empty();
    }

    private Role chooseRole() {
        while (true) {
            output.println("Account type:");
            output.println("1. General user");
            output.println("2. Administrator");
            output.println("0. Cancel creation");
            String choice = prompt("Choose account type: ");
            if (choice == null || choice.strip().equals("0")) {
                return null;
            }
            switch (choice.strip()) {
                case "1" -> { return Role.MEMBER; }
                case "2" -> { return Role.ADMIN; }
                default -> output.println("Invalid account type. Please choose 0, 1 or 2.");
            }
        }
    }

    @FunctionalInterface
    private interface FieldCheck {
        String check(String value) throws IOException;
    }

    private String ask(String message, FieldCheck check) throws IOException {
        while (true) {
            String value = prompt(message);
            if (value == null) {
                return null;
            }
            try {
                return check.check(value);
            } catch (IllegalArgumentException e) {
                output.println(e.getMessage() + " Please try again.");
            }
        }
    }

    private String prompt(String message) {
        output.print(message);
        output.flush();
        return input.hasNextLine() ? input.nextLine() : null;
    }
}
