package me.verschuls.ylf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a config class to be loaded from the resource stream set via {@link CM#setResource(java.io.InputStream)}
 * instead of reading directly from the file system.
 *
 * <p>When a {@link BaseConfig} subclass is annotated with {@code @ResourceFile}, the configurator
 * will use the {@link java.io.InputStream} provided to {@link CM#setResource(java.io.InputStream)}
 * as the default content source (e.g. to extract a bundled default file).</p>
 *
 * @see CM#setResource(java.io.InputStream)
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourceFile { }
