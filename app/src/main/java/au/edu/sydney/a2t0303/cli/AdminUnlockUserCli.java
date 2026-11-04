package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.service.AdminAccountUnlockService;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;
import java.util.Scanner;

public final class AdminUnlockUserCli {
    private final AdminAccountUnlockService service;
    private final Scanner input;
    private final PrintStream output;

    public AdminUnlockUserCli(AdminAccountUnlockService service, Scanner input, PrintStream output) {
        this.service = Objects.requireNonNull(service);
        this.input = Objects.requireNonNull(input);
        this.output = Objects.requireNonNull(output);
    }

    public void unlock() {
        try {
            var accounts = service.listLockedAccounts();
            output.println("=== Unlock User Account ===");
            if (accounts.isEmpty()) {
                output.println("No locked accounts.");
                return;
            }
            for (var account : accounts) {
                output.printf("User ID: %s | Username: %s | Failed attempts: %d%n",
                        account.id(), account.username(), account.failedAttempts());
            }
            output.print("Enter User ID to unlock (Enter to cancel): ");
            output.flush();
            if (!input.hasNextLine()) {
                output.println("Unlock cancelled.");
                return;
            }
            String id = input.nextLine().strip();
            if (id.isEmpty()) {
                output.println("Unlock cancelled.");
                return;
            }
            var user = service.unlock(id);
            output.println("Account unlocked successfully. User ID: " + user.id());
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            output.println("Unlock failed: " + e.getMessage());
        }
    }
}
