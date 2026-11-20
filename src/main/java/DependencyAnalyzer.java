import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        requireStartPackage(graph, startPackage);
        Map<String, Integer> bestDepths = new LinkedHashMap<>();
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(startPackage, 0));

        while (!stack.isEmpty()) {
            Frame current = stack.pop();
            String packageName = current.packageName();
            int depth = current.depth();

            if (depth > maxDepth
                    || isFiltered(packageName, filterSubstring)
                    || !visitAtSmallerDepth(bestDepths, packageName, depth)) {
                continue;
            }

            if (depth < maxDepth) {
                pushDependencies(stack, graph.get(packageName), depth + 1);
            }
        }

        bestDepths.remove(startPackage);
        return new ArrayList<>(bestDepths.keySet());
    }

    private static void pushDependencies(
            Deque<Frame> stack, List<String> dependencies, int depth) {
        for (int i = dependencies.size() - 1; i >= 0; i--) {
            stack.push(new Frame(dependencies.get(i), depth));
        }
    }

    /** Reports cycles closed by back edges encountered during the bounded DFS. */
    public static List<String> findCycles(
            Map<String, List<String>> graph,
            String startPackage,
            int maxDepth,
            String filterSubstring) {
        requireStartPackage(graph, startPackage);
        if (maxDepth <= 0 || isFiltered(startPackage, filterSubstring)) {
            return new ArrayList<>();
        }

        Set<String> cycles = new LinkedHashSet<>();
        Map<String, Integer> bestDepths = new HashMap<>();
        Set<String> active = new LinkedHashSet<>();
        Deque<CycleFrame> stack = new ArrayDeque<>();
        bestDepths.put(startPackage, 0);
        active.add(startPackage);
        stack.push(new CycleFrame(startPackage, 0));

        while (!stack.isEmpty()) {
            CycleFrame current = stack.peek();
            List<String> dependencies = graph.get(current.packageName);
            if (current.depth >= maxDepth
                    || current.nextIndex >= dependencies.size()) {
                stack.pop();
                active.remove(current.packageName);
                continue;
            }

            String dependency = dependencies.get(current.nextIndex++);
            if (isFiltered(dependency, filterSubstring)) {
                continue;
            }
            int nextDepth = current.depth + 1;
            if (active.contains(dependency)) {
                cycles.add(describeCycle(active, dependency));
            } else if (visitAtSmallerDepth(bestDepths, dependency, nextDepth)) {
                active.add(dependency);
                stack.push(new CycleFrame(dependency, nextDepth));
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
        private final int depth;
        private int nextIndex = 0;

        private CycleFrame(String packageName, int depth) {
            this.packageName = packageName;
            this.depth = depth;
        }
    }

    private record Frame(String packageName, int depth) {
    }
}
