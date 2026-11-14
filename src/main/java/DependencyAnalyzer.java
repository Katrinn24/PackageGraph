import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DependencyAnalyzer {

    public static List<String> analyze(
            Map<String, List<String>> graph,
            String startPackage,
            int maxDepth,
            String filterSubstring) {

        if (!graph.containsKey(startPackage)) {
            throw new IllegalArgumentException(
                    "Пакет не найден в графе: " + startPackage
            );
        }

        List<String> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();

        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(startPackage, 0));

        while (!stack.isEmpty()) {
            Frame current = stack.pop();

            String packageName = current.packageName();
            int depth = current.depth();

            if (depth > maxDepth) {
                continue;
            }

            if (!filterSubstring.isEmpty()
                    && packageName.contains(filterSubstring)) {
                continue;
            }

            if (!visited.add(packageName)) {
                continue;
            }

            if (!packageName.equals(startPackage)) {
                result.add(packageName);
            }

            List<String> dependencies = graph.get(packageName);

            for (int i = dependencies.size() - 1; i >= 0; i--) {
                stack.push(new Frame(
                        dependencies.get(i),
                        depth + 1
                ));
            }
        }

        return result;
    }

    public static List<String> findCycles(
            Map<String, List<String>> graph,
            String startPackage,
            int maxDepth,
            String filterSubstring) {

        if (!graph.containsKey(startPackage)) {
            throw new IllegalArgumentException(
                    "Пакет не найден в графе: " + startPackage
            );
        }

        List<String> cycles = new ArrayList<>();
        Set<String> finished = new HashSet<>();
        Set<String> active = new HashSet<>();

        Deque<CycleFrame> stack = new ArrayDeque<>();

        if (!filterSubstring.isEmpty()
                && startPackage.contains(filterSubstring)) {
            return cycles;
        }

        active.add(startPackage);
        stack.push(new CycleFrame(startPackage, 0));

        while (!stack.isEmpty()) {
            CycleFrame current = stack.peek();
            List<String> dependencies =
                    graph.get(current.packageName);

            if (current.nextIndex >= dependencies.size()) {
                stack.pop();
                active.remove(current.packageName);
                finished.add(current.packageName);
                continue;
            }

            String dependency =
                    dependencies.get(current.nextIndex++);

            int nextDepth = current.depth + 1;

            if (nextDepth > maxDepth) {
                continue;
            }

            if (!filterSubstring.isEmpty()
                    && dependency.contains(filterSubstring)) {
                continue;
            }


            if (active.contains(dependency)) {
                List<String> path = new ArrayList<>();

                for (CycleFrame frame : stack) {
                    path.add(frame.packageName);
                }

                List<String> cycle = new ArrayList<>();
                boolean insideCycle = false;

                // Стек перебирается от текущего пакета к начальному,
                // поэтому идём по пути в обратном направлении.
                for (int i = path.size() - 1; i >= 0; i--) {
                    String name = path.get(i);

                    if (name.equals(dependency)) {
                        insideCycle = true;
                    }

                    if (insideCycle) {
                        cycle.add(name);
                    }
                }

                cycle.add(dependency);

                String description = String.join(" -> ", cycle);

                if (!cycles.contains(description)) {
                    cycles.add(description);
                }
            } else if (!finished.contains(dependency)) {
                active.add(dependency);
                stack.push(new CycleFrame(dependency, nextDepth));
            }
        }

        return cycles;
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

