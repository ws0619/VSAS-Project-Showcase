package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.Scroll;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * US15: filter criteria for the scroll list. A null criterion is not applied, and all
 * given criteria must match. IDs match exactly, the name matches any part ignoring case,
 * and the upload date matches one exact UTC day.
 */
public record ScrollFilter(String uploaderId, String scrollId, String nameContains,
        LocalDate uploadDate) {

    public static final ScrollFilter NONE = new ScrollFilter(null, null, null, null);

    /** Builds a filter from typed text; blank values mean "no filter" for that field. */
    public static ScrollFilter of(String uploaderId, String scrollId, String nameContains,
            String uploadDate) {
        String date = blankToNull(uploadDate);
        return new ScrollFilter(blankToNull(uploaderId), blankToNull(scrollId),
                blankToNull(nameContains), date == null ? null : parseDate(date));
    }

    /** @throws IllegalArgumentException if the text is not a valid YYYY-MM-DD date */
    public static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Upload date must be a valid date in YYYY-MM-DD format.");
        }
    }

    /**
     * The upload date shown and filtered on. When a file has been replaced (US08/US18), the
     * tutor confirmed at the Sprint 2 Review that this is the time of the latest replacement;
     * otherwise it is the original upload.
     */
    public static Instant latestUploadAt(Scroll scroll) {
        var versions = scroll.versions();
        return versions.size() > 1 ? versions.get(versions.size() - 1).createdAt() : scroll.uploadedAt();
    }

    public boolean isEmpty() {
        return uploaderId == null && scrollId == null && nameContains == null && uploadDate == null;
    }

    public boolean matches(Scroll scroll) {
        return (uploaderId == null || uploaderId.equals(scroll.uploaderId()))
                && (scrollId == null || scrollId.equals(scroll.id()))
                && (nameContains == null || scroll.name().toLowerCase(Locale.ROOT)
                        .contains(nameContains.toLowerCase(Locale.ROOT)))
                && (uploadDate == null
                        || uploadDate.equals(LocalDate.ofInstant(latestUploadAt(scroll), ZoneOffset.UTC)));
    }

    /** e.g. "uploader ID = U001, name contains "java"" */
    public String describe() {
        List<String> parts = new ArrayList<>();
        if (uploaderId != null) {
            parts.add("uploader ID = " + uploaderId);
        }
        if (scrollId != null) {
            parts.add("scroll ID = " + scrollId);
        }
        if (nameContains != null) {
            parts.add("name contains \"" + nameContains + "\"");
        }
        if (uploadDate != null) {
            parts.add("upload date = " + uploadDate);
        }
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
