package au.edu.sydney.a2t0303.service;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ScrollUploadValidatorTest {

    @TempDir
    Path tempDir;

    private final ScrollUploadValidator validator =
        new ScrollUploadValidator();

    @Test
    void validFilePathIsAccepted() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello Scroll");

        Path result = validator.validateFilePath(
            file.toString()
        );

        assertEquals(file, result);
    }

    @Test
    void missingFileIsRejected() {
        Path missing = tempDir.resolve("missing.txt");

        assertThrows(IllegalArgumentException.class,
            () -> validator.validateFilePath(
                missing.toString()
            )
        );
    }

    @Test
    void emptyFilePathIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> validator.validateFilePath("")
        );
    }

    @Test
    void directoryIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> validator.validateFilePath(
                tempDir.toString()
            )
        );
    }

    @Test
    void duplicateIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> validator.validateScrollIdentity(
                "S001",
                "New Notes",
                Set.of("S001", "S002"),
                Set.of("Java Notes")
            )
        );
    }

    @Test
    void duplicateNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> validator.validateScrollIdentity(
                "S003",
                "Java Notes",
                Set.of("S001", "S002"),
                Set.of("Java Notes")
            )
        );
    }

    @Test
    void uniqueIdAndNameAreAccepted() {
        assertDoesNotThrow(
            () -> validator.validateScrollIdentity(
                "S003",
                "Math Notes",
                Set.of("S001", "S002"),
                Set.of("Java Notes")
            )
        );
    }

    @Test
    void emptyScrollIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> validator.validateScrollIdentity(
                "",
                "Math Notes",
                Set.of(),
                Set.of()
            )
        );
    }

    @Test
    void emptyScrollNameIsRejected() {

        assertThrows(IllegalArgumentException.class,
            () -> validator.validateScrollIdentity(
                "S003",
                "",
                Set.of("S001", "S002"),
                Set.of("Java Notes")
            )
        );
    }

    @Test
    void scrollIdIsCheckedOnItsOwn() {
        validator.validateScrollId("S002", Set.of("S001"));

        assertEquals("Scroll ID cannot be empty.", assertThrows(IllegalArgumentException.class,
                () -> validator.validateScrollId(" ", Set.of())).getMessage());
        assertEquals("Scroll ID already exists.", assertThrows(IllegalArgumentException.class,
                () -> validator.validateScrollId("S001", Set.of("S001"))).getMessage());
    }

    @Test
    void scrollNameIsCheckedOnItsOwn() {
        validator.validateScrollName("Python Notes", Set.of("Java Notes"));

        assertEquals("Scroll name cannot be empty.", assertThrows(IllegalArgumentException.class,
                () -> validator.validateScrollName("", Set.of())).getMessage());
        assertEquals("Scroll name already exists.", assertThrows(IllegalArgumentException.class,
                () -> validator.validateScrollName("Java Notes", Set.of("Java Notes"))).getMessage());
    }
}
