import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class CargoDependencyReader {
    private static final String KEY = "(?:[A-Za-z0-9_-]+|\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*')";
    private static final String KEY_PATH = KEY + "(?:\\h*\\.\\h*" + KEY + ")*";
    private static final Pattern KEY_PART = Pattern.compile(KEY);
    private static final Pattern TABLE_HEADER = Pattern.compile(
            "^\\[\\h*(" + KEY_PATH + ")\\h*]\\h*(?:#.*)?$");
    private static final Pattern ENTRY = Pattern.compile("^(" + KEY_PATH + ")\\h*=");
    private static final Pattern STRING_VALUE = Pattern.compile(
            "^\\h*(\"[^\"\\r\\n]*\"|'[^'\\r\\n]*')\\h*(?:#.*)?$");
    private static final Pattern REPOSITORY_PATH = Pattern.compile("^/([A-Za-z0-9-]+)/([\\w.-]+)/?$");
    private static final Set<String> DEPENDENCY_TABLES = Set.of(
            "dependencies", "build-dependencies", "dev-dependencies");
    private static final int TARGET_DEPENDENCY_TABLE_INDEX = 2;
    private static final int HTTP_OK = 200;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);

    public static List<String> readDependencies(
            String repository, String version, String packageName
    ) throws IOException, InterruptedException {
        String cargoToml = fetchFile(repository, version,
                List.of(packageName + "/Cargo.toml", "Cargo.toml"), "Cargo.toml пакета " + packageName,
                manifest -> matchesPackage(manifest, packageName, version));
        return parseDependencies(cargoToml);
    }

    static CargoLockGraph readLockGraph(Config config) throws IOException, InterruptedException {
        String lock = fetchFile(config.repository(), config.version(),
                List.of("Cargo.lock", config.packageName() + "/Cargo.lock"), "Cargo.lock",
                content -> true);
        return CargoLockGraph.parse(lock, config.packageName(), config.version());
    }

    private static String fetchFile(
            String repository, String version, List<String> filePaths,
            String description, Predicate<String> matches
    ) throws IOException, InterruptedException {
        String baseUrl = buildRawUrl(repository);
        HttpClient client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
        List<String> tags = List.of("v" + version, version);
        for (String tag : tags) {
            for (String filePath : filePaths) {
                String url = baseUrl + "/" + tag + "/" + filePath;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() == HTTP_OK && matches.test(response.body())) {
                    System.out.println("Файл получен: " + url);
                    return response.body();
                }
            }
        }

        throw new IOException(description + " не найден для версии " + version);
    }


    private static String buildRawUrl(String repository) {
        URI uri = URI.create(repository);
        String host = uri.getHost();

        if (!"github.com".equalsIgnoreCase(host)) {
            throw new IllegalArgumentException(
                    "Пока поддерживаются только GitHub-репозитории"
            );
        }

        var path = REPOSITORY_PATH.matcher(uri.getPath());
        if (!path.matches()) {
            throw new IllegalArgumentException(
                    "Некорректный адрес GitHub-репозитория"
            );
        }

        String owner = path.group(1);
        String repo = path.group(2).replaceAll("\\.git$", "");

        return "https://raw.githubusercontent.com/"
                + owner + "/" + repo;
    }

    /** Checks the explicit package identity before using a downloaded manifest. */
    static boolean matchesPackage(String manifest, String expectedName, String expectedVersion) {
        boolean inPackage = false;
        String name = "";
        String version = "";
        for (String originalLine : manifest.split("\\R")) {
            String line = originalLine.strip();
            if (line.startsWith("[")) {
                var table = TABLE_HEADER.matcher(line);
                inPackage = table.matches() && splitKeys(table.group(1)).equals(List.of("package"));
                continue;
            }
            if (inPackage) {
                var entry = ENTRY.matcher(line);
                if (entry.find()) {
                    List<String> key = splitKeys(entry.group(1));
                    var value = STRING_VALUE.matcher(line.substring(entry.end()));
                    if (value.matches()) {
                        if (key.equals(List.of("name"))) {
                            name = unquoteKey(value.group(1));
                        } else if (key.equals(List.of("version"))) {
                            version = unquoteKey(value.group(1));
                        }
                    }
                }
            }
        }
        return name.equals(expectedName) && version.equals(expectedVersion);
    }


    /** Lists the dependency keys declared by the package across all targets. */
    static List<String> parseDependencies(String cargoToml) {
        Set<String> dependencies = new LinkedHashSet<>();
        boolean inDependencyTable = false;

        for (String originalLine : cargoToml.split("\\R")) {
            String line = originalLine.strip();

            if (line.startsWith("[")) {
                inDependencyTable = readDependencyTable(line, dependencies);
                continue;
            }

            if (inDependencyTable) {
                var entry = ENTRY.matcher(line);
                if (entry.find()) {
                    dependencies.add(splitKeys(entry.group(1)).getFirst());
                }
            }
        }

        return List.copyOf(dependencies);
    }

    private static boolean readDependencyTable(String line, Set<String> dependencies) {
        var table = TABLE_HEADER.matcher(line);
        if (!table.matches()) {
            return false;
        }
        List<String> keys = splitKeys(table.group(1));
        if (keys.getFirst().equals("target")) {
            if (keys.size() <= TARGET_DEPENDENCY_TABLE_INDEX) {
                return false;
            }
            keys = keys.subList(TARGET_DEPENDENCY_TABLE_INDEX, keys.size());
        }
        if (!DEPENDENCY_TABLES.contains(keys.getFirst())) {
            return false;
        }
        if (keys.size() == 1) {
            return true;
        }
        dependencies.add(keys.get(1));
        return false;
    }

    private static List<String> splitKeys(String path) {
        return KEY_PART.matcher(path).results()
                .map(match -> unquoteKey(match.group()))
                .toList();
    }

    private static String unquoteKey(String key) {
        char first = key.charAt(0);
        return first == '"' || first == '\'' ? key.substring(1, key.length() - 1) : key;
    }
}
