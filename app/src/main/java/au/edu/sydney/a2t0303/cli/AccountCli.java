
package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.AuthenticationService;
import au.edu.sydney.a2t0303.service.ScrollFilter;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.io.Console;
import java.util.Arrays;

/** Start menu, authenticated main screen, and guest browsing. */
public final class AccountCli {

    private final AuthenticationService authentication;
    private final RegistrationCli registration;
    private final ScrollBrowserCli browser;
    private final ScrollUploadCli upload;
    private final ScrollEditCli editor;
    private final ScrollRemoveCli remover;
    private final ScrollPreviewCli preview;
    private final ScrollDownloadCli downloader;
    private final AdminCli admin;
    private final ProfileCli profile;
    private final ScrollVersionCli versions;
    private final Scanner input;
    private final PrintStream output;
    private final boolean securePasswordInput;

    // Original 6-parameter constructor
    public AccountCli(
            AuthenticationService authentication,
            RegistrationCli registration,
            ScrollBrowserCli browser,
            ScrollUploadCli upload,
            Scanner input,
            PrintStream output) {

        this(authentication, registration, browser, upload,
                null, null, null, null, null, null, input, output);
    }

    // Original 8-parameter constructor: edit and remove
    public AccountCli(
            AuthenticationService authentication,
            RegistrationCli registration,
            ScrollBrowserCli browser,
            ScrollUploadCli upload,
            ScrollEditCli editor,
            ScrollRemoveCli remover,
            Scanner input,
            PrintStream output) {

        this(authentication, registration, browser, upload,
                editor, remover, null, null, null, null, input, output);
    }

    // US10 constructor: preview
    public AccountCli(
            AuthenticationService authentication,
            RegistrationCli registration,
            ScrollBrowserCli browser,
            ScrollUploadCli upload,
            ScrollEditCli editor,
            ScrollRemoveCli remover,
            ScrollPreviewCli preview,
            Scanner input,
            PrintStream output) {

        this(authentication, registration, browser, upload,
                editor, remover, preview, null, null, null, input, output);
    }

    // US11 constructor: download
    public AccountCli(
            AuthenticationService authentication,
            RegistrationCli registration,
            ScrollBrowserCli browser,
            ScrollUploadCli upload,
            ScrollEditCli editor,
            ScrollRemoveCli remover,
            ScrollDownloadCli downloader,
            Scanner input,
            PrintStream output) {

        this(authentication, registration, browser, upload,
                editor, remover, null, downloader, null, null, input, output);
    }

    // US10 + US11 constructor: preview and download
    public AccountCli(
            AuthenticationService authentication,
            RegistrationCli registration,
            ScrollBrowserCli browser,
            ScrollUploadCli upload,
            ScrollEditCli editor,
            ScrollRemoveCli remover,
            ScrollPreviewCli preview,
            ScrollDownloadCli downloader,
            Scanner input,
            PrintStream output) {

        this(authentication, registration, browser, upload,
                editor, remover, preview, downloader, null, null, input, output);
    }

    // US12 constructor retained for existing callers and tests.
    public AccountCli(
            AuthenticationService authentication,
            RegistrationCli registration,
            ScrollBrowserCli browser,
            ScrollUploadCli upload,
            ScrollEditCli editor,
            ScrollRemoveCli remover,
            ScrollPreviewCli preview,
            ScrollDownloadCli downloader,
            AdminCli admin,
            Scanner input,
            PrintStream output) {

        this(authentication, registration, browser, upload,
                editor, remover, preview, downloader, admin, null, input, output);
    }

