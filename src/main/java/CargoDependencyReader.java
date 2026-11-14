import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class CargoDependencyReader {

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


    private static List<String> parseDependencies(String cargoToml) {
        List<String> dependencies = new ArrayList<>();
        boolean inDependencies = false;

        for (String originalLine : cargoToml.split("\\R")) {
            String line = originalLine.split("#", 2)[0].trim();

            if (line.startsWith("[")) {
                inDependencies = line.equals("[dependencies]")
                        || line.startsWith("[dependencies.");

                if (line.startsWith("[dependencies.")) {
                    String name = line.substring(
                            "[dependencies.".length(),
                            line.indexOf("]")
                    );
                    dependencies.add(name);
                }
                continue;
            }

            if (inDependencies && line.contains("=")) {
                String name = line.substring(0, line.indexOf("=")).trim();
                name = name.replace("\"", "").replace("'", "");

                if (!name.isEmpty()) {
                    dependencies.add(name);
                }
            }
        }

        return dependencies;
    }

}
