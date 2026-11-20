import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ConfigLoader {
    private static final Set<String> REQUIRED_KEYS = Set.of(
            "packageName",
            "repository",
            "testRepository",
            "version",
            "outputFile",
            "maxDepth",
            "filterSubstring"
    );

    public static Config load(String fileName) throws IOException {
        Path path = Path.of(fileName);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IOException("Файл конфигурации не найден: " + fileName);
        }

        Map<String, Object> values = readValues(path);
        validateKeys(values);
        return new Config(
                getString(values, "packageName"),
                getString(values, "repository"),
                getBoolean(values, "testRepository"),
                getString(values, "version"),
                getString(values, "outputFile"),
                getInteger(values, "maxDepth"),
                getString(values, "filterSubstring")
        );
    }

    private static Map<String, Object> readValues(Path path) throws IOException {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object data;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            data = yaml.load(reader);
        }

        if (!(data instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException(
                    "Корень YAML должен быть объектом с настройками"
            );
        }

        Map<String, Object> values = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(
                        "Названия параметров должны быть строками"
                );
            }
            values.put(key, entry.getValue());
        }
        return values;
    }

    private static void validateKeys(Map<String, Object> values) {
        for (String key : REQUIRED_KEYS) {
            if (!values.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Отсутствует обязательный параметр: " + key
                );
            }
        }

        for (String key : values.keySet()) {
            if (!REQUIRED_KEYS.contains(key)) {
                throw new IllegalArgumentException(
                        "Неизвестный параметр: " + key
                );
            }
        }
    }

    private static String getString(
            Map<String, Object> values, String key
    ) {
        Object value = values.get(key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(
                    key + ": ожидается текстовое значение"
            );
        }
        return (String) value;
    }

    private static boolean getBoolean(
            Map<String, Object> values, String key
    ) {
        Object value = values.get(key);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(
                    key + ": ожидается true или false"
            );
        }
        return (Boolean) value;
    }

    private static int getInteger(
            Map<String, Object> values, String key
    ) {
        Object value = values.get(key);
        if (!(value instanceof Number number)
                || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException(
                    key + ": ожидается целое число"
            );
        }
        return number.intValue();
    }
}

