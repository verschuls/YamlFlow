package me.verschuls.ylf;

import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
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
    private final Class<T> tClass;
    private final YamlConfigurationProperties properties;
    private volatile T instance;
    private volatile byte[] fileHash;
    private final List<Consumer<T>> reloadConsumers = new CopyOnWriteArrayList<>();
    private final CompletableFuture<BaseConfig<T>> init = new CompletableFuture<>();

    private final Executor executor;
    private final Object ioLock = new Object();

    /**
     * Creates a new config. Loads from {@code path/name.yml}, creating with defaults if missing.
     *
     * @param path     directory for the config file
     * @param name     file name without .yml extension (null to use path as full file path)
     * @param tClass   the data class
     * @param executor executor for async callbacks
     */
    public BaseConfig(Path path, String name, Class<T> tClass, Executor executor) {
        this.executor = executor;
        if (name == null) this.path = path;
        else this.path = path.resolve(name+".yml");
        this.tClass = tClass;
        YamlConfigurationProperties.Builder<?> builder = YamlConfigurationProperties.newBuilder();
        if (tClass.isAnnotationPresent(Header.class))
            builder.header(tClass.getAnnotation(Header.class).value());
        if (tClass.isAnnotationPresent(Footer.class))
            builder.footer(tClass.getAnnotation(Footer.class).value());
        this.properties = builder.build();
        this.instance = load();
        try {
            this.fileHash = calculateFileHash();
        } catch (Exception e) {
            throw new RuntimeException("Wasn't able to calculate hash for config file: "+name, e);
        }
        init.complete(this);
    }

    private T load() {
        return YamlConfigurations.update(path, tClass, properties);
    }

    private byte[] calculateFileHash() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(Files.readAllBytes(path));
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
            YamlConfigurations.save(path, tClass, instance, properties);
            try {
                this.fileHash = calculateFileHash();
            } catch (Exception e) {
                throw new RuntimeException("Wasn't able to calculate hash after save", e);
            }
        }
    }

    final byte[] getHash() {
        return fileHash;
    }

    final Executor getExecutor() {
        return executor;
    }

    /**
     * Base class for config data. Extend and annotate with {@link Configuration}.
     */
    @Configuration
    public static abstract class Data {
    }
}
