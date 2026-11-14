import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestGraphReader {

    public static Map<String, List<String>> readGraph(String filePath)
            throws IOException {

        Map<String, List<String>> graph = new LinkedHashMap<>();

        List<String> lines = Files.readAllLines(Path.of(filePath));

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split(":", -1);

            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "Неверный формат строки " + (i + 1)
                );
            }

            String packageName = parts[0].trim();

            if (!packageName.matches("[A-Z]+")) {
                throw new IllegalArgumentException(
                        "Некорректное имя пакета в строке " + (i + 1)
                );
            }

            if (graph.containsKey(packageName)) {
                throw new IllegalArgumentException(
                        "Пакет повторяется: " + packageName
                );
            }

            List<String> dependencies = new ArrayList<>();

            String dependencyText = parts[1].trim();

            if (!dependencyText.isEmpty()) {
                for (String dependency : dependencyText.split(",")) {
                    String name = dependency.trim();

                    if (!name.matches("[A-Z]+")) {
                        throw new IllegalArgumentException(
                                "Некорректная зависимость в строке " + (i + 1)
                        );
                    }

                    dependencies.add(name);
                }
            }

            graph.put(packageName, dependencies);
        }

        // Проверяем, что все упомянутые зависимости описаны в файле.
        for (Map.Entry<String, List<String>> entry : graph.entrySet()) {
            for (String dependency : entry.getValue()) {
                if (!graph.containsKey(dependency)) {
                    throw new IllegalArgumentException(
                            "Пакет " + dependency
                                    + " не описан в тестовом файле"
                    );
                }
            }
        }

        return graph;
    }
}
