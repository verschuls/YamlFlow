package me.verschuls.ylf;

import de.exlll.configlib.Configuration;

/**
 * Base class for config data. Extend this and annotate with {@link Configuration}.
 * Used by both {@link BaseConfig} and {@link CMI} for versioning support.
 *
 * @see CVersion
 */
@Configuration
public abstract class BaseData {
    public BaseData() {}
    /** Internal version field, managed automatically when using {@link CVersion}. */
    String version = "";
}
