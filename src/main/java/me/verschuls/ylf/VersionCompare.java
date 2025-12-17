package me.verschuls.ylf;

import java.util.regex.Pattern;

/**
 * Functional interface for comparing configuration versions.
 * Used to determine if a config file version matches the expected version.
 *
 * <p>Default implementation validates version format (e.g., "1.0", "1.2.3")
 * and performs exact equality comparison.</p>
 *
 * @see CM#setVersionComparator(VersionCompare)
 * @see CVersion
 */
@FunctionalInterface
public interface VersionCompare {

    /**
     * Pattern for validating version strings.
     * Matches formats like "1", "1.0", "1.2.3", etc.
     */
    Pattern VALID_VERSION = Pattern.compile("^\\d+(\\.\\d+)*$");

    /**
     * Compares two version strings.
     *
     * @param current the current version from the config file
     * @param target  the expected version from the annotation
     * @return true if versions match, false otherwise
     */
    boolean compare(String current, String target);

    /**
     * Returns a basic version comparator that validates format and checks equality.
     *
     * @return a comparator that returns true only if both versions are valid and equal
     */
    static VersionCompare basic() {
        return ((current, target) -> {
            if (!VALID_VERSION.matcher(target).matches()) return false;
            if (!VALID_VERSION.matcher(current).matches()) return false;
            return current.equalsIgnoreCase(target);
        });
    }
}
