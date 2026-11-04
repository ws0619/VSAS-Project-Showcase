package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.AuthenticationService;
import au.edu.sydney.a2t0303.service.ProfileService;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;
import java.util.Scanner;

/** Command-line screen for US07: view and update the logged-in user's profile and password. */
public final class ProfileCli {

    private final ProfileService service;
    private final AuthenticationService authentication;
    private final Scanner input;
    private final PrintStream output;

    public ProfileCli(ProfileService service, AuthenticationService authentication,
            Scanner input, PrintStream output) {
        this.service = Objects.requireNonNull(service, "service");
        this.authentication = Objects.requireNonNull(authentication, "authentication");
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
    }

    /** Shows the user's details and runs one profile action, then returns to the main menu. */
    public void open(User user) {
        if (user == null) {
            output.println("Please log in to update your profile.");
            return;
        }

        output.println("===== My Profile =====");
        output.println("ID key: " + user.id() + " (cannot be changed)");
        output.println("Username: " + user.username());
        output.println("Full name: " + user.fullName());
        output.println("Email: " + user.email());
        output.println("Phone number: " + user.phone());
        output.println("1. Edit my details");
        output.println("2. Change my password");
        output.println("0. Back");

        String choice = prompt("Choose an option: ");
        if (choice == null) {
            output.println("Profile update cancelled.");
            return;
        }

        switch (choice.strip()) {
            case "1" -> editDetails(user);
            case "2" -> changePassword(user);
            case "0" -> { }
            default -> output.println("Invalid option. Please choose 0, 1 or 2.");
        }
    }

    private void editDetails(User user) {
        output.println("Press Enter to keep the current value.");

        String fullName = prompt("Full name [" + user.fullName() + "]: ");
        String email = fullName == null ? null : prompt("Email [" + user.email() + "]: ");
        String phone = email == null ? null : prompt("Phone number [" + user.phone() + "]: ");
        if (phone == null) {
            output.println("Profile update cancelled.");
            return;
        }

        if (fullName.isBlank() && email.isBlank() && phone.isBlank()) {
            output.println("No changes made.");
            return;
        }

        try {
            User updated = service.updateDetails(user.id(),
                    blankToNull(fullName), blankToNull(email), blankToNull(phone));
            authentication.refreshCurrentUser(updated);
            output.println("Profile updated successfully.");
        } catch (IllegalArgumentException e) {
            output.println("Profile update failed: " + e.getMessage());
        } catch (IOException e) {
            output.println("Profile update failed: user data could not be saved. Please try again.");
        }
    }

    private void changePassword(User user) {
        String current = prompt("Current password: ");
        String next = current == null ? null : prompt("New password: ");
        String confirm = next == null ? null : prompt("Confirm new password: ");
        if (confirm == null) {
            output.println("Profile update cancelled.");
            return;
        }

        if (!next.equals(confirm)) {
            output.println("Password change failed: the new passwords do not match.");
            return;
        }

        try {
            User updated = service.changePassword(user.id(), current, next);
            authentication.refreshCurrentUser(updated);
            output.println("Password changed successfully.");
        } catch (IllegalArgumentException e) {
            output.println("Password change failed: " + e.getMessage());
        } catch (IOException e) {
            output.println("Password change failed: user data could not be saved. Please try again.");
        }
    }

    private static String blankToNull(String value) {
        return value.isBlank() ? null : value;
    }

    private String prompt(String message) {
        output.print(message);
        output.flush();
        return input.hasNextLine() ? input.nextLine() : null;
    }
}
