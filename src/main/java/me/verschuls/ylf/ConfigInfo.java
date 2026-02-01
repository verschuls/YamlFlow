package me.verschuls.ylf;

import java.nio.file.Path;

/**
 * Wrapper holding a config's data and its file path.
 * Used by {@link CMI} to track where each config came from.
 *
 * @param <DataClass> the config data type
 */
public final class ConfigInfo<DataClass extends BaseData> {
    private final DataClass data;
    private final Path path;

    private ConfigInfo(DataClass data, Path path) {
        this.data = data;
        this.path = path;
    }

    static <DataClass extends BaseData> ConfigInfo<DataClass> of(DataClass data, Path path) {
        return new ConfigInfo<>(data, path);
    }

    /**
     * @return the config data instance
     */
    public DataClass getData() {
        return data;
    }

    /**
     * @return path to the config file
     */
    public Path getPath() {
        return path;
    }
}