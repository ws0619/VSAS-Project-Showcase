package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.model.ScrollVersion;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScrollFilterTest {

    private static final Instant OCT_20 = Instant.parse("2026-10-20T09:00:00Z");

    private static Scroll scroll(String id, String name, String uploader, Instant uploadedAt) {
        return new Scroll(id, name, uploader, uploadedAt, uploadedAt,
                "scroll-files/" + id + ".bin", id + ".txt", 3, "a".repeat(64));
    }

    private final Scroll javaNotes = scroll("S001", "Java Notes", "U001", OCT_20);
    private final Scroll pythonGuide = scroll("S002", "Python Guide", "U002", OCT_20);

    @Test
    void emptyFilterMatchesEverything() {
        assertTrue(ScrollFilter.NONE.isEmpty());
        assertTrue(ScrollFilter.NONE.matches(javaNotes));
        assertTrue(ScrollFilter.of(" ", "", null, "  ").isEmpty());
    }

    @Test
    void eachCriterionWorksOnItsOwn() {
        assertTrue(ScrollFilter.of("U001", null, null, null).matches(javaNotes));
        assertFalse(ScrollFilter.of("U001", null, null, null).matches(pythonGuide));

        assertTrue(ScrollFilter.of(null, "S002", null, null).matches(pythonGuide));
        assertFalse(ScrollFilter.of(null, "S002", null, null).matches(javaNotes));

        assertTrue(ScrollFilter.of(null, null, "java", null).matches(javaNotes));
        assertFalse(ScrollFilter.of(null, null, "java", null).matches(pythonGuide));

        assertTrue(ScrollFilter.of(null, null, null, "2026-10-20").matches(javaNotes));
        assertFalse(ScrollFilter.of(null, null, null, "2026-10-21").matches(javaNotes));
    }

    @Test
    void criteriaCanBeCombined() {
        assertTrue(ScrollFilter.of("U001", null, "notes", "2026-10-20").matches(javaNotes));
        assertFalse(ScrollFilter.of("U001", null, "guide", "2026-10-20").matches(javaNotes));
        assertFalse(ScrollFilter.of("U002", "S001", null, null).matches(javaNotes));
    }

    @Test
    void namesMatchAnyPartIgnoringCaseButIdsMustMatchExactly() {
        assertTrue(ScrollFilter.of(null, null, "NOTE", null).matches(javaNotes));
        assertFalse(ScrollFilter.of("u001", null, null, null).matches(javaNotes));
        assertFalse(ScrollFilter.of(null, "S00", null, null).matches(javaNotes));
    }

    @Test
    void dateMatchesOneExactUtcDay() {
        Scroll lateEvening = scroll("S003", "Late", "U001", Instant.parse("2026-10-20T23:30:00Z"));

        assertTrue(ScrollFilter.of(null, null, null, "2026-10-20").matches(lateEvening));
        assertFalse(ScrollFilter.of(null, null, null, "2026-10-21").matches(lateEvening));
    }

    @Test
    void replacedScrollIsFilteredByItsLatestUploadDate() {
        Instant replaced = Instant.parse("2026-10-27T10:00:00Z");
        Scroll edited = new Scroll("S004", "Edited", "U001", OCT_20, replaced,
                "scroll-files/v2.bin", "v2.txt", 2, "b".repeat(64), 0, List.of(
                        new ScrollVersion(1, OCT_20, "scroll-files/v1.bin", "v1.txt", 1, "a".repeat(64)),
                        new ScrollVersion(2, replaced, "scroll-files/v2.bin", "v2.txt", 2, "b".repeat(64))));

        assertEquals(replaced, ScrollFilter.latestUploadAt(edited));
        assertEquals(OCT_20, ScrollFilter.latestUploadAt(javaNotes));
        assertTrue(ScrollFilter.of(null, null, null, "2026-10-27").matches(edited));
        assertFalse(ScrollFilter.of(null, null, null, "2026-10-20").matches(edited));
    }

    @Test
    void invalidDatesAreRejectedWithAClearMessage() {
        for (String bad : List.of("20-10-2026", "2026-13-01", "2026-02-30", "yesterday")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ScrollFilter.of(null, null, null, bad), bad);
            assertEquals("Upload date must be a valid date in YYYY-MM-DD format.", e.getMessage());
        }
        assertEquals(LocalDate.of(2026, 10, 20), ScrollFilter.parseDate(" 2026-10-20 "));
    }

    @Test
    void describeListsTheActiveCriteria() {
        assertEquals("uploader ID = U001, name contains \"java\", upload date = 2026-10-20",
                ScrollFilter.of("U001", null, "java", "2026-10-20").describe());
        assertEquals("none", ScrollFilter.NONE.describe());
    }
}
