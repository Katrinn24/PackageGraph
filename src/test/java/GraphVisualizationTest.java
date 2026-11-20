import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class GraphVisualizationTest {
    @TempDir
    Path directory;

    @Test
    void shouldKeepOnlyEdgesWithinDepthAndExportBothFormats() throws IOException {
        String lock = Files.readString(Path.of("tests/visualization/cargo-book-example.lock"));
        CargoLockGraph cargo = CargoLockGraph.parse(lock, "rand_chacha", "0.2.2");
        var graph = DependencyAnalyzer.subgraph(cargo.edges(), cargo.root(), 2, name -> true);
        assertEquals(List.of("getrandom 0.1.14"), graph.get("rand_core 0.5.1"));
        assertEquals(List.of(), graph.get("getrandom 0.1.14"));
        assertFalse(graph.containsKey("libc 0.2.68"));

        Path png = directory.resolve("demo.png");
        Path puml = GraphVisualization.write(png, graph, cargo.root(), cargo.labels()::get);
        assertEquals(directory.resolve("demo.puml"), puml);
        assertTrue(Files.readString(puml).contains("rectangle \"getrandom 0.1.14\""));
        assertNotNull(ImageIO.read(png.toFile()));
    }

    @Test
    void shouldKeepDifferentVersionsOfTheSameDependency() {
        String lock = """
                [[package]]
                name = "root"
                version = "1.0.0"
                dependencies = ["dep 1.0.0", "dep 2.0.0"]
                [[package]]
                name = "dep"
                version = "1.0.0"
                [[package]]
                name = "dep"
                version = "2.0.0"
                """;
        CargoLockGraph graph = CargoLockGraph.parse(lock, "root", "1.0.0");

        assertEquals(List.of("dep 1.0.0", "dep 2.0.0"), graph.edges().get(graph.root()));
    }
}
