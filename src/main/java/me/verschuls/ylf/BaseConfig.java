package me.verschuls.ylf;

import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Base class for YAML configurations with hash-based change detection.
 * Extend this class and define a nested {@link Data} class with your config fields.
 * Register with {@link CM#register(BaseConfig)}.
 *
 * @param <T> the data type extending {@link Data}
 * @see CM
 */
public abstract class BaseConfig<T extends BaseConfig.Data> {

    private final Path path;
    private final String name;
    private final Path file;
    private final Class<T> dataClass;
    private final YamlConfigurationProperties properties;
    private String configVersion;
    private volatile T instance;
    private volatile byte[] fileHash;
    private final List<Consumer<T>> reloadConsumers = new CopyOnWriteArrayList<>();
    private final CompletableFuture<BaseConfig<T>> init = new CompletableFuture<>();

    private final Executor executor;
    private final Object ioLock = new Object();

    /**
     * Creates a new config. Loads from {@code path/name.yml}, creating with defaults if missing.
     *
     * @param path      directory for the config file
     * @param name      file name without .yml extension
     * @param dataClass the data class
     * @param executor  executor for async callbacks - can be null
     */
    public BaseConfig(Path path, String name, Class<T> dataClass, Executor executor) {
        this.executor = executor;
        this.path = path;
        this.name = name;
        this.file = path.resolve(name+".yml");;
        this.dataClass = dataClass;
        YamlConfigurationProperties.Builder<?> builder = YamlConfigurationProperties.newBuilder();
        if (dataClass.isAnnotationPresent(Header.class))
            builder.header(dataClass.getAnnotation(Header.class).value());
        if (dataClass.isAnnotationPresent(Footer.class))
            builder.footer(dataClass.getAnnotation(Footer.class).value());
        if (dataClass.isAnnotationPresent(CVersion.class))
            configVersion = dataClass.getAnnotation(CVersion.class).value();
        builder.setFieldFilter(field -> !(field.getName().equals("version") && configVersion == null));
        this.properties = builder.build();
        this.instance = load();
        try {
            this.fileHash = calculateFileHash();
        } catch (Exception e) {
            throw new RuntimeException("Wasn't able to calculate hash for config file: "+name, e);
        }
        init.complete(this);
    }

    private static String getVersion(Path file) throws IOException {
        try (var lines = Files.lines(file)) {
            String version = lines
                    .map(String::trim)
                    .filter(line -> !line.startsWith("#"))
                    .filter(line -> line.startsWith("version:"))
                    .map(line -> line.substring(9).trim())
                    .findFirst()
                    .orElse(null);
            if (version == null) throw new RuntimeException();
            return version.replace("'", "").replace("\"", "");
        }
    }

    private T load() {
        if (!Files.exists(file)) return update();
        if (configVersion == null)
            return YamlConfigurations.update(file, dataClass, properties);
        String fileVersion;
        try {
            fileVersion = getVersion(file);
        } catch (IOException e) {
            throw new RuntimeException("Wasn't able to find version",e);
        }
        if (CM.getVersionCompare().compare(fileVersion, configVersion)) return update();
        try {
            Files.copy(file, path.resolve(name+"-v"+fileVersion+"-"+ UUID.randomUUID().toString().substring(0, 4)+".yml"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return update();
    }

    private T update() {
        if (configVersion == null)
            return YamlConfigurations.update(file, dataClass, properties);
        T data = YamlConfigurations.update(file, dataClass, properties);
        data.version = configVersion;
        YamlConfigurations.save(file, dataClass, data, properties);
        return data;
    }

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
                this.instance = load();
                for (Consumer<T> consumer : reloadConsumers)
                    consumer.accept(instance);
            } catch (Exception e) {
                throw new RuntimeException("Wasn't able to check config hash'es. Fallback to old file hash logic.", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    final <K extends BaseConfig.Data> void onReload(Consumer<K> onReload) {
        reloadConsumers.add((Consumer<T>) onReload);
    }

    final CompletableFuture<BaseConfig<T>> onInit() {
        return init;
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

    final Executor getExecutor() {
        return executor;
    }

    /**
     * Base class for config data. Extend and annotate with {@link Configuration}.
     */
    @Configuration
    public static abstract class Data {
        public Data() {}
        String version = "";
    }
}
