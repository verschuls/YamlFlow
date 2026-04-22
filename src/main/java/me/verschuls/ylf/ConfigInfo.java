package me.verschuls.ylf;

import java.nio.file.Path;

/**
 * Wrapper holding a config's data, key and its file path.
 * Used by {@link CMI} to track where each config came from.
 *
 * @param <DataClass> the config data type
 */
public record ConfigInfo<DataKey, DataClass extends BaseData>(DataKey key, DataClass data, Path path) {

    static <DataKey, DataClass extends BaseData> ConfigInfo<DataKey, DataClass> of(DataKey key, DataClass data, Path path) {
        return new ConfigInfo<>(key, data, path);
    }

}