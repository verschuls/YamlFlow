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
        this.file = path.resolve(name+".yml");;
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
        builder.setFieldFilter(field -> !(field.getName().equals("version") && configVersion == null));
        this.properties = builder.build();
        this.instance = YLFUtils.loadConfig(file, path, dataClass, properties, CM.getVersionCompare(), configVersion, backUpDir);
        try {
            this.fileHash = calculateFileHash();
        } catch (Exception e) {
            throw new RuntimeException("Wasn't able to calculate hash for config file: "+name, e);
        }
        init.complete(this);
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
