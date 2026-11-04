package au.edu.sydney.a2t0303;

import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.storage.JsonScrollCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

/** US15 through the real application: any user, including a guest, can filter and clear. */
class AppScrollFilterIntegrationTest {

    @TempDir
    Path directory;

    private void save(JsonScrollCatalog catalog, String id, String name, String uploader, String date)
            throws IOException {
        Instant at = Instant.parse(date + "T09:00:00Z");
        catalog.save(new Scroll(id, name, uploader, at, at, "scroll-files/" + id + ".bin",
                id + ".txt", 3, "a".repeat(64)));
    }

    @Test
    void guestFiltersCombinesRetriesAnInvalidDateAndClears() throws IOException {
        var catalog = new JsonScrollCatalog(directory.resolve("scrolls.json"));
        save(catalog, "S001", "Java Notes", "U001", "2026-10-20");
        save(catalog, "S002", "Java Advanced", "U002", "2026-10-21");
        save(catalog, "S003", "Python Guide", "U001", "2026-10-21");

        String typed = "3\n1\n"                           // guest, view all scrolls
                + "3\n\n\njava\n2026-21-10\n2026-10-21\n" // name + date, first date invalid
                + "4\n"                                   // clear filter
                + "3\nU001\n\n\n\n"                       // uploader only
                + "3\n\n\ncooking\n\n"                    // no matches
                + "0\n0\n";                               // back to guest menu, exit
        var bytes = new ByteArrayOutputStream();

        App.run(directory, new Scanner(typed), new PrintStream(bytes, true, StandardCharsets.UTF_8));
        String output = bytes.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("3. Filter scrolls"));
        assertTrue(output.contains("Upload date must be a valid date in YYYY-MM-DD format. Please try again."));
        assertTrue(output.contains("Filter: name contains \"java\", upload date = 2026-10-21 (1 of 3 scrolls)"));
        assertTrue(output.contains("4. Clear filter"));
        assertTrue(output.contains("Filter cleared. Showing all scrolls."));
        assertTrue(output.contains("Filter: uploader ID = U001 (2 of 3 scrolls)"));
        assertTrue(output.contains("No scrolls match the filter."));
        assertEquals(3, new JsonScrollCatalog(directory.resolve("scrolls.json")).findAll().size());
    }
}
