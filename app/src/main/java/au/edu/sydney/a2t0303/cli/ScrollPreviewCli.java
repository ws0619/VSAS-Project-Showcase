
package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.service.ScrollPreviewService;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;

public final class ScrollPreviewCli {

    private final ScrollPreviewService previewService;

    public ScrollPreviewCli(
            ScrollPreviewService previewService) {

        this.previewService = Objects.requireNonNull(
                previewService, "previewService");
    }

    public void showPreview(
            String scrollId,
            PrintStream out) throws IOException {

        Objects.requireNonNull(out, "out");

        // Obtain the preview from the service
        ScrollPreviewService.PreviewResult result =
                previewService.preview(scrollId);

        out.println();
        out.println("=== Scroll Preview ===");
        out.println("Scroll ID: " + result.scrollId());
        out.println("Name: " + result.scrollName());

        if (result.textFile()) {

            out.println("Type: Text");
            out.println("--- First 10 Lines ---");

            if (result.textLines().isEmpty()) {
                out.println("(Empty file)");
            } else {
                for (String line : result.textLines()) {
                    out.println(line);
                }
            }

        } else {

            out.println("Type: Binary");
            out.println("File size: "
                    + result.sizeBytes() + " bytes");

            out.println("--- First 32 Bytes (Hex) ---");
            out.println(result.hexPreview());
        }

        out.println("=== End of Preview ===");
    }
}
