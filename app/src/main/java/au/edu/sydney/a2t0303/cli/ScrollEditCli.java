package au.edu.sydney.a2t0303.cli;


import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.ScrollEditService;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Scanner;

public final class ScrollEditCli {

    private final ScrollEditService service;
    private final Scanner input;
    private final PrintStream output;

    public ScrollEditCli(
            ScrollEditService service,
            Scanner input,
            PrintStream output) {

        this.service = Objects.requireNonNull(service);
        this.input = Objects.requireNonNull(input);
        this.output = Objects.requireNonNull(output);
    }

    public void edit(User currentUser) {

        if (currentUser == null) {
            output.println("Please log in to edit a scroll.");
            return;
        }

        output.println("===== Edit Scroll =====");

        String scrollId;

        // Validate Scroll ID before asking for edit details
        while (true) {

            scrollId = prompt(
                    "Enter Scroll ID (Enter to cancel): ");

            if (scrollId == null || scrollId.isBlank()) {
                output.println("Edit cancelled.");
                return;
            }

            scrollId = scrollId.strip();

            try {
                service.validateEditableScroll(currentUser, scrollId);
                break;

            } catch (IllegalArgumentException e) {

                output.println(
                        "Invalid Scroll ID: " + e.getMessage());

                output.println(
                        "Please enter another Scroll ID or cancel.");

            } catch (SecurityException e) {

                output.println("Edit failed: " + e.getMessage());
                return;

            } catch (IOException e) {

                output.println("Edit failed: " + e.getMessage());
                return;
            }
        }

        String newName = prompt(
                "New name (leave blank to keep current): ");

        if (newName == null) {
            output.println("Edit cancelled.");
            return;
        }

        String filePath = prompt(
                "Replacement file path (leave blank to keep current): ");

        if (filePath == null) {
            output.println("Edit cancelled.");
            return;
        }

        try {
            // Blank name means keep the existing name
            String targetName = newName.isBlank()
                    ? null
                    : newName.strip();

            // Blank file path means keep the existing file
            Path replacement = filePath.isBlank()
                    ? null
                    : Path.of(filePath.strip());

            Scroll updated = service.edit(
                    currentUser,
                    scrollId.strip(),
                    targetName,
                    replacement
            );

            output.println("Scroll updated successfully!");
            output.println("Scroll ID: " + updated.id());
            output.println("Scroll Name: " + updated.name());

        } catch (IOException |
                 IllegalArgumentException |
                 SecurityException e) {

            output.println("Edit failed: " + e.getMessage());
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

