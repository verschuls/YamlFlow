package me.verschuls.ylf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for adding a header comment to the top of a YAML config file.
 *
 * <p>Apply to a config data class to include a header:</p>
 * <pre>{@code
 * @Header("MyPlugin Configuration\nModify values below to customize behavior")
 * public class MyConfig extends BaseConfig.Data {
 *     // ...
 * }
 * }</pre>
 *
 * @see Footer
 * @see BaseConfig
 * @see CMI
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Header {

    /**
     * The header comment text to prepend at the top of the YAML file.
     *
     * @return the header text
     */
    String value();
}
