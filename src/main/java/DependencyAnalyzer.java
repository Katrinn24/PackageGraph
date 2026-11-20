import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class DependencyAnalyzer {

    /**
     * Lists reachable dependencies in the order they are first visited.
     * A shorter route can leave enough depth to explore additional edges.
     */
    public static List<String> analyze(
            Map<String, List<String>> graph,
            String startPackage,
            int maxDepth,
            String filterSubstring) {

        Map<String, Integer> depths = findDepths(
                graph, startPackage, maxDepth, name -> !isFiltered(name, filterSubstring));
        depths.remove(startPackage);
        return new ArrayList<>(depths.keySet());
    }

    /** Returns the vertices and edges reached within the depth limit. */
    public static Map<String, List<String>> subgraph(
            Map<String, List<String>> graph, String startPackage,
            int maxDepth, Predicate<String> include) {
        Map<String, Integer> depths = findDepths(graph, startPackage, maxDepth, include);
        Map<String, List<String>> result = new LinkedHashMap<>();
        depths.forEach((name, depth) -> {
            List<String> edges = depth < maxDepth
                    ? graph.get(name).stream().filter(depths::containsKey).distinct().toList()
                    : List.of();
            result.put(name, edges);
        });
        return result;
    }

    private static Map<String, Integer> findDepths(
            Map<String, List<String>> graph, String startPackage,
            int maxDepth, Predicate<String> include) {
        requireStartPackage(graph, startPackage);
        Map<String, Integer> bestDepths = new LinkedHashMap<>();
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(startPackage, 0));

        while (!stack.isEmpty()) {
            Frame current = stack.pop();
            String packageName = current.packageName();
            int depth = current.depth();

            if (depth > maxDepth
                    || !include.test(packageName)
                    || !visitAtSmallerDepth(bestDepths, packageName, depth)) {
                continue;
            }

            if (depth < maxDepth) {
                pushDependencies(stack, graph.get(packageName), depth + 1);
            }
        }

        return bestDepths;
    }

    /** Finds packages that depend directly or transitively on the selected package. */
    public static List<String> analyzeReverse(
            Map<String, List<String>> graph,
            String startPackage,
            int maxDepth,
            String filterSubstring) {
        Map<String, List<String>> reversed = new LinkedHashMap<>();
        graph.keySet().forEach(name -> reversed.put(name, new ArrayList<>()));
        graph.forEach((name, dependencies) -> {
            for (String dependency : dependencies) {
                reversed.get(dependency).add(name);
            }
        });
        return analyze(reversed, startPackage, maxDepth, filterSubstring);
    }

    private static void pushDependencies(
            Deque<Frame> stack, List<String> dependencies, int depth) {
        for (int i = dependencies.size() - 1; i >= 0; i--) {
            stack.push(new Frame(dependencies.get(i), depth));
        }
    }

    /** Reports cycles closed by back edges in the graph selected by the bounded DFS. */
    public static List<String> findCycles(
            Map<String, List<String>> graph,
            String startPackage,
            int maxDepth,
            String filterSubstring) {
        Map<String, List<String>> selected = subgraph(
                graph, startPackage, maxDepth, name -> !isFiltered(name, filterSubstring));
        if (selected.isEmpty()) {
            return List.of();
        }

        Set<String> cycles = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        Set<String> active = new LinkedHashSet<>();
        Deque<CycleFrame> stack = new ArrayDeque<>();
        visited.add(startPackage);
        active.add(startPackage);
        stack.push(new CycleFrame(startPackage));

        while (!stack.isEmpty()) {
            CycleFrame current = stack.peek();
            List<String> dependencies = selected.get(current.packageName);
            if (current.nextIndex >= dependencies.size()) {
                stack.pop();
                active.remove(current.packageName);
                continue;
            }

            String dependency = dependencies.get(current.nextIndex++);
            if (active.contains(dependency)) {
                cycles.add(describeCycle(active, dependency));
            } else if (visited.add(dependency)) {
                active.add(dependency);
                stack.push(new CycleFrame(dependency));
            }
        }

        return new ArrayList<>(cycles);
    }

    private static String describeCycle(Set<String> active, String dependency) {
        List<String> path = new ArrayList<>(active);
        List<String> cycle = path.subList(path.indexOf(dependency), path.size());
        return String.join(" -> ", cycle) + " -> " + dependency;
    }

    private static boolean visitAtSmallerDepth(
            Map<String, Integer> bestDepths, String packageName, int depth) {
        Integer previousDepth = bestDepths.get(packageName);
        if (previousDepth != null && previousDepth <= depth) {
            return false;
        }
        bestDepths.put(packageName, depth);
        return true;
    }

    private static boolean isFiltered(String packageName, String filterSubstring) {
        return !filterSubstring.isEmpty() && packageName.contains(filterSubstring);
    }

    private static void requireStartPackage(
            Map<String, List<String>> graph, String startPackage) {
        if (!graph.containsKey(startPackage)) {
            throw new IllegalArgumentException(
                    "Пакет не найден в графе: " + startPackage
            );
        }
    }

    private static class CycleFrame {
        private final String packageName;
        private int nextIndex = 0;

        private CycleFrame(String packageName) {
            this.packageName = packageName;
        }
    }

    private record Frame(String packageName, int depth) {
    }
}
