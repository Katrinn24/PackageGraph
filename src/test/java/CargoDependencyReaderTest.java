import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CargoDependencyReaderTest {
    @Test
    void shouldUseOnlyManifestWithTheRequestedPackageAndVersion() {
        String manifest = """
                [package] # Root package
                name = "ripgrep"
                version = '14.1.1' # Release version
                [package.metadata]
                name = "another-package"
                version = "9.0.0"
                """;

        assertTrue(CargoDependencyReader.matchesPackage(manifest, "ripgrep", "14.1.1"));
        assertFalse(CargoDependencyReader.matchesPackage(manifest, "ripgrep", "9.0.0"));
        assertFalse(CargoDependencyReader.matchesPackage(manifest, "another-package", "14.1.1"));
    }

    @Test
    void shouldReadTableDependenciesWithoutTreatingTheirFieldsAsPackages() {
        String manifest = """
                [dependencies]
                serde = "1"
                [dependencies.regex]
                version = "1"
                default-features = false
                [target.'cfg(unix)'.dependencies]
                libc = "0.2"
                """;

        assertEquals(List.of("serde", "regex", "libc"),
                CargoDependencyReader.parseDependencies(manifest));
    }

    @Test
    void shouldIncludeBuildAndDevDependenciesAndIgnoreOtherTables() {
        String manifest = """
                [workspace.dependencies]
                unused = "1"
                [build-dependencies]
                cc = "1"
                [dev-dependencies]
                cc = "1"
                'tempfile' = "3"
                [ target . 'cfg(target_os = "windows")' . build-dependencies . "winres" ] # Windows
                version = "0.1"
                [package.metadata.dependencies]
                metadata = "ignored"
                """;

        assertEquals(List.of("cc", "tempfile", "winres"),
                CargoDependencyReader.parseDependencies(manifest));
    }

    @Test
    void shouldReadDottedKeysAndIgnoreDependencyReferencesInFeatures() {
        String manifest = """
                [dependencies]
                toml.version = "0.8"
                toml.optional = true
                serde = { version = "1", features = ["derive"] }
                [features]
                config = ["dep:toml"]
                [[bin]]
                name = "main"
                """;

        assertEquals(List.of("toml", "serde"), CargoDependencyReader.parseDependencies(manifest));
    }
}
