package me.verschuls.ylf;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Key generator for CMI. Maps config files to identifiers.
 *
 * @param <K> key type (String, UUID, Integer, etc.)
 * @param <T> config type
 */
@FunctionalInterface
public interface CIdentifier<K, T> {

    /**
     * Generates a key from file and parsed config.
     */
    K identify(File file, T config);

    /**
     * Uses file name without extension as key.
     */
    static <T> CIdentifier<String, T> fileName() {
        return ((file, config) -> file.getName().replaceFirst("\\.(yml|yaml)$", ""));
    }

    /**
     * Parses file name as UUID.
     */
    static <T> CIdentifier<UUID, T> fileNameUUID() {
        return ((file, config) -> UUID.fromString(file.getName().replaceFirst("\\.(yml|yaml)$", "")));
    }

    /**
     * Auto-incrementing integer IDs.
     */
    static <T> CIdentifier<Integer, T> simpleID(AtomicInteger counter) {
        return ((file, config) -> counter.getAndIncrement());
    }
}
