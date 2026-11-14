
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestGraphReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadValidGraph() throws IOException {
        Path file = tempDir.resolve("graph.txt");

        Files.writeString(file, """
                A: B, C
                B: D
                C: D
                D:
                """);

        Map<String, List<String>> graph =
                TestGraphReader.readGraph(file.toString());

        assertEquals(List.of("B", "C"), graph.get("A"));
        assertEquals(List.of("D"), graph.get("B"));
        assertEquals(List.of(), graph.get("D"));
    }

    @Test
    void shouldRejectDuplicatePackages() throws IOException {
        Path file = tempDir.resolve("duplicate.txt");

        Files.writeString(file, """
                A: B
                A: C
                B:
                C:
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> TestGraphReader.readGraph(file.toString())
        );
    }

    @Test
    void shouldRejectInvalidPackageNames() throws IOException {
        Path file = tempDir.resolve("invalid.txt");

        Files.writeString(file, """
                A: B
                package:
                B:
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> TestGraphReader.readGraph(file.toString())
        );
    }

    @Test
    void shouldRejectUnknownDependencies() throws IOException {
        Path file = tempDir.resolve("unknown.txt");

        Files.writeString(file, """
                A: B
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> TestGraphReader.readGraph(file.toString())
        );
    }

    @Test
    void shouldRejectInvalidLineFormat() throws IOException {
        Path file = tempDir.resolve("malformed.txt");

        Files.writeString(file, """
                A: B
                B: C: D
                C:
                D:
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> TestGraphReader.readGraph(file.toString())
        );
    }
}

