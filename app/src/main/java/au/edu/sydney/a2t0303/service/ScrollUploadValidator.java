package au.edu.sydney.a2t0303.service;

import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;

public class ScrollUploadValidator {

    public Path validateFilePath(String filePath) {

        // 1. Reject empty paths
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException(
                "File path cannot be empty."
            );
        }

        // 2. Convert the input to a Path
        Path path;
        try {
            path = Path.of(filePath);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException(
                "Invalid file path.", e
            );
        }

        // 3. Check whether the file exists
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                "File does not exist or is not a regular file."
            );
        }

        // 4. Check whether the file is readable
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException(
                "File cannot be read."
            );
        }

        return path;
    }

    /** Checks only the scroll ID, so it can be validated as soon as it is typed. */
    public void validateScrollId(String scrollId, Set<String> existingIds) {
        if (scrollId == null || scrollId.isBlank()) {
            throw new IllegalArgumentException(
                "Scroll ID cannot be empty."
            );
        }
        if (existingIds.contains(scrollId)) {
            throw new IllegalArgumentException(
                "Scroll ID already exists."
            );
        }
    }

    /** Checks only the scroll name, so it can be validated as soon as it is typed. */
    public void validateScrollName(String scrollName, Set<String> existingNames) {
        if (scrollName == null || scrollName.isBlank()) {
            throw new IllegalArgumentException(
                "Scroll name cannot be empty."
            );
        }
        if (existingNames.contains(scrollName)) {
            throw new IllegalArgumentException(
                "Scroll name already exists."
            );
        }
    }

    public void validateScrollIdentity(
            String scrollId,
            String scrollName,
            Set<String> existingIds,
            Set<String> existingNames) {

        // 1. Check empty ID
        if (scrollId == null || scrollId.isBlank()) {
            throw new IllegalArgumentException(
                "Scroll ID cannot be empty."
            );
        }

        // 2. Check empty name
        if (scrollName == null || scrollName.isBlank()) {
            throw new IllegalArgumentException(
                "Scroll name cannot be empty."
            );
        }

        // 3. Check duplicate ID
        if (existingIds.contains(scrollId)) {
            throw new IllegalArgumentException(
                "Scroll ID already exists."
            );
        }

        // 4. Check duplicate name
        if (existingNames.contains(scrollName)) {
            throw new IllegalArgumentException(
                "Scroll name already exists."
            );
        }
    }

}
