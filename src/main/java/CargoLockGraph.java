import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Reads the resolved package identities and dependency edges in a Cargo lockfile. */
record CargoLockGraph(String root, Map<String, List<String>> edges, Map<String, String> labels) {
    private static final Pattern PACKAGE_TABLE = Pattern.compile("(?m)^\\h*\\[\\[package]]\\h*$");
    private static final String QUOTED = "\"([^\"\\r\\n]*)\"";
    private static final Pattern DEPENDENCIES = Pattern.compile(
            "(?m)^\\h*dependencies\\h*=\\h*\\[((?:[\\s,]|\"[^\"\\r\\n]*\")*)]");
    private static final Pattern STRING = Pattern.compile(QUOTED);

    static CargoLockGraph parse(String lock, String name, String version) {
        List<LockedPackage> packages = readPackages(lock);
        Map<String, List<LockedPackage>> byName = packages.stream()
                .collect(Collectors.groupingBy(LockedPackage::name));
        LockedPackage root = findRoot(byName.getOrDefault(name, List.of()), name, version);
        Map<String, List<String>> edges = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (LockedPackage pkg : packages) {
            List<String> dependencies = pkg.dependencies().stream()
                    .map(reference -> resolve(reference, byName))
                    .toList();
            edges.put(pkg.id(), dependencies);
            labels.put(pkg.id(), displayLabel(pkg, byName.get(pkg.name())));
        }
        return new CargoLockGraph(root.id(), edges, labels);
    }

    String nameOf(String id) {
        return id.substring(0, id.indexOf(' '));
    }

    private static List<LockedPackage> readPackages(String lock) {
        String[] sections = PACKAGE_TABLE.split(lock);
        List<LockedPackage> packages = new ArrayList<>();
        for (int i = 1; i < sections.length; i++) {
            String table = sections[i];
            String name = readField(table, "name");
            String version = readField(table, "version");
            if (name.isEmpty() || version.isEmpty()) {
                throw new IllegalArgumentException("В Cargo.lock пакет без имени или версии");
            }
            var dependencies = DEPENDENCIES.matcher(table);
            List<String> entries = dependencies.find()
                    ? STRING.matcher(dependencies.group(1)).results().map(entry -> entry.group(1)).toList()
                    : List.of();
            packages.add(new LockedPackage(name, version, readField(table, "source"), entries));
        }
        return packages;
    }

    private static String readField(String table, String key) {
        var field = Pattern.compile("(?m)^\\h*" + key + "\\h*=\\h*" + QUOTED).matcher(table);
        return field.find() ? field.group(1) : "";
    }

    private static LockedPackage findRoot(List<LockedPackage> candidates, String name, String version) {
        List<LockedPackage> matching = candidates.stream().filter(pkg -> pkg.version().equals(version)).toList();
        return matching.stream().filter(pkg -> pkg.source().isEmpty()).findFirst()
                .orElseGet(() -> onlyMatch(matching, name + " " + version));
    }

    private static String resolve(String reference, Map<String, List<LockedPackage>> byName) {
        String name = reference.split(" ", 2)[0];
        List<LockedPackage> matches = byName.getOrDefault(name, List.of()).stream()
                .filter(pkg -> reference.equals(name) || reference.equals(pkg.nameAndVersion())
                        || reference.equals(pkg.id()))
                .toList();
        return onlyMatch(matches, reference).id();
    }

    private static LockedPackage onlyMatch(List<LockedPackage> matches, String reference) {
        if (matches.size() != 1) {
            throw new IllegalArgumentException("В Cargo.lock нет однозначного пакета " + reference);
        }
        return matches.getFirst();
    }

    private static String displayLabel(LockedPackage pkg, List<LockedPackage> sameName) {
        long sameVersion = sameName.stream().filter(other -> other.version().equals(pkg.version())).count();
        return sameVersion == 1 ? pkg.nameAndVersion() : pkg.id();
    }

    private record LockedPackage(String name, String version, String source, List<String> dependencies) {
        String nameAndVersion() {
            return name + " " + version;
        }

        String id() {
            return source.isEmpty() ? nameAndVersion() : nameAndVersion() + " (" + source + ")";
        }
    }
}
