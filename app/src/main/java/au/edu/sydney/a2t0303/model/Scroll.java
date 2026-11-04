package au.edu.sydney.a2t0303.model;

import java.time.Instant;
import java.util.List;

public record Scroll(
        String id,
        String name,
        String uploaderId,
        Instant uploadedAt,
        Instant updatedAt,
        String storagePath,
        String originalFileName,
        long sizeBytes,
        String sha256,
        long downloadCount,
        List<ScrollVersion> versions
) {

    public Scroll {
        // Legacy JSON has no history. Its existing file becomes version 1.
        versions = versions == null || versions.isEmpty()
                ? List.of(new ScrollVersion(1, updatedAt, storagePath,
                        originalFileName, sizeBytes, sha256))
                : List.copyOf(versions);
    }

    public Scroll(
            String id, String name, String uploaderId,
            Instant uploadedAt, Instant updatedAt,
            String storagePath, String originalFileName,
            long sizeBytes, String sha256, long downloadCount
    ) {
        this(id, name, uploaderId, uploadedAt, updatedAt, storagePath,
                originalFileName, sizeBytes, sha256, downloadCount, null);
    }

    // Backward-compatible constructor
    public Scroll(
            String id,
            String name,
            String uploaderId,
            Instant uploadedAt,
            Instant updatedAt,
            String storagePath,
            String originalFileName,
            long sizeBytes,
            String sha256
    ) {
        this(
                id, name, uploaderId,
                uploadedAt, updatedAt,
                storagePath, originalFileName,
                sizeBytes, sha256, 0L
        );
    }
}
