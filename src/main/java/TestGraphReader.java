import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestGraphReader {
    private static final int PACKAGE_AND_DEPENDENCIES = 2;
    private static final String PACKAGE_NAME = "[A-Z]+";

    /** Reads the test graph, validating package names and all dependency references. */
    public static Map<String, List<String>> readGraph(String filePath) throws IOException {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(Path.of(filePath));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (!line.isEmpty()) {
                readEntry(graph, line, i + 1);
            }
        }
        validateDependencies(graph);
        return graph;
    }

    private static void readEntry(Map<String, List<String>> graph, String line, int lineNumber) {
        String[] parts = line.split(":", -1);
        if (parts.length != PACKAGE_AND_DEPENDENCIES) {
            throw new IllegalArgumentException("Неверный формат строки " + lineNumber);
        }
        String name = parts[0].strip();
        if (!name.matches(PACKAGE_NAME)) {
            throw new IllegalArgumentException("Некорректное имя пакета в строке " + lineNumber);
        }
        if (graph.containsKey(name)) {
            throw new IllegalArgumentException("Пакет повторяется: " + name);
        }
        graph.put(name, readDependencies(parts[1].strip(), lineNumber));
    }

    private static List<String> readDependencies(String text, int lineNumber) {
        List<String> dependencies = new ArrayList<>();
        if (text.isEmpty()) {
            return dependencies;
        }
        for (String dependency : text.split(",", -1)) {
            String name = dependency.strip();
            if (!name.matches(PACKAGE_NAME)) {
                throw new IllegalArgumentException("Некорректная зависимость в строке " + lineNumber);
            }
            dependencies.add(name);
        }
        return dependencies;
    }

    private static void validateDependencies(Map<String, List<String>> graph) {
        for (List<String> dependencies : graph.values()) {
            for (String dependency : dependencies) {
                if (!graph.containsKey(dependency)) {
                    throw new IllegalArgumentException(
                            "Пакет " + dependency + " не описан в тестовом файле");
                }
            }
        }
    }
}
