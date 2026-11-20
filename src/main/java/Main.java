import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class Main {
    private static final String REVERSE_FLAG = "--reverse";
    private static final String VISUALIZE_FLAG = "--visualize";

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        String last = args.length == 0 ? "" : args[args.length - 1];
        boolean reverse = REVERSE_FLAG.equals(last);
        boolean visualize = VISUALIZE_FLAG.equals(last);
        Config config = ConfigLoader.load(configPath(args, reverse || visualize));
        if (reverse && !config.testRepository()) {
            throw new IllegalArgumentException(
                    "Для --reverse включите testRepository в конфигурации"
            );
        }
        printConfig(config);

        if (visualize) {
            visualize(config);
        } else if (config.testRepository()) {
            analyzeTestGraph(config, reverse);
        } else {
            printCargoDependencies(config);
        }
    }

    private static String configPath(String[] args, boolean hasMode) {
        int configArguments = args.length - (hasMode ? 1 : 0);
        if (configArguments > 1 || (configArguments == 1 && args[0].startsWith("--"))) {
            throw new IllegalArgumentException("Формат запуска: Main [config.yaml] [--reverse | --visualize]");
        }
        return configArguments == 1 ? args[0] : "config.yaml";
    }

    private static void visualize(Config config) throws IOException, InterruptedException {
        if (!config.testRepository() || config.repository().endsWith(".lock")) {
            CargoLockGraph graph = config.testRepository()
                    ? CargoLockGraph.parse(Files.readString(Path.of(config.repository())),
                        config.packageName(), config.version())
                    : CargoDependencyReader.readLockGraph(config);
            Predicate<String> include = id -> includePackage(graph.nameOf(id), config);
            writeGraph(config, graph.edges(), graph.root(), include, graph.labels()::get);
            return;
        }

        Map<String, List<String>> graph = TestGraphReader.readGraph(config.repository());
        writeGraph(config, graph, config.packageName(), name -> includePackage(name, config), Function.identity());
    }

    private static void writeGraph(
            Config config, Map<String, List<String>> graph, String root,
            Predicate<String> include, Function<String, String> label) throws IOException {
        Map<String, List<String>> selected = DependencyAnalyzer.subgraph(
                graph, root, config.maxDepth(), include);
        Path png = Path.of(config.outputFile());
        Path puml = GraphVisualization.write(png, selected, root, label);
        System.out.println("PlantUML: " + puml);
        System.out.println("PNG: " + png.toAbsolutePath().normalize());
    }

    private static boolean includePackage(String name, Config config) {
        return config.filterSubstring().isEmpty() || !name.contains(config.filterSubstring());
    }

    private static void printCargoDependencies(Config config) throws IOException, InterruptedException {
        List<String> dependencies = CargoDependencyReader.readDependencies(
                config.repository(), config.version(), config.packageName()
        );
        System.out.println("\n=== Direct dependencies ===");
        printItems(dependencies, "- ", "Зависимости не найдены.");
    }

    private static void analyzeTestGraph(Config config, boolean reverse) throws IOException {
        Map<String, List<String>> graph = TestGraphReader.readGraph(config.repository());
        String start = config.packageName();
        int depth = config.maxDepth();
        String filter = config.filterSubstring();

        if (reverse) {
            List<String> dependents = DependencyAnalyzer.analyzeReverse(graph, start, depth, filter);
            System.out.println("\n=== Reverse dependencies ===");
            printItems(dependents, "- ", "Обратные зависимости не найдены.");
            return;
        }

        System.out.println("\n=== Dependency analysis ===");
        printItems(DependencyAnalyzer.analyze(graph, start, depth, filter),
                "- ", "Зависимости не найдены.");

        System.out.println("\n=== Cycles ===");
        printItems(DependencyAnalyzer.findCycles(graph, start, depth, filter),
                "Цикл: ", "Циклические зависимости не найдены.");
    }

    private static void printItems(List<String> items, String prefix, String emptyMessage) {
        if (items.isEmpty()) {
            System.out.println(emptyMessage);
        } else {
            for (String item : items) {
                System.out.println(prefix + item);
            }
        }
    }

    private static void printConfig(Config config) {
        System.out.println("=== PackageGraph: configuration ===");
        System.out.println("packageName: " + config.packageName());
        System.out.println("repository: " + config.repository());
        System.out.println("testRepository: " + config.testRepository());
        System.out.println("version: " + config.version());
        System.out.println("outputFile: " + config.outputFile());
        System.out.println("maxDepth: " + config.maxDepth());
        System.out.println("filterSubstring: " + config.filterSubstring());
        System.out.println("====================================");
    }
}
