package au.edu.sydney.a2t0303.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Existing feature tests use application plaintext reads instead of inspecting ciphertext as text. */
public final class EncryptedStorageTestSupport {
    private EncryptedStorageTestSupport() {}

    public static void write(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        storage(file).migrateLegacyFile("scroll-files/" + file.getFileName(), null, null);
    }

    public static byte[] read(Path file) throws IOException {
        return storage(file).readAllBytes("scroll-files/" + file.getFileName(), Integer.MAX_VALUE - 8L);
    }

    public static String readString(Path file) throws IOException {
        return new String(read(file), StandardCharsets.UTF_8);
    }

    private static LocalScrollFileStorage storage(Path file) {
        return new LocalScrollFileStorage(file.toAbsolutePath().getParent().getParent());
    }
}