    // US07 constructor retained for existing callers and tests.
    public AccountCli(
            AuthenticationService authentication,
            RegistrationCli registration,
            ScrollBrowserCli browser,
            ScrollUploadCli upload,
            ScrollEditCli editor,
            ScrollRemoveCli remover,
            ScrollPreviewCli preview,
            ScrollDownloadCli downloader,
            ProfileCli profile,
            Scanner input,
            PrintStream output) {

        this(authentication, registration, browser, upload,
                editor, remover, preview, downloader, null, profile, input, output);
    }
// Backward-compatible constructor for existing callers and tests.
    public AccountCli(
        AuthenticationService authentication,
        RegistrationCli registration,
        ScrollBrowserCli browser,
        ScrollUploadCli upload,
        ScrollEditCli editor,
        ScrollRemoveCli remover,
        ScrollPreviewCli preview,
        ScrollDownloadCli downloader,
        AdminCli admin,
        ProfileCli profile,
        Scanner input,
        PrintStream output) {

    this(authentication, registration, browser, upload,
            editor, remover, preview, downloader,
            admin, profile, input, output, false);
        }

    // Full constructor: scroll features, US12 admin and US07 profile.
    // Optional features may be null and are then hidden from the menus.

    public AccountCli(
            AuthenticationService authentication,
            RegistrationCli registration,
            ScrollBrowserCli browser,
            ScrollUploadCli upload,
            ScrollEditCli editor,
            ScrollRemoveCli remover,
            ScrollPreviewCli preview,
            ScrollDownloadCli downloader,
            AdminCli admin,
            ProfileCli profile,
            Scanner input,
            PrintStream output,
            boolean securePasswordInput) {

        this(authentication, registration, browser, upload, editor, remover,
                preview, downloader, admin, profile, null, input, output, securePasswordInput);
    }

    // US18: version history is optional for backwards-compatible callers.
    public AccountCli(
            AuthenticationService authentication,
            RegistrationCli registration,
            ScrollBrowserCli browser,
            ScrollUploadCli upload,
            ScrollEditCli editor,
            ScrollRemoveCli remover,
            ScrollPreviewCli preview,
            ScrollDownloadCli downloader,
            AdminCli admin,
            ProfileCli profile,
            ScrollVersionCli versions,
            Scanner input,
            PrintStream output,
            boolean securePasswordInput) {

        this.authentication =
                Objects.requireNonNull(authentication, "authentication");
        this.registration =
                Objects.requireNonNull(registration, "registration");
        this.browser =
                Objects.requireNonNull(browser, "browser");
        this.upload =
                Objects.requireNonNull(upload, "upload");

        this.editor = editor;
        this.remover = remover;
        this.preview = preview;
        this.downloader = downloader;
        this.admin = admin;
        this.profile = profile;
        this.versions = versions;

        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
        this.securePasswordInput = securePasswordInput;
    }


    public void run() {
        try {
            while (true) {
                var current = authentication.currentUser();

                if (current.isPresent()) {
                    showMainMenu(current.get());
                } else if (authentication.isGuest()) {
                    showGuestMenu();
                } else {
                    output.println("=== VSAS Start Menu ===");
                    output.println("1. Register an account");
                    output.println("2. Log in");
                    output.println("3. Continue as guest");
                    output.println("0. Exit");
                }

                String choice = prompt("Choose an option: ");

                if (choice == null || choice.strip().equals("0")) {
                    output.println("Goodbye!");
                    return;
                }

                if (current.isPresent()) {
                    authenticatedAction(choice.strip(), current.get());
                } else if (authentication.isGuest()) {
                    guestAction(choice.strip());
                } else {
                    startAction(choice.strip());
                }
            }
        } finally {
            authentication.logout();
        }
    }

    private void showMainMenu(User user) {
        output.println("=== VSAS Main Menu ===");
        output.println("User: " + user.fullName()
                + " | Type: "
                + (user.isAdmin() ? "Admin" : "User"));

        output.println("1. View all scrolls");
        output.println("2. Upload a new scroll");
        output.println("3. Log out");

        if (editor != null) {
            output.println("4. Edit a scroll");
        }

        if (remover != null) {
            output.println("5. Remove a scroll");
        }

        if (profile != null) {
            output.println("6. Update my profile");
        }

        if (user.isAdmin() && admin != null) {
            output.println("7. Administrator functions");
        }

        if (versions != null) {
            output.println("8. View my scroll version history");
        }

        output.println("0. Exit");
    }

