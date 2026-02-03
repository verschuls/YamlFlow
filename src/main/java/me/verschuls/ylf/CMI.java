package me.verschuls.ylf;

import de.exlll.configlib.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Instance-based config manager for bulk loading YAML files from a directory.
 * Unlike {@link CM}, loads multiple files using the same data class.
 *
 * <p>Use the {@link Builder} to construct instances with customizable options
 * for filtering, versioning, serialization, and null handling.</p>
 *
 * <pre>{@code
 * CMI<String, PlayerData> players = CMI.newBuilder(
 *         Path.of("./players"),
 *         PlayerData.class,
 *         CIdentifier.fileName()
 *     )
 *     .filter(CFilter.underScores())
 *     .setVersionCompare(VersionCompare.basic())
 *     .build();
 * }</pre>
 *
 * @param <DataKey>   key type for identifying configs (e.g., String, UUID, Integer)
 * @param <DataClass> the config data class (must have {@code @Configuration} annotation and extend {@link BaseData})
 * @see CM
 * @see CIdentifier
 * @see CFilter
 * @see CVersion
 */
public final class CMI<DataKey, DataClass extends BaseData> {

    private final Map<DataKey, ConfigInfo<DataClass>> configs = new ConcurrentHashMap<>();
    private final CompletableFuture<HashMap<DataKey, ConfigInfo<DataClass>>> init = new CompletableFuture<>();
    private final List<Consumer<HashMap<DataKey, ConfigInfo<DataClass>>>> reload = new CopyOnWriteArrayList<>();
    private final Path path;
    private final Class<DataClass> parseClass;
    private final CIdentifier<DataKey, DataClass> identifier;
    private final CFilter<DataClass> filter;
    private String configVersion;
    private String backUpDir = "old";
    private VersionCompare versionCompare = VersionCompare.basic();

    private final YamlConfigurationProperties properties;

    /**
     * Private constructor. Use {@link #newBuilder(Path, Class, CIdentifier)} to create instances.
     *
     * @param builder the builder containing configuration options
     * @throws IOException if directory creation or file loading fails
     */
    private CMI(Builder<DataKey, DataClass> builder) throws IOException {
        if (!builder.parseClass.isAnnotationPresent(Configuration.class)) {
            throw new RuntimeException("Data class in CMI must annotate Configuration from exlll ConfigLib");
        }
        this.parseClass = builder.parseClass;
        YamlConfigurationProperties.Builder<?> properties = builder.properties;
        if (parseClass.isAnnotationPresent(Header.class))
            properties.header(parseClass.getAnnotation(Header.class).value());
        if (parseClass.isAnnotationPresent(Footer.class))
            properties.footer(parseClass.getAnnotation(Footer.class).value());
        if (parseClass.isAnnotationPresent(CVersion.class)) {
            configVersion = parseClass.getAnnotation(CVersion.class).value();
            backUpDir = parseClass.getAnnotation(CVersion.class).backupDir();
        }
        if (builder.fieldfilter == null)
            properties.setFieldFilter(field -> !(field.getName().equals("version") && configVersion == null));
        else
            properties.setFieldFilter(field -> (!(field.getName().equals("version") && configVersion == null)) && builder.fieldfilter.test(field));
        this.properties = properties.build();
        this.identifier = builder.identifier;
        this.filter = builder.filter;
        if (versionCompare != null)
            this.versionCompare = builder.versionCompare;
        this.path = builder.path;
        if (Files.notExists(path)) Files.createDirectory(path);
        load();
        init.completeAsync(this::get);
    }

    /**
     * Creates a new builder for constructing a CMI instance.
     *
     * @param path       directory containing YAML config files
     * @param parseClass the config data class (must have {@code @Configuration} annotation)
     * @param identifier strategy for generating keys from files
     * @param <DataKey>   key type for identifying configs
     * @param <DataClass> the config data class type
     * @return a new builder instance
     */
    public static <DataKey, DataClass extends BaseData> Builder<DataKey, DataClass> newBuilder(Path path, Class<DataClass> parseClass, CIdentifier<DataKey, DataClass> identifier) {
        return new Builder<>(path, parseClass, identifier);
    }

