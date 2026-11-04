package au.edu.sydney.a2t0303.model;

import java.time.Instant;

/** One immutable file revision, including the current revision. */
public record ScrollVersion(
        int versionNumber,
        Instant createdAt,
        String storagePath,
        String originalFileName,
        long sizeBytes,
        String sha256
) {}
