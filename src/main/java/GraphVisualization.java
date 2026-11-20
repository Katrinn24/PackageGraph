import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.imageio.ImageIO;

/** Exports the same dependency graph as PlantUML text and a PNG image. */
public class GraphVisualization {
    public static Path write(
            Path outputFile, Map<String, List<String>> graph,
            String root, Function<String, String> label) throws IOException {
        Path pngPath = outputFile.toAbsolutePath().normalize();
        String filename = pngPath.getFileName().toString();
        String stem = filename.substring(0, filename.lastIndexOf('.'));
        Path umlPath = pngPath.resolveSibling(stem + ".puml");

        var image = GraphImage.draw(graph, root, label);
        String source = toPlantUml(graph, label);
        Files.createDirectories(pngPath.getParent());
        if (!ImageIO.write(image, "png", pngPath.toFile())) {
            throw new IOException("Java не удалось сохранить изображение PNG");
        }
        Files.writeString(umlPath, source, StandardCharsets.UTF_8);
        return umlPath;
    }

    static String toPlantUml(Map<String, List<String>> graph, Function<String, String> label) {
        StringBuilder text = new StringBuilder("@startuml\nleft to right direction\n");
        Map<String, String> nodes = new LinkedHashMap<>();
        graph.keySet().forEach(name -> {
            String id = "p" + (nodes.size() + 1);
            nodes.put(name, id);
            text.append("rectangle \"").append(escapeLabel(label.apply(name)))
                    .append("\" as ").append(id).append('\n');
        });
        graph.forEach((name, dependencies) -> dependencies.forEach(dependency ->
                text.append(nodes.get(name)).append(" --> ")
                        .append(nodes.get(dependency)).append('\n')));
        if (nodes.isEmpty()) {
            text.append("title Нет пакетов после фильтрации\n");
        }
        return text.append("@enduml\n").toString();
    }

    private static String escapeLabel(String label) {
        return label.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "").replace("\n", "\\n");
    }
}
