package me.verschuls.ylf;

import java.io.File;

/**
 * File filter for CMI. Return true to exclude a file from loading.
 *
 * @param <T> config type
 */
@FunctionalInterface
public interface CFilter<T> {

    /**
     * Returns true to exclude this file.
     */
    boolean filter(File file, T config);

    /**
     * No filtering - loads all files.
     */
    static <T> CFilter<T> none() {
        return ((file, config) -> false);
    }

    /**
     * Excludes files matching {@code _name_.yml} pattern.
     */
    static <T> CFilter<T> underScores() {
        return ((file, config) -> file.getName().startsWith("_") && file.getName().endsWith("_.yml"));
    }
}
