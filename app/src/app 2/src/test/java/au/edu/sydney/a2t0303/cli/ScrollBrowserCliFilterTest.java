package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.ScrollVersion;
import au.edu.sydney.a2t0303.service.ScrollFilter;
import au.edu.sydney.a2t0303.storage.ScrollRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** US15: the scroll list honours a filter and shows the latest upload date. */
class ScrollBrowserCliFilterTest {

    private static final Instant OCT_20 = Instant.parse("2026-10-20T09:00:00Z");

    private final List<Scroll> stored = new ArrayList<>();
    private final ScrollRepository repository = new ScrollRepository() {
        @Override
        public List<Scroll> findAll() {
            return List.copyOf(stored);
        }

        @Override
        public void save(Scroll scroll) {
            stored.add(scroll);
        }
    };

    private static Scroll scroll(String id, String name, String uploader) {
        return new Scroll(id, name, uploader, OCT_20, OCT_20,
                "scroll-files/" + id + ".bin", id + ".txt", 3, "a".repeat(64));
    }

    private String show(ScrollFilter filter) throws IOException {
        var bytes = new ByteArrayOutputStream();
        new ScrollBrowserCli(repository).showScrolls(new PrintStream(bytes, true, StandardCharsets.UTF_8), filter);
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void onlyMatchingScrollsAreListedWithTheActiveFilter() throws IOException {
        stored.add(scroll("S001", "Java Notes", "U001"));
        stored.add(scroll("S002", "Python Guide", "U002"));

        String result = show(ScrollFilter.of("U001", null, null, null));

        assertTrue(result.contains("Filter: uploader ID = U001 (1 of 2 scrolls)"));
        assertTrue(result.contains("Java Notes"));
        assertFalse(result.contains("Python Guide"));
    }

    @Test
    void noMatchesGiveAClearMessage() throws IOException {
        stored.add(scroll("S001", "Java Notes", "U001"));

        String result = show(ScrollFilter.of(null, null, "cooking", null));

        assertTrue(result.contains("No scrolls match the filter."));
        assertFalse(result.contains("Java Notes"));
    }

    @Test
    void noFilterKeepsTheOriginalListing() throws IOException {
        stored.add(scroll("S001", "Java Notes", "U001"));

        String result = show(ScrollFilter.NONE);

        assertFalse(result.contains("Filter:"));
        assertTrue(result.contains("Java Notes"));
        assertTrue(result.contains("2026-10-20"));
    }

    @Test
    void replacedScrollShowsItsLatestUploadDate() throws IOException {
        Instant replaced = Instant.parse("2026-10-27T10:00:00Z");
        stored.add(new Scroll("S004", "Edited", "U001", OCT_20, replaced,
                "scroll-files/v2.bin", "v2.txt", 2, "b".repeat(64), 0, List.of(
                        new ScrollVersion(1, OCT_20, "scroll-files/v1.bin", "v1.txt", 1, "a".repeat(64)),
                        new ScrollVersion(2, replaced, "scroll-files/v2.bin", "v2.txt", 2, "b".repeat(64)))));

        String result = show(ScrollFilter.NONE);

        assertTrue(result.contains("2026-10-27"));
        assertFalse(result.contains("2026-10-20"));
    }
}
