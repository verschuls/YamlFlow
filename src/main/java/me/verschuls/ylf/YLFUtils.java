package me.verschuls.ylf;

import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Internal utilities for config loading and versioning.
 */
public class YLFUtils {

    // Reads version field from yaml file
    private static String getVersion(Path file) throws IOException {
        try (var lines = Files.lines(file)) {
            String version = lines
                    .map(String::trim)
                    .filter(line -> !line.startsWith("#"))
                    .filter(line -> line.startsWith("version:"))
                    .map(line -> line.substring(9).trim())
                    .findFirst()
                    .orElse(null);
            if (version == null) throw new RuntimeException();
            return version.replace("'", "").replace("\"", "");
        }
    }

    // Returns filename without extension
    public static String getBaseName(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }

    // Loads config, creates backup if version mismatch
    static <T extends BaseData> T loadConfig(Path file, Path path, Class<T> dataClass, YamlConfigurationProperties properties, VersionCompare versionCompare, String configVersion, String backupDir) {
        if (!Files.exists(file)) return updateConfig(file, dataClass, properties, configVersion);
        if (configVersion == null)
            return YamlConfigurations.update(file, dataClass, properties);
        String fileVersion;
        try {
            fileVersion = getVersion(file);
        } catch (IOException e) {
            throw new RuntimeException("Wasn't able to find version",e);
        }
        if (versionCompare.compare(fileVersion, configVersion)) return updateConfig(file, dataClass, properties, configVersion);
        try {
            Path backUp = path.resolve(backupDir);
            if (!Files.exists(backUp)) Files.createDirectory(backUp);
            Files.copy(file, backUp.resolve(getBaseName(file)+"-v"+fileVersion+"-"+ UUID.randomUUID().toString().substring(0, 4)+".yml"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return updateConfig(file, dataClass, properties, configVersion);
    }

    // Updates config and sets version field
    private static <T extends BaseData> T updateConfig(Path file, Class<T> dataClass, YamlConfigurationProperties properties, String configVersion) {
        if (configVersion == null)
            return YamlConfigurations.update(file, dataClass, properties);
        T data = YamlConfigurations.update(file, dataClass, properties);
        data.version = configVersion;
        YamlConfigurations.save(file, dataClass, data, properties);
        return data;
    }
}
