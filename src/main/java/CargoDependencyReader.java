import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class CargoDependencyReader {
    private static final String KEY = "(?:[A-Za-z0-9_-]+|\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*')";
    private static final String KEY_PATH = KEY + "(?:\\h*\\.\\h*" + KEY + ")*";
    private static final Pattern KEY_PART = Pattern.compile(KEY);
    private static final Pattern TABLE_HEADER = Pattern.compile(
            "^\\[\\h*(" + KEY_PATH + ")\\h*]\\h*(?:#.*)?$");
    private static final Pattern ENTRY = Pattern.compile("^(" + KEY_PATH + ")\\h*=");
    private static final Set<String> DEPENDENCY_TABLES = Set.of(
            "dependencies", "build-dependencies", "dev-dependencies");
    private static final int TARGET_DEPENDENCY_TABLE_INDEX = 2;

    public static List<String> readDependencies(
            String repository, String version, String packageName
    ) throws IOException, InterruptedException {
        String cargoToml = fetchCargoToml(
                repository, version, packageName
        );
        return parseDependencies(cargoToml);
    }


    private static String fetchCargoToml(
            String repository, String version, String packageName
    ) throws IOException, InterruptedException {
        String baseUrl = buildRawUrl(repository);
        HttpClient client = HttpClient.newHttpClient();

        List<String> filePaths = List.of(
                packageName + "/Cargo.toml",
                "Cargo.toml"
        );

        List<String> tags = List.of(
                "v" + version,
                version
        );

        for (String tag : tags) {
            for (String filePath : filePaths) {
                String url = baseUrl + "/" + tag + "/" + filePath;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() == 200) {
                    System.out.println("Cargo.toml получен: " + url);
                    return response.body();
                }
            }
        }

        throw new IOException(
                "Cargo.toml не найден для версии " + version
        );
    }


    private static String buildRawUrl(String repository) {
        URI uri = URI.create(repository);
        String host = uri.getHost();

        if (!"github.com".equalsIgnoreCase(host)) {
            throw new IllegalArgumentException(
                    "Пока поддерживаются только GitHub-репозитории"
            );
        }

        String[] parts = uri.getPath().split("/");
        if (parts.length < 3) {
            throw new IllegalArgumentException(
                    "Некорректный адрес GitHub-репозитория"
            );
        }

        String owner = parts[1];
        String repo = parts[2].replaceAll("\\.git$", "");

        return "https://raw.githubusercontent.com/"
                + owner + "/" + repo;
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
