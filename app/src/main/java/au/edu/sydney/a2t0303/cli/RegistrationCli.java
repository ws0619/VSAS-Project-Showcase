package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.RegistrationRequest;
import au.edu.sydney.a2t0303.service.RegistrationService;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

/**
 * Command-line screen for US01: asks for the new user's details and shows the result.
 */
public class RegistrationCli {

    private final RegistrationService service;
    private final Scanner input;
    private final PrintStream output;

    public RegistrationCli(RegistrationService service, Scanner input, PrintStream output) {
        this.service = Objects.requireNonNull(service, "service");
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
    }

    /**
     * Runs the registration screen.
     *
     * @return the new user if registration succeeded, otherwise empty
     */
    public Optional<User> register() {
        output.println("===== Register New Account =====");
        output.println("All fields are required. Note: your ID key cannot be changed after registration.");
        output.println("ID key: 3-20 letters, digits, - or _. Phone number: 8-15 digits.");

        try {
            // Each field is checked as soon as it is typed; only a wrong field is asked again.
            String username = ask("Username: ", service::checkUsername);
            String password = username == null ? null : ask("Password: ", service::checkPassword);
            String fullName = password == null ? null : ask("Full name: ", service::checkFullName);
            String email = fullName == null ? null : ask("Email: ", service::checkEmail);
            String phone = email == null ? null : ask("Phone number: ", service::checkPhone);
            String idKey = phone == null ? null : ask("ID key: ", service::checkIdKey);

            // Input ended early (e.g. Ctrl+D)
            if (idKey == null) {
                output.println("Registration cancelled.");
                return Optional.empty();
            }

            User user = service.register(
                    new RegistrationRequest(username, password, fullName, email, phone, idKey));
            output.println("Registration successful! Welcome, " + user.fullName() + ".");
            output.println("Your ID key is " + user.id() + ". You can now log in as " + user.username() + ".");
            return Optional.of(user);
        } catch (IllegalArgumentException e) {
            output.println("Registration failed: " + e.getMessage());
        } catch (IOException e) {
            output.println("Registration failed: user data could not be saved. Please try again.");
        }
        return Optional.empty();
    }

    /** A check that returns the cleaned value or throws IllegalArgumentException. */
    @FunctionalInterface
    private interface FieldCheck {
        String check(String value) throws IOException;
    }

    /** Asks until the value passes its check; returns null if the input ends. */
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
        if (!input.hasNextLine()) {
            return null;
        }
        return input.nextLine();
    }
}
