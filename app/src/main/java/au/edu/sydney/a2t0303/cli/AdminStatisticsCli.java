package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.service.AdminStatisticsService;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;

/** US16 screen: upload and download counts for every scroll. */
public final class AdminStatisticsCli {

    private static final String ROW = "%-12s | %-24s | %-12s | %7s | %9s%n";

    private final AdminStatisticsService service;
    private final PrintStream output;

    public AdminStatisticsCli(AdminStatisticsService service, PrintStream output) {
        this.service = Objects.requireNonNull(service, "service");
        this.output = Objects.requireNonNull(output, "output");
    }

    public void show() {
        try {
            var rows = service.statistics();
            output.println("=== Scroll Statistics ===");
            if (rows.isEmpty()) {
                output.println("No scrolls have been uploaded yet.");
                return;
            }
            output.printf(ROW, "Scroll ID", "Name", "Uploader", "Uploads", "Downloads");
            long uploads = 0;
            long downloads = 0;
            for (var row : rows) {
                output.printf(ROW, row.scrollId(), row.name(), row.uploaderId(),
                        row.uploadCount(), row.downloadCount());
                uploads += row.uploadCount();
                downloads += row.downloadCount();
            }
            output.println("Total: " + rows.size() + " scroll(s), " + uploads + " upload(s), "
                    + downloads + " download(s).");
        } catch (SecurityException e) {
            output.println(e.getMessage());
        } catch (IOException e) {
            output.println("Scroll statistics could not be read. Please try again.");
        }
    }
}
