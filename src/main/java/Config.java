public record Config(
        String packageName,
        String repository,
        boolean testRepository,
        String version,
        String outputFile,
        int maxDepth,
        String filterSubstring
) {
    public Config {
        validatePackageName(packageName);
        validateRepository(repository, testRepository);
        validateVersion(version);
        validateOutputFile(outputFile);
        validateMaxDepth(maxDepth);
        validateFilterSubstring(filterSubstring);
    }

    private static void validatePackageName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException(
                    "packageName: укажите корректное имя пакета"
            );
        }
    }

    private static void validateRepository(
            String repository, boolean testMode
    ) {
        if (repository == null || repository.isBlank()) {
            throw new IllegalArgumentException(
                    "repository: значение не должно быть пустым"
            );
        }

        if (testMode) {
            java.nio.file.Path path = java.nio.file.Path.of(repository);
            if (!java.nio.file.Files.isRegularFile(path)
                    || !java.nio.file.Files.isReadable(path)) {
                throw new IllegalArgumentException(
                        "repository: тестовый файл не найден или недоступен"
                );
            }
            return;
        }

        try {
            java.net.URI uri = java.net.URI.create(repository);
            String scheme = uri.getScheme();
            if (uri.getHost() == null
                    || !("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "repository: укажите корректный HTTP или HTTPS URL"
            );
        }
    }

    private static void validateVersion(String version) {
        if (version == null || !version.matches(
                "\\d+\\.\\d+\\.\\d+([+-][A-Za-z0-9.-]+)?"
        )) {
            throw new IllegalArgumentException(
                    "version: ожидается версия формата 1.2.3"
            );
        }
    }

    private static void validateOutputFile(String file) {
        if (file == null || file.isBlank()
                || !file.toLowerCase().endsWith(".png")) {
            throw new IllegalArgumentException(
                    "outputFile: укажите имя PNG-файла"
            );
        }
    }

    private static void validateMaxDepth(int depth) {
        if (depth < 0) {
            throw new IllegalArgumentException(
                    "maxDepth: глубина не может быть отрицательной"
            );
        }
    }

    private static void validateFilterSubstring(String filter) {
        if (filter == null) {
            throw new IllegalArgumentException(
                    "filterSubstring: значение должно быть строкой"
            );
        }
    }
}

