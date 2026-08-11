package me.verschuls.ylf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a config class whose default file is copied out of the jar resources.
 *
 * <p>The file is copied once, only when the config file does not exist yet on disk.
 * Requires {@link CM#setResourceLoader(ClassLoader)} to be set before registering the config.</p>
 *
 * <p>The whole library uses the {@code .yml} extension, never {@code .yaml}.</p>
 *
 * @see CM#setResourceLoader(ClassLoader)
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourceFile {

    /**
     * The resource <b>folder</b> holding the default file. The file name is appended
     * automatically as the config name plus {@code .yml}, so do not include it here.
     *
     * <p>Always use {@code /} as separator (resource paths, not file system paths) and no
     * leading slash. Use an empty string for a file at the jar root.</p>
     *
     * <pre>{@code
     * // jar resource: example/config/path/settings.yml
     * @ResourceFile("example/config/path")
     * @Configuration
     * public class SettingsData extends BaseData { }
     *
     * // config name "settings" -> looks up "example/config/path/settings.yml"
     * new SettingsConfig(dataFolder, "settings", SettingsData.class);
     * }</pre>
     *
     * @return the resource folder, without file name and without leading slash
     */
    String value() default "";
}
