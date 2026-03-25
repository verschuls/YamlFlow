package me.verschuls.ylf;

import de.exlll.configlib.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Base class for YAML configurations with hash-based change detection.
 * Extend this class and define a nested {@link BaseData} class with your config fields.
 * Register with {@link CM#register(BaseConfig)}.
 *
 * @param <T> the data type extending {@link BaseData}
 * @see CM
 */
public abstract class BaseConfig<T extends BaseData> {

    private final Path path;
    private final String name;
    private final Path file;
    private final Class<T> dataClass;
    private Executor executor = null;
    private final YamlConfigurationProperties properties;
    private String configVersion;
    private String backUpDir = "old";
    private volatile T instance;
    private volatile byte[] fileHash;
    private final List<Consumer<T>> reloadConsumers = new CopyOnWriteArrayList<>();
    private final CompletableFuture<BaseConfig<T>> init = new CompletableFuture<>();

    private final Object ioLock = new Object();

    /**
     * Creates a new config. Loads from {@code path/name.yml}, creating with defaults if missing.
     *
     * @param path      directory for the config file
     * @param name      file name without .yml extension
     * @param dataClass the data class
     */
    public BaseConfig(Path path, String name, Class<T> dataClass, Executor executor) {
        this.path = path;
        this.name = name;
        this.file = path.resolve(name+".yml");
        this.dataClass = dataClass;
        this.executor = executor;
        YamlConfigurationProperties.Builder<?> builder = YamlConfigurationProperties.newBuilder();
        if (dataClass.isAnnotationPresent(Header.class))
            builder.header(dataClass.getAnnotation(Header.class).value());
        if (dataClass.isAnnotationPresent(Footer.class))
            builder.footer(dataClass.getAnnotation(Footer.class).value());
        if (dataClass.isAnnotationPresent(CVersion.class)) {
            configVersion = dataClass.getAnnotation(CVersion.class).value();
            backUpDir = dataClass.getAnnotation(CVersion.class).backupDir();
        }
        if (dataClass.isAnnotationPresent(NullPolicy.class)) {
            switch (dataClass.getAnnotation(NullPolicy.class).value()) {
                case FULL -> {
                    builder.inputNulls(true);
                    builder.outputNulls(true);
                }
                case INPUT -> builder.inputNulls(true);
                case OUTPUT -> builder.outputNulls(true);
            }
        }
        serializers().forEach((k, v)->registerSerializer(builder, k, v));
        serializersFactory().forEach((k, v)->registerFactory(builder, k, v));
        builder.setNameFormatter(nameFormatter());
        builder.setFieldFilter(field -> !(field.getName().equals("version") && configVersion == null) && fieldFilter().test(field));
        advanced(builder);
        this.properties = builder.build();
        if (dataClass.isAnnotationPresent(ResourceFile.class)) {
            try (InputStream stream = CM.getResource()) {
                if (Files.notExists(file)) Files.copy(stream, file);
            } catch (IOException e) {
                throw new RuntimeException();
            }
        }
        this.instance = YLFUtils.loadConfig(file, path, dataClass, properties, CM.getVersionCompare(), configVersion, backUpDir);
        try {
            this.fileHash = calculateFileHash();
        } catch (Exception e) {
            throw new RuntimeException("Wasn't able to calculate hash for config file: "+name, e);
        }
        init.complete(this);
    }

    @SuppressWarnings("unchecked")
    private static <S> void registerSerializer(YamlConfigurationProperties.Builder<?> builder, Class<?> clazz, Serializer<?, ?> serializer) {
        builder.addSerializer((Class<S>) clazz, (Serializer<? super S, ?>) serializer);
    }

    @SuppressWarnings("unchecked")
    private static <S> void registerFactory(YamlConfigurationProperties.Builder<?> builder, Class<?> clazz, Function<? super SerializerContext, ? extends Serializer<?, ?>> factory) {
        builder.addSerializerFactory((Class<S>) clazz,  (Function<? super SerializerContext, ? extends Serializer<S, ?>>) factory);
    }

    /**
     * Override to provide custom serializers for specific types.
     *
     * <p>Example:</p>
     * <pre>{@code
     * @Override
     * protected Map<Class<?>, Serializer<?, ?>> serializers() {
     *     return Map.of(UUID.class, new UUIDSerializer());
     * }
     * }</pre>
     *
     * @return a map of types to their serializers (default: empty)
     * @see #serializersFactory()
     */
    protected Map<Class<?>, Serializer<?, ?>> serializers() {
        return Map.of();
    }

    /**
     * Override to provide serializer factories for specific types.
     * Factories receive a {@link SerializerContext} and produce a serializer,
     * useful when serializers need context about the field being serialized.
     *
     * <p>Example:</p>
     * <pre>{@code
     * @Override
     * protected Map<Class<?>, Function<? super SerializerContext, ? extends Serializer<?, ?>>> serializersFactory() {
     *     return Map.of(MyType.class, ctx -> new MyTypeSerializer(ctx));
     * }
     * }</pre>
     *
     * @return a map of types to their serializer factories (default: empty)
     * @see #serializers()
     */
    protected Map<Class<?>, Function<? super SerializerContext, ? extends Serializer<?, ?>>> serializersFactory() {
        return Map.of();
    }

    /**
     * Override to customize how field names are formatted in the YAML file.
     *
     * <p>Example (camelCase to kebab-case):</p>
     * <pre>{@code
     * @Override
     * protected NameFormatter nameFormatter() {
     *     return NameFormatters.LOWER_KEBAB_CASE;
     * }
     * }</pre>
     *
     * @return the name formatter (default: identity, no transformation)
     */
    protected NameFormatter nameFormatter() {
        return (s)->s;
    }

    /**
     * Override to filter which fields are included in the YAML config.
     * Fields that do not pass the predicate are excluded from serialization.
     *
     * <p>Example (exclude transient fields):</p>
     * <pre>{@code
     * @Override
     * protected Predicate<Field> fieldFilter() {
     *     return field -> !Modifier.isTransient(field.getModifiers());
     * }
     * }</pre>
     *
     * @return a predicate that returns {@code true} for fields to include (default: all fields)
     */
    protected Predicate<Field> fieldFilter() {
        return (f)->true;
    }

    /**
     * Override for direct access to the configuration properties builder.
     * Use this for advanced customization not covered by the other override methods.
     *
     * <p>Example:</p>
     * <pre>{@code
     * @Override
     * protected void advanced(YamlConfigurationProperties.Builder<?> properties) {
     *     properties.charset(StandardCharsets.UTF_16);
     * }
     * }</pre>
     *
     * @param properties the builder to modify
     * @see #serializers()
     * @see #nameFormatter()
     * @see #fieldFilter()
     */
    protected void advanced(YamlConfigurationProperties.Builder<?> properties) {}

    private byte[] calculateFileHash() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(Files.readAllBytes(file));
    }

    final T get() {
        return instance;
    }

    final void reload() {
        synchronized (ioLock) {
            try {
                byte[] newHash = calculateFileHash();
                if (Arrays.equals(fileHash, newHash)) return;
                fileHash = newHash;
                this.instance = YLFUtils.loadConfig(file, path, dataClass, properties, CM.getVersionCompare(), configVersion, backUpDir);
                for (Consumer<T> consumer : reloadConsumers)
                    consumer.accept(instance);
            } catch (Exception e) {
                throw new RuntimeException("Wasn't able to check config hash'es. Fallback to old file hash logic.", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    final <K extends BaseData> void onReload(Consumer<K> onReload) {
        reloadConsumers.add((Consumer<T>) onReload);
    }

    final CompletableFuture<BaseConfig<T>> onInit() {
        return init;
    }

    final Executor getExecutor() {
        return executor;
    }


    protected final void save() {
        synchronized (ioLock) {
            YamlConfigurations.save(file, dataClass, instance, properties);
            try {
                this.fileHash = calculateFileHash();
            } catch (Exception e) {
                throw new RuntimeException("Wasn't able to calculate hash after save", e);
            }
        }
    }

}
