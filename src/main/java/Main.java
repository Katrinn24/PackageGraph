import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Main {
    private static final String REVERSE_FLAG = "--reverse";

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        boolean reverse = args.length > 0 && REVERSE_FLAG.equals(args[args.length - 1]);
        int configArguments = args.length - (reverse ? 1 : 0);
        if (configArguments > 1 || (configArguments == 1 && args[0].startsWith("--"))) {
            throw new IllegalArgumentException("Формат запуска: Main [config.yaml] [--reverse]");
        }

        String configPath = configArguments == 1 ? args[0] : "config.yaml";
        Config config = ConfigLoader.load(configPath);
        if (reverse && !config.testRepository()) {
            throw new IllegalArgumentException(
                    "Для --reverse включите testRepository в конфигурации"
            );
        }
        printConfig(config);

        if (config.testRepository()) {
            analyzeTestGraph(config, reverse);
        } else {
            printCargoDependencies(config);
        }
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
