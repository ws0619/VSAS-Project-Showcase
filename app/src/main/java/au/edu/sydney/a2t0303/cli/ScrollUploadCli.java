package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.service.ScrollUploadService;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Scanner;

public class ScrollUploadCli {

    private final ScrollUploadService service;
    private final Scanner input;
    private final PrintStream output;

    public ScrollUploadCli(
            ScrollUploadService service,
            Scanner input,
            PrintStream output) {

        this.service = service;
        this.input = input;
        this.output = output;
    }

    public void upload(String authenticatedUserId) {

        // Only authenticated users can upload
        if (authenticatedUserId == null ||
                authenticatedUserId.isBlank()) {
            output.println("Login is required to upload.");
            return;
        }

        output.println("===== Upload New Scroll =====");
        output.println("Each value is checked as soon as you enter it. Press Enter to cancel.");

        try {
            // Validate each value immediately and re-ask only the wrong one
            String scrollId = ask("Enter Scroll ID: ", service::checkScrollId);
            String scrollName = scrollId == null
                    ? null
                    : ask("Enter Scroll Name: ", service::checkScrollName);
            String filePath = scrollName == null
                    ? null
                    : ask("Enter File Path: ", service::checkFilePath);

            // Handle cancelled or incomplete input
            if (filePath == null) {
                output.println("Upload cancelled.");
                return;
            }

            var scroll = service.upload(
                    scrollId,
                    scrollName,
                    filePath,
                    authenticatedUserId
            );

            output.println("Scroll uploaded successfully!");
            output.println("Scroll ID: " + scroll.id());
            output.println("Scroll Name: " + scroll.name());

        } catch (IOException |
                 IllegalArgumentException |
                 SecurityException e) {

            output.println("Upload failed: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface InputCheck {
        void check(String value) throws IOException;
    }

    // Returns a valid, stripped value, or null when the user presses Enter or input ends.
    private String ask(String message, InputCheck check) throws IOException {
        while (true) {
            String value = prompt(message);
            if (value == null || value.isBlank()) {
                return null;
            }
            value = value.strip();
            try {
                check.check(value);
                return value;
            } catch (IllegalArgumentException e) {
                output.println(e.getMessage() + " Please try again or press Enter to cancel.");
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
