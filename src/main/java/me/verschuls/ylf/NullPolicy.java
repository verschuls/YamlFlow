package me.verschuls.ylf;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for controlling null handling in YAML config serialization.
 *
 * <p>Apply to a {@link BaseData} subclass to configure null behavior:</p>
 * <pre>{@code
 * @NullPolicy(NullPolicy.Type.FULL)
 * public class MyConfig extends BaseData {
 *     // null values will be read from and written to the YAML file
 * }
 * }</pre>
 *
 * @see BaseConfig
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NullPolicy {

    /**
     * The null handling policy to apply.
     *
     * @return the null policy type
     */
    Type value();

    /**
     * Defines how null values are handled during serialization.
     */
    enum Type {
        /** Allow null values when reading from the YAML file. */
        INPUT,
        /** Allow null values when writing to the YAML file. */
        OUTPUT,
        /** Allow null values for both reading and writing. */
        FULL
    }
}
