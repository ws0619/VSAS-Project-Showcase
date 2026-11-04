package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.ScrollVersionService;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;
import java.util.Scanner;

public final class ScrollVersionCli {
    private final ScrollVersionService service;
    private final Scanner input;
    private final PrintStream output;

    public ScrollVersionCli(ScrollVersionService service, Scanner input, PrintStream output) {
        this.service = Objects.requireNonNull(service);
        this.input = Objects.requireNonNull(input);
        this.output = Objects.requireNonNull(output);
    }

    public void showHistory(User user) {
        if (user == null) {
            output.println("Please log in to view scroll version history.");
            return;
        }
        output.println("===== Scroll Version History =====");
        output.print("Enter Scroll ID (Enter to return): ");
        output.flush();
        if (!input.hasNextLine()) {
            return;
        }
        String scrollId = input.nextLine().strip();
        if (scrollId.isBlank()) {
            return;
        }
        try {
            var versions = service.listVersions(user, scrollId);
            output.println("Scroll ID: " + scrollId);
            output.println("Dates are in UTC. Versions are listed oldest first.");
            for (var version : versions) {
                output.printf("Version %d%s | %s | %s | %d bytes%n",
                        version.versionNumber(),
                        version.versionNumber() == versions.size() ? " (current)" : "",
                        version.createdAt(), version.originalFileName(), version.sizeBytes());
            }
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            output.println("Unable to view version history: " + e.getMessage());
        }
    }
}
