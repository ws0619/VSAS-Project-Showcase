package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.storage.ScrollRepository;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * US16: administrator-only upload and download counts for every scroll.
 * Every upload and every file replacement adds a retained version (US18), so the
 * upload count is the number of versions; the download count is stored by US11.
 */
public final class AdminStatisticsService {

    public record ScrollStatistics(String scrollId, String name, String uploaderId,
            int uploadCount, long downloadCount) {
    }

    private final ScrollRepository scrolls;
    private final AuthenticationService authentication;

    public AdminStatisticsService(ScrollRepository scrolls, AuthenticationService authentication) {
        this.scrolls = Objects.requireNonNull(scrolls, "scrolls");
        this.authentication = Objects.requireNonNull(authentication, "authentication");
    }

    public void requireAdmin() {
        authentication.currentUser().filter(User::isAdmin)
                .orElseThrow(() -> new SecurityException(
                        "Only administrators can view scroll statistics."));
    }

    /** Checks the session before reading any scroll data; sorted by scroll ID. */
    public List<ScrollStatistics> statistics() throws IOException {
        requireAdmin();
        return scrolls.findAll().stream()
                .sorted(Comparator.comparing(Scroll::id))
                .map(scroll -> new ScrollStatistics(scroll.id(), scroll.name(),
                        scroll.uploaderId(), scroll.versions().size(), scroll.downloadCount()))
                .toList();
    }
}
