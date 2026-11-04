package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.service.AdminUserDeletionService;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;
import java.util.Scanner;

/** US14 screen: choose a user by ID key, see what will be removed, then confirm with YES. */
public final class AdminDeleteUserCli {

    private final AdminUserDeletionService service;
    private final Scanner input;
    private final PrintStream output;

    public AdminDeleteUserCli(AdminUserDeletionService service, Scanner input, PrintStream output) {
        this.service = Objects.requireNonNull(service, "service");
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
    }

    public void delete() {
        try {
            service.requireAdmin();
            output.println("=== Delete a User ===");
            String idKey = prompt("Enter the ID key of the user to delete (Enter to cancel): ");
            if (idKey == null || idKey.isBlank()) {
                output.println("Deletion cancelled.");
                return;
            }

            var target = service.preview(idKey);
            var user = target.user();
            output.println("User: " + user.username() + " (" + user.fullName() + ") | ID key: "
                    + user.id() + " | Type: " + (user.isAdmin() ? "Admin" : "User"));
            output.println("Their " + target.scrolls().size()
                    + " uploaded scroll(s) will also be deleted.");

            String confirmation = prompt("Type YES to confirm: ");
            if (!"YES".equals(confirmation == null ? null : confirmation.strip())) {
                output.println("Deletion cancelled. No data was changed.");
                return;
            }

            var deleted = service.deleteUser(user.id());
            output.println("User " + deleted.user().username() + " deleted. "
                    + deleted.scrolls().size() + " scroll(s) removed.");
        } catch (SecurityException e) {
            output.println(e.getMessage());
        } catch (IllegalArgumentException e) {
            output.println("Deletion failed: " + e.getMessage());
        } catch (IOException e) {
            output.println("Deletion failed: user or scroll data could not be updated. "
                    + "Please try again.");
        }
    }

    private String prompt(String message) {
        output.print(message);
        output.flush();
        return input.hasNextLine() ? input.nextLine() : null;
    }
}
