package me.verschuls.ylf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for specifying config version requirements.
 * When the file version doesn't match, a backup is created and the config is updated.
 *
 * <p>Apply to a {@link BaseConfig.Data} subclass to enable version checking:</p>
 * <pre>{@code
 * @CVersion("1.2")
 * public class MyConfig extends BaseConfig.Data {
 *     // ...
 * }
 * }</pre>
 *
 * @see VersionCompare
 * @see BaseConfig
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CVersion {


    /**
     * The expected version value. If the file version differs,
     * a backup is created and the config is updated to the new schema.
     *
     * @return the required version string
     */
    String value();
}
