import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        String configPath = args.length > 0
                ? args[0]
                : "config.yaml";

        try {
            Config config = ConfigLoader.load(configPath);
            printConfig(config);

            if (!config.testRepository()) {
                List<String> dependencies =
                        CargoDependencyReader.readDependencies(
                                config.repository(),
                                config.version(),
                                config.packageName()
                        );

                System.out.println("\n=== Direct dependencies ===");

                if (dependencies.isEmpty()) {
                    System.out.println("Зависимости не найдены.");
                } else {
                    for (String dependency : dependencies) {
                        System.out.println("- " + dependency);
                    }
                }

            } else {
                Map<String, List<String>> graph =
                        TestGraphReader.readGraph(config.repository());

                System.out.println("\n=== Dependency analysis ===");

                List<String> dependencies = DependencyAnalyzer.analyze(
                        graph,
                        config.packageName(),
                        config.maxDepth(),
                        config.filterSubstring()
                );

                if (dependencies.isEmpty()) {
                    System.out.println("Зависимости не найдены.");
                } else {
                    for (String dependency : dependencies) {
                        System.out.println("- " + dependency);
                    }
                }

                List<String> cycles = DependencyAnalyzer.findCycles(
                        graph,
                        config.packageName(),
                        config.maxDepth(),
                        config.filterSubstring()
                );

                System.out.println("\n=== Cycles ===");

                if (cycles.isEmpty()) {
                    System.out.println("Циклические зависимости не найдены.");
                } else {
                    for (String cycle : cycles) {
                        System.out.println("Цикл: " + cycle);
                    }
                }
            }


        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
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
