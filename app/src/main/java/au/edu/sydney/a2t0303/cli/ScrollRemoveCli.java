package au.edu.sydney.a2t0303.cli;


import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.ScrollRemoveService;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;
import java.util.Scanner;

public final class ScrollRemoveCli {

    private final ScrollRemoveService service;
    private final Scanner input;
    private final PrintStream output;

    public ScrollRemoveCli(
            ScrollRemoveService service,
            Scanner input,
            PrintStream output) {

        this.service = Objects.requireNonNull(service);
        this.input = Objects.requireNonNull(input);
        this.output = Objects.requireNonNull(output);
    }

    public void remove(User currentUser) {

        if (currentUser == null) {
            output.println("Please log in to remove a scroll.");
            return;
        }

        output.println("===== Remove Scroll =====");

        String scrollId;
        Scroll target;

        // Validate the ID and permission before asking for confirmation
        while (true) {
            scrollId = prompt("Enter Scroll ID (Enter to cancel): ");

            if (scrollId == null || scrollId.isBlank()) {
                output.println("Removal cancelled.");
                return;
            }

            scrollId = scrollId.strip();

            try {
                target = service.validateRemovableScroll(currentUser, scrollId);
                break;
            } catch (IllegalArgumentException e) {
                output.println("Invalid Scroll ID: " + e.getMessage());
                output.println("Please enter another Scroll ID or press Enter to cancel.");
            } catch (IOException | SecurityException e) {
                output.println("Removal failed: " + e.getMessage());
                return;
            }
        }

        output.println(
                "Are you sure you want to remove scroll "
                        + scrollId + " (" + target.name() + ")?");

        String confirmation = prompt(
                "Type YES to confirm: ");

        // Only an explicit YES allows removal
        if (!"YES".equals(confirmation)) {
            output.println("Removal cancelled.");
            return;
        }

        try {
            Scroll removed = service.remove(
                    currentUser, scrollId);

            output.println("Scroll removed successfully!");
            output.println("Scroll ID: " + removed.id());
            output.println("Scroll Name: " + removed.name());

        } catch (IOException |
                 IllegalArgumentException |
                 SecurityException e) {

            output.println(
                    "Removal failed: " + e.getMessage());
        }
    }

    private String prompt(String message) {
        output.print(message);
        output.flush();

        return input.hasNextLine()
                ? input.nextLine()
                : null;
    }
}