    private void startAction(String choice) {
        switch (choice) {
            case "1" -> registration.register();
            case "2" -> login();
            case "3" -> {
                authentication.enterGuest();
                output.println("You are browsing as a guest.");
            }
            default -> output.println(
                    "Invalid option. Please choose 0, 1, 2 or 3.");
        }
    }

    private void login() {
        String username = prompt("Username: ");

        if (username == null) {
            output.println("Login cancelled.");
            return;
        }

        String password = promptLoginPassword();
        if (password == null) {
            output.println("Login cancelled.");
            return;
        }

        try {
            if (authentication.login(username, password)) {
                output.println("Login successful.");
            } else {
                output.println(
                        "Login failed: incorrect username or password.");
            }
        } catch (SecurityException e) {
            output.println("Login failed: " + e.getMessage());
        } catch (IOException e) {
            output.println(
                    "Login failed: user data could not be read or saved. "
                    + "Please try again.");
        }
    }

    private void authenticatedAction(String choice, User user) {
        switch (choice) {
            case "1" -> showScrolls();
            case "2" -> upload.upload(user.id());

            case "3" -> {
                authentication.logout();
                output.println("You have logged out.");
            }

            case "4" -> {
                if (editor != null) {
                    editor.edit(user);
                } else {
                    invalidAuthenticatedOption();
                }
            }

            case "5" -> {
                if (remover != null) {
                    remover.remove(user);
                } else {
                    invalidAuthenticatedOption();
                }
            }

            case "6" -> {
                if (profile != null) {
                    profile.open(user);
                } else {
                    invalidAuthenticatedOption();
                }
            }

            case "7" -> {
                if (!user.isAdmin()) {
                    output.println("Only administrators can view all users.");
                } else if (admin != null) {
                    admin.run();
                } else {
                    invalidAuthenticatedOption();
                }
            }

            case "8" -> {
                if (versions != null) {
                    versions.showHistory(user);
                } else {
                    invalidAuthenticatedOption();
                }
            }

            default -> invalidAuthenticatedOption();
        }
    }

    // Lists only the currently visible options, including 7 for administrators.
    private void invalidAuthenticatedOption() {
        List<String> options = new ArrayList<>(List.of("0", "1", "2", "3"));
        if (editor != null) {
            options.add("4");
        }
        if (remover != null) {
            options.add("5");
        }
        if (profile != null) {
            options.add("6");
        }
        if (admin != null && authentication.currentUser().filter(User::isAdmin).isPresent()) {
            options.add("7");
        }
        if (versions != null) {
            options.add("8");
        }
        String last = options.remove(options.size() - 1);
        output.println("Invalid option. Please choose "
                + String.join(", ", options) + " or " + last + ".");
    }

    private void showGuestMenu() {
        output.println("=== VSAS Guest Menu ===");
        output.println("User: Guest | Type: Guest");
        output.println("1. View all scrolls");
        output.println("2. Upload a new scroll (login required)");
        output.println("3. Return to the start menu");
        output.println("4. Download a scroll (login required)");
        output.println("0. Exit");
    }

    private void guestAction(String choice) {
        try {
            switch (choice) {
                case "1" -> showScrolls();

                case "2" ->
                        authentication.requireUploadPermission();

                case "3" -> {
                    authentication.logout();
                    output.println(
                            "You have returned to the start menu.");
                }

                case "4" ->
                        authentication.requireDownloadPermission();

                default -> output.println(
                        "Invalid option. Please choose 0, 1, 2, 3 or 4.");
            }
        } catch (SecurityException e) {
            output.println(e.getMessage());
        }
    }