    private synchronized void load() throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.getFileName().toString().equalsIgnoreCase(backUpDir))
                    return FileVisitResult.SKIP_SIBLINGS;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
                if (Files.isRegularFile(p) && p.toString().endsWith(".yml"))
                    loadSingle(p);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void loadSingle(Path yaml) {
        try {
            DataClass data_ = YamlConfigurations.load(yaml, parseClass);
            if (filter.filter(yaml.toFile(), data_)) return;
            DataClass data = YLFUtils.loadConfig(yaml, path, parseClass, properties, versionCompare, configVersion, backUpDir);
            DataKey id = identifier.identify(yaml.toFile(), data);
            configs.put(id, ConfigInfo.of(data, yaml));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load: " + yaml.getFileName(), e);
        }
    }

    /**
     * Returns a future that completes when all configs are initially loaded.
     *
     * @return future containing a snapshot of all loaded configs
     */
    public CompletableFuture<HashMap<DataKey, ConfigInfo<DataClass>>> onInit() {
        return init;
    }

    /**
     * Returns a snapshot of all currently loaded configs.
     *
     * @return a new HashMap containing all loaded configs
     */
    public HashMap<DataKey, ConfigInfo<DataClass>> get() {
        return new HashMap<>(configs);
    }

    /**
     * Gets a config by its key.
     *
     * @param key the key identifying the config
     * @return an Optional containing the config, or empty if not found
     */
    public Optional<DataClass> get(DataKey key) {
        return Optional.ofNullable(configs.get(key).getData());
    }

    /**
     * Gets the full config info (data + path) for a key.
     *
     * @param key the key identifying the config
     * @return an Optional containing the ConfigInfo, or empty if not found
     */
    public Optional<ConfigInfo<DataClass>> getInfo(DataKey key) {
        return Optional.ofNullable(configs.get(key));
    }

    /**
     * Finds all configs matching the given predicate.
     *
     * @param predicate condition to test each config
     * @return list of configs that match the predicate
     */
    public List<DataClass> getWhere(Predicate<ConfigInfo<DataClass>> predicate) {
        List<DataClass> dataList = new ArrayList<>();
        for (ConfigInfo<DataClass> config : configs.values())
            if (predicate.test(config))
                dataList.add(config.getData());
        return dataList;
    }

    /**
     * Creates or retrieves a config. If the key doesn't exist, creates a new file
     * with default values and registers it.
     *
     * @param key  the key to identify the config
     * @param name the file name without .yml extension
     * @return the existing or newly created config instance
     */
    public ConfigInfo<DataClass> create(DataKey key, String name) {
        Path path = this.path.resolve(name+".yml");
        if (configs.containsKey(key)) return configs.get(key);
        return configs.computeIfAbsent(key, key_ -> ConfigInfo.of(YamlConfigurations.update(path, parseClass, properties), path));
    }

    /**
     * Saves a config to disk. Only saves if the key exists in the manager.
     *
     * @param key  the key identifying the config
     * @param data the config data to save
     */
    public synchronized void save(DataKey key, DataClass data) {
        if (!configs.containsKey(key)) return;
        YamlConfigurations.save(configs.get(key).getPath(), parseClass, data, properties);
        configs.replace(key, ConfigInfo.of(data, configs.get(key).getPath()));
    }

    /**
     * Reloads all configs from the directory. Clears current configs and rescans
     * the directory, then notifies all registered reload callbacks.
     *
     * @throws RuntimeException if reloading fails
     */
    public synchronized void reload() {
        try {
            configs.clear();
            load();
            for (Consumer<HashMap<DataKey, ConfigInfo<DataClass>>> consumer : reload)
                consumer.accept(new HashMap<>(configs));
        } catch (IOException e) {
            throw new RuntimeException("Error while reloading configs in '"+path.toString()+"'",e);
        }
    }

    /**
     * Registers a callback to be invoked after each reload.
     *
     * @param consumer callback receiving a snapshot of all configs after reload
     */
    public void onReload(Consumer<HashMap<DataKey, ConfigInfo<DataClass>>> consumer) {
        reload.add(consumer);
    }


    /**
     * Builder for constructing {@link CMI} instances with customizable options.
     *
     * <p>Provides fluent methods for configuring filtering, versioning, null handling,
     * custom serializers, field filters, and name formatters.</p>
     *
     * <pre>{@code
     * CMI<String, PlayerData> players = CMI.newBuilder(path, PlayerData.class, CIdentifier.fileName())
     *     .filter(CFilter.underScores())
     *     .setVersionCompare(VersionCompare.basic())
     *     .inputNulls(true)
     *     .addSerializer(UUID.class, new UUIDSerializer())
     *     .build();
     * }</pre>
     *
     * @param <DataKey>   key type for identifying configs
     * @param <DataClass> the config data class type
     */
    public static class Builder<DataKey, DataClass extends BaseData> {

        private final Path path;
        private final Class<DataClass> parseClass;
        private final CIdentifier<DataKey, DataClass> identifier;
        private CFilter<DataClass> filter = CFilter.none();
        private VersionCompare versionCompare;
        private Predicate<Field> fieldfilter;
        private final YamlConfigurationProperties.Builder<?> properties = YamlConfigurationProperties.newBuilder();

        private Builder(Path path, Class<DataClass> parseClass, CIdentifier<DataKey, DataClass> identifier) {
            this.path = path;
            this.parseClass = parseClass;
            this.identifier = identifier;
        }

        /**
         * Sets the filter for excluding files from loading.
         *
         * @param filter the filter to apply (return true to exclude)
         * @return this builder
         * @see CFilter
         */
        public Builder<DataKey, DataClass> filter(CFilter<DataClass> filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Sets a custom version comparator for this CMI instance.
         * If not set, uses {@link VersionCompare#basic()}.
         *
         * @param compare the version comparator
         * @return this builder
         * @see VersionCompare
         */
        public Builder<DataKey, DataClass> setVersionCompare(VersionCompare compare) {
            this.versionCompare = compare;
            return this;
        }

        /**
         * Configures whether null values from YAML are set on config fields.
         *
         * @param inputNulls true to allow null values from YAML
         * @return this builder
         */
        public Builder<DataKey, DataClass> inputNulls(boolean inputNulls) {
            this.properties.inputNulls(inputNulls);
            return this;
        }

        /**
         * Configures whether null field values are written to YAML.
         *
         * @param outputNulls true to write null values to YAML
         * @return this builder
         */
        public Builder<DataKey, DataClass> outputNulls(boolean outputNulls) {
            this.properties.outputNulls(outputNulls);
            return this;
        }

        /**
         * Convenience method to set both inputNulls and outputNulls.
         *
         * @param acceptNulls true to allow nulls in both directions
         * @return this builder
         */
        public Builder<DataKey, DataClass> acceptNulls(boolean acceptNulls) {
            this.properties.outputNulls(acceptNulls);
            this.properties.inputNulls(acceptNulls);
            return this;
        }

        /**
         * Adds a custom serializer for a specific type.
         *
         * @param serializedType the class type to serialize
         * @param serializer     the serializer instance
         * @param <T>            the type being serialized
         * @return this builder
         */
        public <T> Builder<DataKey, DataClass> addSerializer(Class<T> serializedType, Serializer<? super T, ?> serializer) {
            this.properties.addSerializer(serializedType, serializer);
            return this;
        }

        /**
         * Adds a serializer factory for context-aware serialization.
         *
         * @param serializedType    the class type to serialize
         * @param serializerFactory factory that creates serializers with context
         * @param <T>               the type being serialized
         * @return this builder
         */
        public <T> Builder<DataKey, DataClass> addSerializerFactory(Class<T> serializedType, Function<? super SerializerContext, ? extends Serializer<T, ?>> serializerFactory) {
            this.properties.addSerializerFactory(serializedType, serializerFactory);
            return this;
        }

        /**
         * Sets a filter to control which fields are included in serialization.
         *
         * @param filter the field filter
         * @return this builder
         */
        public Builder<DataKey, DataClass> setFieldFilter(Predicate<Field> filter) {
            this.fieldfilter = filter;
            return this;
        }

        /**
         * Sets a formatter for converting field names to YAML keys.
         *
         * @param formatter the name formatter
         * @return this builder
         */
        public Builder<DataKey, DataClass> setNameFormatter(NameFormatter formatter) {
            this.properties.setNameFormatter(formatter);
            return this;
        }

        /**
         * Builds the CMI instance. Loads all matching YAML files from the directory.
         *
         * @return the constructed CMI instance
         * @throws IOException if directory creation or file loading fails
         */
        public CMI<DataKey, DataClass> build() throws IOException {
            return new CMI<>(this);
        }
    }
}