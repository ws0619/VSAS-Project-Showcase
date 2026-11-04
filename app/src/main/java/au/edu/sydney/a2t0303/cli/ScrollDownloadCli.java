
package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.service.ScrollDownloadService;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Scanner;
import java.nio.file.FileAlreadyExistsException;


public final class ScrollDownloadCli {

    private final ScrollDownloadService downloadService;
    private final Scanner input;
    private final PrintStream output;

    public ScrollDownloadCli(
            ScrollDownloadService downloadService,
            Scanner input,
            PrintStream output) {

        this.downloadService = Objects.requireNonNull(
                downloadService, "downloadService");

        this.input = Objects.requireNonNull(
                input, "input");

        this.output = Objects.requireNonNull(
                output, "output");
    }

    public void download() {

        output.println("=== Download a Scroll ===");

        // Step 1: Validate Scroll ID before requesting a folder
        String scrollId;

        while (true) {

            output.print("Enter Scroll ID (Enter to cancel): ");
            output.flush();

            if (!input.hasNextLine()) {
                output.println("Download cancelled.");
                return;
            }

            scrollId = input.nextLine().strip();

            if (scrollId.isEmpty()) {
                output.println("Download cancelled.");
                return;
            }

            try {
                // Validate ID existence and download permission
                downloadService.validateDownloadableScroll(scrollId);

                // Valid ID: proceed to destination folder
                break;

            } catch (IllegalArgumentException e) {

                output.println(
                        "Invalid Scroll ID: " + e.getMessage());

                output.println(
                        "Please enter another Scroll ID or cancel.");

            } catch (SecurityException e) {

                output.println(e.getMessage());
                return;

            } catch (IOException e) {

                output.println(
                        "Download could not be completed: "
                                + e.getMessage());
                return;
            }
        }

        // Step 2: Allow retrying the destination folder
        while (true) {

            output.print(
                    "Enter destination folder (Enter to cancel): ");
            output.flush();

            if (!input.hasNextLine()) {
                output.println("Download cancelled.");
                return;
            }

            String folderInput = input.nextLine().strip();

            if (folderInput.isEmpty()) {
                output.println("Download cancelled.");
                return;
            }

            try {
                // Step 3: Convert user input to a destination folder
                Path folder = Path.of(folderInput);

                // Step 4: Download using the original filename
                Path savedPath =
                        downloadService.downloadToFolder(scrollId, folder);

                // Step 5: Display the complete saved file path
                output.println(
                        "Scroll downloaded successfully to: "
                                + savedPath
                );

                return;

            } catch (
                    ScrollDownloadService.InvalidDestinationFolderException e) {

                output.println(e.getMessage());
                output.println(
                        "Please choose another folder or cancel.");

            } catch (FileAlreadyExistsException e) {

                output.println(
                        "Download could not be completed: "
                            + "Destination file already exists: "
                            + e.getMessage());

                output.println(
                        "Please choose another folder or cancel.");

            } catch (InvalidPathException e) {

                output.println("Invalid destination path.");
                output.println(
                        "Please choose another folder or cancel.");

            } catch (SecurityException e) {

                output.println(e.getMessage());
                return;

            } catch (IllegalArgumentException e) {

                output.println(
                        "Invalid download request: " + e.getMessage());
                return;

            } catch (
                    ScrollDownloadService.DownloadCountUpdateException e) {

                // The file was saved, but count update failed
                output.println(
                        "Scroll file downloaded successfully to: "
                                + e.savedPath()
                );

                output.println(
                        "Warning: The download count could not be updated.");

                return;

            } catch (IOException e) {

                output.println(
                        "Download could not be completed: "
                                + e.getMessage()
                );

                return;
            }
        }
    }
}

