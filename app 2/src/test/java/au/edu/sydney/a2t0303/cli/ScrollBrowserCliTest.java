
package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.storage.ScrollRepository;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScrollBrowserCliTest {

    // Create a sample scroll for testing
    private Scroll createScroll() {
        Instant timestamp = Instant.parse("2026-10-13T10:00:00Z");

        return new Scroll(
                "S001",
                "Java Notes",
                "U001",
                timestamp,
                timestamp,
                "S001.bin",
                "notes.txt",
                100L,
                "sample-hash"
        );
    }

    // Fake repository to avoid accessing real JSON files
    private ScrollRepository createRepository(List<Scroll> scrolls) {
        return new ScrollRepository() {

            @Override
            public List<Scroll> findAll() {
                return scrolls;
            }

            @Override
            public void save(Scroll scroll) {
                throw new UnsupportedOperationException();
            }
        };
    }

    // Capture the CLI output
    private String runBrowser(List<Scroll> scrolls)
            throws IOException {

        ScrollBrowserCli browser =
                new ScrollBrowserCli(createRepository(scrolls));

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(
                output, true, StandardCharsets.UTF_8)) {
            browser.showAllScrolls(out);
        }

        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void testViewAllScrolls() throws IOException {

        String result = runBrowser(List.of(createScroll()));

        assertTrue(result.contains("S001"));
        assertTrue(result.contains("Java Notes"));
        assertTrue(result.contains("U001"));
        assertTrue(result.contains("2026-10-13"));
    }

    @Test
    void testEmptyScrollLibrary() throws IOException {

        String result = runBrowser(List.of());

        assertTrue(result.contains(
                "The library is currently empty"));

        assertFalse(result.contains("Scroll ID"));
    }

    @Test
    void testMultipleScrolls() throws IOException {

        Scroll first = createScroll();

        Scroll second = new Scroll(
                "S002",
                "Python Notes",
                "U002",
                Instant.parse("2026-10-12T10:00:00Z"),
                Instant.parse("2026-10-12T10:00:00Z"),
                "S002.bin",
                "python.txt",
                200L,
                "another-hash"
        );

        String result = runBrowser(List.of(first, second));

        assertTrue(result.contains("S001"));
        assertTrue(result.contains("S002"));
        assertTrue(result.contains("Java Notes"));
        assertTrue(result.contains("Python Notes"));
    }

    @Test
    void testRepositoryReadFailure() {

        ScrollRepository failingRepository = new ScrollRepository() {

            @Override
            public List<Scroll> findAll() throws IOException {
                throw new IOException("Cannot read scroll catalog");
            }

            @Override
            public void save(Scroll scroll) {
                throw new UnsupportedOperationException();
            }
        };

        ScrollBrowserCli browser =
                new ScrollBrowserCli(failingRepository);

        IOException exception = assertThrows(
                IOException.class,
                () -> browser.showAllScrolls(System.out)
        );

        assertEquals(
                "Cannot read scroll catalog",
                exception.getMessage()
        );
    }
}
