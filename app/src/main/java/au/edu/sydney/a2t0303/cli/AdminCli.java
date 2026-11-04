package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.service.AdminUserService;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

/** Administrator submenu; main-menu option 7 is reserved for admin functionality. */
public final class AdminCli {
    private final AdminUserService service;
    private final AdminCreateUserCli creator;
    private final AdminDeleteUserCli deleter;
    private final AdminStatisticsCli statistics;
    private final AdminUnlockUserCli unlocker;
    private final Scanner input;
    private final PrintStream output;

    public AdminCli(AdminUserService service, Scanner input, PrintStream output) {
        this(service, null, input, output);
    }

    public AdminCli(AdminUserService service, AdminCreateUserCli creator,
                    Scanner input, PrintStream output) {
        this(service, creator, null, null, input, output);
    }

    /** Full constructor; optional screens may be null and are then hidden from the menu. */
    public AdminCli(AdminUserService service, AdminCreateUserCli creator,
                    AdminDeleteUserCli deleter, AdminStatisticsCli statistics,
                    Scanner input, PrintStream output) {
        this(service, creator, deleter, statistics, null, input, output);
    }

    public AdminCli(AdminUserService service, AdminCreateUserCli creator,
                    AdminDeleteUserCli deleter, AdminStatisticsCli statistics,
                    AdminUnlockUserCli unlocker, Scanner input, PrintStream output) {
        this.service = Objects.requireNonNull(service, "service");
        this.creator = creator;
        this.deleter = deleter;
        this.statistics = statistics;
        this.unlocker = unlocker;
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
    }

    public void run() {
        while (true) {
            try {
                service.requireAdmin();
            } catch (SecurityException e) {
                output.println(e.getMessage());
                return;
            }

            output.println("=== Administrator Functions ===");
            output.println("1. View all users");
            if (creator != null) {
                output.println("2. Create a user account");
            }
            if (deleter != null) {
                output.println("3. Delete a user");
            }
            if (statistics != null) {
                output.println("4. View scroll statistics");
            }
            if (unlocker != null) {
                output.println("5. Unlock a user account");
            }
            output.println("0. Return to main menu");
            output.print("Choose an option: ");
            output.flush();
            if (!input.hasNextLine()) {
                return;
            }

            switch (input.nextLine().strip()) {
                case "0" -> { return; }
                case "1" -> showAllUsers();
                case "2" -> {
                    if (creator != null) {
                        creator.create();
                    } else {
                        invalidOption();
                    }
                }
                case "3" -> {
                    if (deleter != null) {
                        deleter.delete();
                    } else {
                        invalidOption();
                    }
                }
                case "4" -> {
                    if (statistics != null) {
                        statistics.show();
                    } else {
                        invalidOption();
                    }
                }
                case "5" -> {
                    if (unlocker != null) unlocker.unlock();
                    else invalidOption();
                }
                default -> invalidOption();
            }
        }
    }

    // Lists only the options shown in the menu, e.g. "0, 1, 2, 3 or 4".
    private void invalidOption() {
        List<String> options = new ArrayList<>(List.of("0", "1"));
        if (creator != null) {
            options.add("2");
        }
        if (deleter != null) {
            options.add("3");
        }
        if (statistics != null) {
            options.add("4");
        }
        if (unlocker != null) options.add("5");
        String last = options.remove(options.size() - 1);
        output.println("Invalid option. Please choose " + String.join(", ", options) + " or " + last + ".");
    }

    /** Also enforces authorization when called directly rather than through the menu. */
    public void showAllUsers() {
        try {
            var users = service.listUsers();
            output.println("=== All Users ===");
            output.printf("%-18s | %-24s | %-28s | %-16s | %-16s | %s%n",
                    "Username", "Full name", "Email", "Phone", "ID key", "Type");
            for (var user : users) {
                output.printf("%-18s | %-24s | %-28s | %-16s | %-16s | %s%n",
                        user.username(), user.fullName(), user.email(),
                        user.phone(), user.idKey(), user.type());
            }
            output.println("Total users: " + users.size());
        } catch (SecurityException e) {
            output.println(e.getMessage());
        } catch (IOException e) {
            output.println("The user list could not be read. Please try again.");
        }
    }
}
