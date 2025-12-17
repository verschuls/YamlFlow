package me.verschuls.ylf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for adding a footer comment to the bottom of a YAML config file.
 *
 * <p>Apply to a config data class to include a footer:</p>
 * <pre>{@code
 * @Footer("End of configuration")
 * public class MyConfig extends BaseConfig.Data {
 *     // ...
 * }
 * }</pre>
 *
 * @see Header
 * @see BaseConfig
 * @see CMI
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Footer {

    /**
     * The footer comment text to append at the end of the YAML file.
     *
     * @return the footer text
     */
    String value();
}