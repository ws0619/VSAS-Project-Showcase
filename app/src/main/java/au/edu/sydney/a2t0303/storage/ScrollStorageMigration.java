package au.edu.sydney.a2t0303.storage;

import au.edu.sydney.a2t0303.model.ScrollVersion;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;

/** Encrypts legacy current and archived files in place without rewriting catalog metadata. */
public final class ScrollStorageMigration {
    private final ScrollRepository repository;
    private final LocalScrollFileStorage storage;

    public ScrollStorageMigration(ScrollRepository repository, LocalScrollFileStorage storage) {
        this.repository = Objects.requireNonNull(repository);
        this.storage = Objects.requireNonNull(storage);
    }

    public int migrate() throws IOException {
        var paths = storage.storedPaths();
        if (paths.isEmpty()) return 0;
        Map<String, ScrollVersion> known = new HashMap<>();
        for (var scroll : repository.findAll()) {
            for (var version : scroll.versions()) known.put(version.storagePath(), version);
        }
        Set<String> legacy = new HashSet<>();
        for (String path : paths) {
            var metadata = known.get(path);
            if (storage.isLegacyFile(path, metadata == null ? null : metadata.sizeBytes(),
                    metadata == null ? null : metadata.sha256())) legacy.add(path);
        }
        if (!paths.isEmpty()) storage.prepareMigration(legacy);
        int migrated = 0;
        for (String path : paths) {
            var metadata = known.get(path);
            if (storage.migrateLegacyFile(path, metadata == null ? null : metadata.sizeBytes(),
                    metadata == null ? null : metadata.sha256())) migrated++;
        }
        return migrated;
    }
}
