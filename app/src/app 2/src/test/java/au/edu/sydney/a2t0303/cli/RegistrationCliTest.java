package au.edu.sydney.a2t0303.cli;

import au.edu.sydney.a2t0303.model.User;
import au.edu.sydney.a2t0303.service.PasswordHasher;
import au.edu.sydney.a2t0303.service.RegistrationService;
import au.edu.sydney.a2t0303.storage.UserRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationCliTest {

    private final List<User> stored = new ArrayList<>();

    // In-memory repository so tests never touch real files
    private final UserRepository repository = new UserRepository() {
        @Override
        public List<User> findAll() {
            return List.copyOf(stored);
        }

        @Override
        public void save(User user) {
            stored.add(user);
        }

        @Override
        public void deleteById(String id) {
            stored.removeIf(user -> user.id().equals(id));
        }
    };

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();

    private Optional<User> run(UserRepository repo, String typedInput) {
        RegistrationService service =
                new RegistrationService(repo, new PasswordHasher(1_000), Clock.systemUTC());
        Scanner in = new Scanner(typedInput);
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        return new RegistrationCli(service, in, out).register();
    }

    private String output() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private static final String ALICE =
            "alice\nsecret123\nAlice Example\nalice@example.org\n0400000000\nU001\n";

    @Test
    void successfulRegistrationShowsWelcome() {
        Optional<User> result = run(repository, ALICE);

        assertTrue(result.isPresent());
        assertEquals(1, stored.size());
        assertTrue(output().contains("Registration successful! Welcome, Alice Example."));
    }

    @Test
    void screenWarnsThatIdKeyCannotBeChanged() {
        run(repository, ALICE);
        assertTrue(output().contains("ID key cannot be changed"));
    }

    @Test
    void takenUsernameIsAskedAgain() {
        run(repository, ALICE);
        outBytes.reset();

        Optional<User> result = run(repository,
                "alice\nbob\nother456\nBob Example\nbob@example.org\n0411111111\nU002\n");

        assertTrue(output().contains("Username \"alice\" is already taken. Please try again."));
        assertEquals("bob", result.orElseThrow().username());
        assertEquals(2, stored.size());
    }

    @Test
    void missingFieldIsAskedAgainWithoutLosingEarlierAnswers() {
        Optional<User> result = run(repository,
                "alice\nsecret123\n\nAlice Example\nalice@example.org\n0400000000\nU001\n");

        assertTrue(output().contains("Full name is required. Please try again."));
        User user = result.orElseThrow();
        assertEquals("alice", user.username());
        assertEquals("Alice Example", user.fullName());
    }

    @Test
    void badlyFormattedFieldsAreAskedAgainOneAtATime() {
        Optional<User> result = run(repository, "alice\nsecret123\nAlice Example\n"
                + "not-an-email\nalice@example.org\n"
                + "123\n0400 000 000\n"
                + "A\nU001\n");

        assertTrue(output().contains("Email must look like name@example.com. Please try again."));
        assertTrue(output().contains("Phone number must have 8 to 15 digits"));
        assertTrue(output().contains("ID key must be 3 to 20 characters"));
        User user = result.orElseThrow();
        assertEquals("alice@example.org", user.email());
        assertEquals("0400000000", user.phone());
        assertEquals("U001", user.id());
        assertEquals(1, stored.size());
    }

    @Test
    void takenIdKeyIsAskedAgain() {
        run(repository, ALICE);
        outBytes.reset();

        Optional<User> result = run(repository,
                "bob\nother456\nBob Example\nbob@example.org\n0411111111\nU001\nU002\n");

        assertTrue(output().contains("ID key \"U001\" is already taken. Please try again."));
        assertEquals("U002", result.orElseThrow().id());
    }

    @Test
    void inputEndingWhileRetryingCancelsRegistration() {
        Optional<User> result = run(repository, "alice\nsecret123\nAlice Example\nbad-email\n");

        assertTrue(result.isEmpty());
        assertTrue(output().contains("Registration cancelled."));
        assertTrue(stored.isEmpty());
    }

    @Test
    void inputEndingEarlyCancelsRegistration() {
        Optional<User> result = run(repository, "alice\nsecret123\n");

        assertTrue(result.isEmpty());
        assertTrue(output().contains("Registration cancelled."));
        assertTrue(stored.isEmpty());
    }

    @Test
    void storageFailureShowsFriendlyMessage() {
        UserRepository failing = new UserRepository() {
            @Override
            public List<User> findAll() throws IOException {
                throw new IOException("disk error");
            }

            @Override
            public void save(User user) {
            }

            @Override
            public void deleteById(String id) {
            }
        };

        Optional<User> result = run(failing, ALICE);

        assertTrue(result.isEmpty());
        assertTrue(output().contains("user data could not be saved"));
    }
}
