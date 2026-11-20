
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DependencyAnalyzerTest {

    private Map<String, List<String>> createGraph() {
        return Map.of(
                "A", List.of("B", "C"),
                "B", List.of("D"),
                "C", List.of("D"),
                "D", List.of("A"),
                "E", List.of()
        );
    }

    @Test
    void shouldFindTransitiveDependencies() {
        List<String> result = DependencyAnalyzer.analyze(
                createGraph(), "A", 10, ""
        );

        assertEquals(List.of("B", "D", "C"), result);
    }

    @Test
    void shouldRespectMaximumDepth() {
        List<String> result = DependencyAnalyzer.analyze(
                createGraph(), "A", 1, ""
        );

        assertEquals(List.of("B", "C"), result);
    }

    @Test
    void shouldExpandPackageReachedAgainAtSmallerDepth() {
        List<String> result = DependencyAnalyzer.analyze(
                createGraphWithShortcut(), "A", 3, ""
        );
        assertEquals(List.of("B", "C", "X", "Y"), result);
    }

    @Test
    void shouldFindCycleWhenShortcutLeavesEnoughDepth() {
        Map<String, List<String>> graph = createGraphWithShortcut();

        assertTrue(DependencyAnalyzer.findCycles(graph, "A", 2, "").isEmpty());
        assertEquals(List.of("X -> Y -> X"),
                DependencyAnalyzer.findCycles(graph, "A", 3, ""));
    }

    private Map<String, List<String>> createGraphWithShortcut() {
        return Map.of(
                "A", List.of("B", "X"),
                "B", List.of("C"),
                "C", List.of("X"),
                "X", List.of("Y"),
                "Y", List.of("X")
        );
    }

    @Test
    void shouldExcludePackagesBySubstring() {
        List<String> result = DependencyAnalyzer.analyze(
                createGraph(), "A", 10, "B"
        );

        assertEquals(List.of("C", "D"), result);
    }

    @Test
    void shouldFindCycles() {
        List<String> result = DependencyAnalyzer.findCycles(
                createGraph(), "A", 10, ""
        );

        assertEquals(List.of("A -> B -> D -> A"), result);
    }

    @Test
    void shouldNotFindCyclesWhenDepthIsTooSmall() {
        List<String> result = DependencyAnalyzer.findCycles(
                createGraph(), "A", 1, ""
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowWhenStartPackageDoesNotExist() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DependencyAnalyzer.analyze(
                        createGraph(), "Z", 10, ""
                )
        );
    }

    @Test
    void shouldFindDirectAndTransitiveReverseDependencies() {
        Map<String, List<String>> graph = Map.of(
                "A", List.of("B"), "B", List.of("C"), "C", List.of(), "E", List.of()
        );

        assertEquals(List.of("B", "A"),
                DependencyAnalyzer.analyzeReverse(graph, "C", 2, ""));
        assertEquals(List.of("B"),
                DependencyAnalyzer.analyzeReverse(graph, "C", 1, ""));
        assertEquals(List.of(),
                DependencyAnalyzer.analyzeReverse(graph, "C", 2, "B"));
        assertEquals(List.of(),
                DependencyAnalyzer.analyzeReverse(graph, "E", 2, ""));
    }

    @Test
    void shouldExcludeStartingPackageWhenReverseDependenciesHaveACycle() {
        Map<String, List<String>> graph = Map.of(
                "A", List.of("B"), "B", List.of("C"), "C", List.of("A")
        );

        assertEquals(List.of("C", "B"),
                DependencyAnalyzer.analyzeReverse(graph, "A", 10, ""));
    }
}