    // Shared Scroll List for US10, US11 and US15 (filter)
    private void showScrolls() {

        // US15: the active filter stays applied until cleared or the list is closed.
        ScrollFilter filter = ScrollFilter.NONE;

        while (true) {
            try {
                browser.showScrolls(output, filter);

                // Preserve browse-only and empty-library behavior
                if ((preview == null && downloader == null)
                        || !browser.hasScrolls()) {
                    return;
                }
            } catch (IOException e) {
                output.println(
                        "The scroll list could not be read. "
                        + "Please try again.");
                return;
            }

            // Preserve US10's original preview-only interaction
            if (preview != null && downloader == null) {
                previewSelectedScroll();
                return;
            }

            // Preserve US11's original guest browsing behavior
            // when only Download is configured
            if (preview == null && authentication.isGuest()) {
                return;
            }

            output.println();
            output.println("=== Scroll List Actions ===");

            if (preview != null) {
                output.println("1. Preview a scroll");
            }

            if (downloader != null) {
                output.println(
                        preview == null
                                ? "1. Download a scroll"
                                : "2. Download a scroll");
            }

            if (preview != null && downloader != null) {
                output.println("3. Filter scrolls");
                if (!filter.isEmpty()) {
                    output.println("4. Clear filter");
                }
            }

            output.println("0. Return to main menu");

            String choice = prompt("Choose an option: ");

            if (choice == null || choice.strip().equals("0")) {
                return;
            }

            switch (choice.strip()) {
                case "1" -> {
                    if (preview != null) {
                        previewSelectedScroll();
                    } else if (downloader != null) {
                        downloadSelectedScroll();
                    }
                }

                case "2" -> {
                    if (preview != null && downloader != null) {
                        downloadSelectedScroll();
                    } else {
                        output.println("Invalid option.");
                    }
                }

                case "3" -> {
                    if (preview != null && downloader != null) {
                        filter = askFilter(filter);
                    } else {
                        output.println("Invalid option.");
                    }
                }

                case "4" -> {
                    if (preview != null && downloader != null && !filter.isEmpty()) {
                        filter = ScrollFilter.NONE;
                        output.println("Filter cleared. Showing all scrolls.");
                    } else {
                        output.println("Invalid option.");
                    }
                }

                default ->
                        output.println("Invalid option.");
            }

            // After an action, show the same list again
        }
    }

    // US15: Enter skips a filter; an invalid date is asked again. Keeps the current filter if input ends.
    private ScrollFilter askFilter(ScrollFilter current) {
        output.println("=== Filter Scrolls ===");
        output.println("Press Enter to skip a filter. Filters can be combined.");
        String uploaderId = prompt("Uploader ID: ");
        String scrollId = uploaderId == null ? null : prompt("Scroll ID: ");
        String name = scrollId == null ? null : prompt("Name contains: ");
        if (name == null) {
            return current;
        }
        while (true) {
            String date = prompt("Upload date (YYYY-MM-DD): ");
            if (date == null) {
                return current;
            }
            try {
                ScrollFilter filter = ScrollFilter.of(uploaderId, scrollId, name, date);
                if (filter.isEmpty()) {
                    output.println("No filter entered. Showing all scrolls.");
                }
                return filter;
            } catch (IllegalArgumentException e) {
                output.println(e.getMessage() + " Please try again.");
            }
        }
    }

    private void previewSelectedScroll() {
        if (preview == null) {
            return;
        }

        String scrollId = prompt(
                "Enter Scroll ID to preview (Enter to return): ");

        if (scrollId == null || scrollId.isBlank()) {
            return;
        }

        try {
            preview.showPreview(scrollId.strip(), output);
        } catch (IOException e) {
            output.println(
                    "Unable to preview scroll: " + e.getMessage());
        }
    }

    private void downloadSelectedScroll() {
        if (downloader == null) {
            return;
        }

        try {
            // Guests must be rejected before any file prompts
            authentication.requireDownloadPermission();
            downloader.download();
        } catch (SecurityException e) {
            output.println(e.getMessage());
        }
    }

    private String prompt(String message) {
        output.print(message);
        output.flush();

        return input.hasNextLine() ? input.nextLine() : null;
    }


private String promptLoginPassword() {

    // Preserve scripted input for existing JUnit tests.
    if (!securePasswordInput) {
        return prompt("Password: ");
    }

    // Interactive mode must not echo the password.
    Console console = System.console();

    if (console == null) {
        output.println(
                "Secure password input is unavailable. "
                + "Please run VSAS in a supported terminal.");
        return null;
    }

    char[] password = console.readPassword("Password: ");

    if (password == null) {
        return null;
    }

    try {
        return new String(password);
    } finally {
        Arrays.fill(password, '\0');
    }
}

}
