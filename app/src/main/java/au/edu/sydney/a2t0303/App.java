
package au.edu.sydney.a2t0303;

import au.edu.sydney.a2t0303.cli.AccountCli;
import au.edu.sydney.a2t0303.cli.AdminCli;
import au.edu.sydney.a2t0303.cli.AdminUnlockUserCli;
import au.edu.sydney.a2t0303.cli.AdminCreateUserCli;
import au.edu.sydney.a2t0303.cli.AdminDeleteUserCli;
import au.edu.sydney.a2t0303.cli.AdminStatisticsCli;
import au.edu.sydney.a2t0303.cli.RegistrationCli;
import au.edu.sydney.a2t0303.cli.ScrollBrowserCli;
import au.edu.sydney.a2t0303.cli.ScrollUploadCli;
import au.edu.sydney.a2t0303.cli.ScrollEditCli;
import au.edu.sydney.a2t0303.cli.ScrollVersionCli;
import au.edu.sydney.a2t0303.cli.ScrollRemoveCli;
import au.edu.sydney.a2t0303.cli.ScrollPreviewCli;
import au.edu.sydney.a2t0303.cli.ScrollDownloadCli;
import au.edu.sydney.a2t0303.cli.ProfileCli;

import au.edu.sydney.a2t0303.service.AuthenticationService;
import au.edu.sydney.a2t0303.service.AdminUserService;
import au.edu.sydney.a2t0303.service.AdminAccountUnlockService;
import au.edu.sydney.a2t0303.service.AdminUserDeletionService;
import au.edu.sydney.a2t0303.service.AdminStatisticsService;
import au.edu.sydney.a2t0303.service.AdminAccountCreationService;
import au.edu.sydney.a2t0303.service.DefaultAdminService;
import au.edu.sydney.a2t0303.service.PasswordHasher;
import au.edu.sydney.a2t0303.service.RegistrationService;
import au.edu.sydney.a2t0303.service.ScrollUploadService;
import au.edu.sydney.a2t0303.service.ScrollUploadValidator;
import au.edu.sydney.a2t0303.service.ScrollEditService;
import au.edu.sydney.a2t0303.service.ScrollVersionService;
import au.edu.sydney.a2t0303.service.ScrollRemoveService;
import au.edu.sydney.a2t0303.service.ScrollPreviewService;
import au.edu.sydney.a2t0303.service.ScrollDownloadService;
import au.edu.sydney.a2t0303.service.ProfileService;

import au.edu.sydney.a2t0303.storage.JsonScrollCatalog;
import au.edu.sydney.a2t0303.storage.JsonUserRepository;
import au.edu.sydney.a2t0303.storage.LocalScrollFileStorage;
import au.edu.sydney.a2t0303.storage.ScrollStorageMigration;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Scanner;

public class App {

    public String getGreeting() {
        return "Hello World!";
    }

    public static void main(String[] args) {

        if (args.length > 1) {
            System.out.println("Usage: VSAS [data-directory]");
            return;
        }

        try {
            Path data = Path.of(
                    args.length == 0 ? "data" : args[0]
            );

           run(data, new Scanner(System.in), System.out, true);

        } catch (IOException | InvalidPathException e) {
            System.out.println(
                    "VSAS could not open its data directory. "
                    + "Check the stored files and permissions."
            );
            System.out.println(e.getMessage());
        }
    }

    /**
     * Streams and data paths are injectable
     * so application journeys can be tested.
     */
  
/**
 * Test-compatible entry point.
 * Keeps scripted Scanner input for existing integration tests.
 */
        public static void run(
                Path data,
                Scanner input,
                PrintStream output) throws IOException {

        run(data, input, output, false);
        }

        /**
         * Starts VSAS with optional secure password input.
         */
        public static void run(
                Path data,
                Scanner input,
                PrintStream output,
                boolean securePasswordInput) throws IOException {


        // Initialize persistent repositories
        var users = new JsonUserRepository(
                data.resolve("users.json")
        );

        var scrolls = new JsonScrollCatalog(
                data.resolve("scrolls.json")
        );

        var hasher = new PasswordHasher();
        var clock = Clock.systemUTC();

        // US12: initialise the documented default admin without resetting existing data.
        try {
            new DefaultAdminService(users, hasher, clock).createIfMissing();
        } catch (IOException e) {
            output.println("The default admin account could not be initialised. "
                    + "Check users.json for invalid data, conflicting accounts or permissions.");
        }

        // Authentication
        var authentication =
                new AuthenticationService(users, hasher);

        // Registration
        var registrationService = new RegistrationService(users, hasher, clock);
        var registration = new RegistrationCli(
                registrationService,
                input,
                output
        );

        // US13 extends US12's existing administrator submenu; no main-menu renumbering.
        var creator = new AdminCreateUserCli(
                new AdminAccountCreationService(registrationService, authentication), input, output);

        // US05: View all scrolls
        var browser = new ScrollBrowserCli(scrolls);

        // US10: Preview scroll content
        var preview = new ScrollPreviewCli(
                new ScrollPreviewService(scrolls, data)
        );

        // Shared local file storage
        var fileStorage = new LocalScrollFileStorage(data);
        new ScrollStorageMigration(scrolls, fileStorage).migrate();

        // US11: Download scrolls
        var downloader = new ScrollDownloadCli(
                new ScrollDownloadService(
                        scrolls,
                        authentication,
                        data
                ),
                input,
                output
        );

        // US04: Upload scroll
        var uploader = new ScrollUploadCli(
                new ScrollUploadService(
                        new ScrollUploadValidator(),
                        fileStorage,
                        scrolls,
                        clock,
                        authentication
                ),
                input,
                output
        );

        // US08: Edit scroll
        var editor = new ScrollEditCli(
                new ScrollEditService(scrolls, fileStorage, clock),
                input,
                output
        );

        // US09: Remove scroll
        var remover = new ScrollRemoveCli(
                new ScrollRemoveService(scrolls, fileStorage),
                input,
                output
        );

        // US12-US14, US16, US20: administrator tools, including account unlock.
        var admin = new AdminCli(
                new AdminUserService(users, authentication),
                creator,
                new AdminDeleteUserCli(
                        new AdminUserDeletionService(users, scrolls,
                                new ScrollRemoveService(scrolls, fileStorage), authentication),
                        input, output),
                new AdminStatisticsCli(new AdminStatisticsService(scrolls, authentication), output),
                new AdminUnlockUserCli(new AdminAccountUnlockService(users, authentication), input, output),
                input,
                output
        );

        // US18: list retained file versions and their dates.
        var versions = new ScrollVersionCli(new ScrollVersionService(scrolls), input, output);

        // US07: Update my profile
        var profile = new ProfileCli(
                new ProfileService(users, hasher, clock),
                authentication,
                input,
                output
        );

        // Unified application: US07 + US10 + US11
                
        new AccountCli(
                authentication,
                registration,
                browser,
                uploader,
                editor,
                remover,
                preview,
                downloader,
                admin,
                profile,
                versions,
                input,
                output,
                securePasswordInput
        ).run();

        }
}
