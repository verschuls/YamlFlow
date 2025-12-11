package me.verschuls.ylf;

import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Instance-based config manager for bulk loading YAML files from a directory.
 * Unlike {@link CM}, loads multiple files using the same data class.
 *
 * @param <DataKey>   key type for identifying configs
 * @param <DataClass> the data class for parsing
 * @see CM
 */
public class CMI<DataKey, DataClass> {

    private final Map<DataKey, DataClass> configs = new ConcurrentHashMap<>();
    private final Map<DataKey, Path> paths = new ConcurrentHashMap<>();
    private final CompletableFuture<HashMap<DataKey ,DataClass>> init = new CompletableFuture<>();
    private final List<Consumer<HashMap<DataKey, DataClass>>> reload = new CopyOnWriteArrayList<>();
    private final Path path;
    private final Class<DataClass> parseClass;
    private final CIdentifier<DataKey, DataClass> identifier;
    private final CFilter<DataClass> filter;

    private final YamlConfigurationProperties properties;

    /**
     * Loads all .yml files from a directory. Creates directory if missing.
     *
     * @param path       directory containing YAML files
     * @param parseClass data class (must have @Configuration)
     * @param identifier key generator for each config
     * @param filter     filter to exclude files (return true to skip)
     * @param executor   executor for async init callback
     * @throws IOException if loading fails
     */
    public CMI(Path path, Class<DataClass> parseClass, CIdentifier<DataKey, DataClass> identifier, CFilter<DataClass> filter, Executor executor) throws IOException {
        if (!parseClass.isAnnotationPresent(Configuration.class)) {
            throw new RuntimeException("Data class in CMI must annotate Configuration from exlll ConfigLib");
        }
        this.parseClass = parseClass;
        YamlConfigurationProperties.Builder<?> builder = YamlConfigurationProperties.newBuilder().outputNulls(true).inputNulls(true);
        if (parseClass.isAnnotationPresent(Header.class))
            builder.header(parseClass.getAnnotation(Header.class).value());
        if (parseClass.isAnnotationPresent(Footer.class))
            builder.footer(parseClass.getAnnotation(Footer.class).value());
        this.properties = builder.build();
        this.identifier = identifier;
        this.filter = filter;
        this.path = path;
        if (Files.notExists(path)) Files.createDirectory(path);
        load();
        init.completeAsync(this::get, executor);
    }

    private synchronized void load() throws IOException {
        try (var stream = Files.walk(path)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".yml"))
                    .forEach(yaml -> {
                        DataClass data = YamlConfigurations.load(yaml, parseClass, properties);
                        if (!filter.filter(yaml.toFile(), data)) {
                            DataKey id = identifier.identify(yaml.toFile(), data);
                            configs.put(id, data);
                            paths.put(id, yaml);
                        }
                    });
        }
    }

    /**
     * Future that completes when all configs are loaded.
     */
    public CompletableFuture<HashMap<DataKey, DataClass>> onInit() {
        return init;
    }

    /**
     * Returns a snapshot of all loaded configs.
     */
    public HashMap<DataKey, DataClass> get() {
        return new HashMap<>(configs);
    }

    /**
     * Gets a config by key.
     */
    public Optional<DataClass> get(DataKey key) {
        return Optional.ofNullable(configs.get(key));
    }

    /**
     * Finds all configs matching a predicate.
     */
    public List<DataClass> getWhere(Predicate<DataClass> predicate) {
        List<DataClass> dataList = new ArrayList<>();
        for (DataClass data : configs.values())
            if (predicate.test(data))
                dataList.add(data);
        return dataList;
    }

    /**
     * Creates or gets a config. Creates file if missing.
     */
    public DataClass create(DataKey key, String name) {
        Path path = this.path.resolve(name+".yml");
        if (configs.containsKey(key)) return configs.get(key);
        paths.put(key, path);
        return configs.computeIfAbsent(key, key_ -> YamlConfigurations.update(path, parseClass, properties));
    }

    /**
     * Saves a config to disk.
     */
    public void save(DataKey key, DataClass data) {
        if (!configs.containsKey(key)) return;
        YamlConfigurations.save(paths.get(key), parseClass, data, properties);
        configs.replace(key, data);
    }

    /**
     * Reloads all configs from directory. Clears and rescans.
     */
    public synchronized void reload() {
        try {
            configs.clear();
            load();
            for (Consumer<HashMap<DataKey ,DataClass>> consumer : reload)
                consumer.accept(new HashMap<>(configs));
        } catch (IOException e) {
            throw new RuntimeException("Error while reloading configs in '"+path.toString()+"'",e);
        }
    }

    /**
     * Registers a reload callback.
     */
    public void onReload(Consumer<HashMap<DataKey, DataClass>> consumer) {
        reload.add(consumer);
    }

}