
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
}
