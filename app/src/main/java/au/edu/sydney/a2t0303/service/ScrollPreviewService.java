
package au.edu.sydney.a2t0303.service;

import au.edu.sydney.a2t0303.model.Scroll;
import au.edu.sydney.a2t0303.storage.ScrollRepository;
import au.edu.sydney.a2t0303.storage.LocalScrollFileStorage;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public class ScrollPreviewService {

    private static final int MAX_TEXT_LINES = 10;
    private static final int MAX_BINARY_BYTES = 32;

    // Resource limits for safe previewing
    private static final long MAX_PREVIEW_FILE_BYTES =
            5L * 1024 * 1024;

    private static final int MAX_LINE_CHARS = 500;

    private final ScrollRepository repository;
    private final LocalScrollFileStorage fileStorage;

    public record PreviewResult(
            String scrollId,
            String scrollName,
            boolean textFile,
            List<String> textLines,
            long sizeBytes,
            String hexPreview
    ) {}

    public ScrollPreviewService(
            ScrollRepository repository,
            Path dataDirectory) {

        this.repository =
                Objects.requireNonNull(repository);

        this.fileStorage = new LocalScrollFileStorage(Objects.requireNonNull(dataDirectory));
    }

    public PreviewResult preview(String scrollId)
            throws IOException {

        // 1. Find scroll metadata
        Scroll scroll = repository.findById(scrollId)
                .orElseThrow(() ->
                        new IOException(
                                "Scroll not found: " + scrollId));

        // Authenticate and decrypt within the existing preview size limit.
        byte[] content = fileStorage.readAllBytes(scroll.storagePath(), MAX_PREVIEW_FILE_BYTES);
        long sizeBytes = content.length;

        // 4. Text preview
        if (isUtf8Text(content)) {

            List<String> lines = readFirstTenLines(content);

            return new PreviewResult(
                    scroll.id(),
                    scroll.name(),
                    true,
                    lines,
                    sizeBytes,
                    ""
            );
        }

        // 5. Binary preview
        byte[] bytes;

        try (InputStream input = new ByteArrayInputStream(content)) {
            bytes = input.readNBytes(MAX_BINARY_BYTES);
        }

        String hex = HexFormat.ofDelimiter(" ")
                .withUpperCase()
                .formatHex(bytes);

        return new PreviewResult(
                scroll.id(),
                scroll.name(),
                false,
                List.of(),
                sizeBytes,
                hex
        );
    }

    /**
     * Validates the authenticated, size-bounded plaintext as UTF-8.
     *
     * The size limit is checked before calling this.
     */
    private boolean isUtf8Text(byte[] content) throws IOException {

        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try (Reader reader =
                     new java.io.InputStreamReader(
                             new ByteArrayInputStream(content),
                             decoder)) {

            char[] buffer = new char[4096];
            int count;

            while ((count = reader.read(buffer)) != -1) {

                for (int i = 0; i < count; i++) {

                    if (buffer[i] == '\0') {
                        return false;
                    }
                }
            }

            return true;

        } catch (CharacterCodingException e) {
            return false;
        }
    }

    /**
     * Reads up to 10 lines, limiting the stored
     * characters in each line.
     *
     * Reads character by character to avoid
     * allocating an unlimited string via readLine().
     */
    private List<String> readFirstTenLines(byte[] content)
            throws IOException {

        List<String> lines = new ArrayList<>();

        try (Reader reader = new java.io.InputStreamReader(
                new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {

            StringBuilder line = new StringBuilder();
            boolean truncated = false;
            boolean hasChars = false;
            boolean previousCR = false;

            int next;

            while (lines.size() < MAX_TEXT_LINES
                    && (next = reader.read()) != -1) {

                char ch = (char) next;

                // Treat CRLF as one line ending
                if (previousCR) {
                    previousCR = false;

                    if (ch == '\n') {
                        continue;
                    }
                }

                if (ch == '\n' || ch == '\r') {

                    lines.add(formatLine(line, truncated));

                    line.setLength(0);
                    truncated = false;
                    hasChars = false;

                    if (ch == '\r') {
                        previousCR = true;
                    }

                } else {

                    hasChars = true;

                    if (line.length() < MAX_LINE_CHARS) {
                        line.append(ch);
                    } else {
                        truncated = true;
                    }
                }
            }

            // Include a final line without a newline
            if (lines.size() < MAX_TEXT_LINES && hasChars) {
                lines.add(formatLine(line, truncated));
            }
        }

        return List.copyOf(lines);
    }

    private String formatLine(
            StringBuilder line,
            boolean truncated) {

        if (truncated) {
            return line + "... [line truncated]";
        }

        return line.toString();
    }
}
