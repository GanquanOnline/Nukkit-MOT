package cn.nukkit.utils.serverconfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void migrateDebugUsingItemToDebugViaBedrockJe() throws IOException {
        Path config = writeConfig("""
                debug-settings:
                  debug-level: 1
                  debug-using-item: true
                """);

        ConfigMigration.migrateYamlKeys(config.toFile());

        Map<String, Object> debug = readDebugSettings(config);
        assertFalse(debug.containsKey("debug-using-item"));
        assertEquals(true, debug.get("debug-viabedrock-je"));
    }

    @Test
    void explicitDebugViaBedrockJeTakesPrecedenceDuringMigration() throws IOException {
        Path config = writeConfig("""
                debug-settings:
                  debug-using-item: true
                  debug-viabedrock-je: false
                """);

        ConfigMigration.migrateYamlKeys(config.toFile());

        Map<String, Object> debug = readDebugSettings(config);
        assertFalse(debug.containsKey("debug-using-item"));
        assertEquals(false, debug.get("debug-viabedrock-je"));
    }

    private Path writeConfig(String yaml) throws IOException {
        return Files.writeString(tempDir.resolve("nukkit-mot.yml"), yaml, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readDebugSettings(Path config) throws IOException {
        try (var reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
            Map<String, Object> root = new Yaml().load(reader);
            assertTrue(root.containsKey("debug-settings"));
            return (Map<String, Object>) root.get("debug-settings");
        }
    }
}
