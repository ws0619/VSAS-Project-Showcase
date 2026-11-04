
package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.service.ScrollFilter;
import au.edu.sydney.a2t0303.storage.ScrollRepository;

import java.io.IOException;
import java.io.PrintStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public final class ScrollBrowserCli {


    private static final DateTimeFormatter UPLOAD_DATE_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    public boolean hasScrolls() throws IOException {
    return !repository.findAll().isEmpty();
}

    private final ScrollRepository repository;

    public ScrollBrowserCli(ScrollRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void showAllScrolls(PrintStream out) throws IOException {
        showScrolls(out, ScrollFilter.NONE);
    }

    /** US15: lists only the scrolls that match the filter (all scrolls for ScrollFilter.NONE). */
    public void showScrolls(PrintStream out, ScrollFilter filter) throws IOException {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(filter, "filter");

        List<Scroll> scrolls = repository.findAll();

        if (scrolls.isEmpty()) {
            out.println("The library is currently empty. No scrolls are available yet.");
            return;
        }

        List<Scroll> shown = scrolls.stream().filter(filter::matches).toList();

        out.println("=== Available Scrolls ===");
        if (!filter.isEmpty()) {
            out.println("Filter: " + filter.describe() + " (" + shown.size() + " of "
                    + scrolls.size() + " scrolls)");
        }
        if (shown.isEmpty()) {
            out.println("No scrolls match the filter.");
            return;
        }

        out.printf("%-12s | %-24s | %-18s | %s%n",
                "Scroll ID", "Name", "Uploader", "Upload Date (UTC)");

        for (Scroll scroll : shown) {
            out.printf("%-12s | %-24s | %-18s | %s%n",
                    scroll.id(),
                    scroll.name(),
                    scroll.uploaderId(),
                    UPLOAD_DATE_FORMAT.format(ScrollFilter.latestUploadAt(scroll)));
        }
    }
}
