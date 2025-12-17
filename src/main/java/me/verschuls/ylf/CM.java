package me.verschuls.ylf;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Centralized config manager. Thread-safe registration, retrieval, and reloading.
 *
 * @see BaseConfig
 */
public class CM {

    private static final Map<Class<?>, BaseConfig<?>> configs = new ConcurrentHashMap<>();
    private static final Map<Class<?>, CompletableFuture<BaseConfig.Data>> queueInit = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Queue<Consumer<BaseConfig.Data>>> queueReload = new ConcurrentHashMap<>();

    private static VersionCompare versionCompare = VersionCompare.basic();

    /**
     * Sets a custom version comparator for all config version checks.
     *
     * @param comparator the version comparator to use
     * @see VersionCompare
     */
    public static void setVersionComparator(VersionCompare comparator) {
        versionCompare = comparator;
    }

    /**
     * Returns the currently configured version comparator.
     *
     * @return the active version comparator
     */
    static VersionCompare getVersionCompare() {
        return versionCompare;
    }

    /**
     * Registers a config. Executes queued init/reload callbacks once loaded.
     *
     * @param config the config instance to register
     */
    public static <T extends BaseConfig<?>> void register(T config) {
        Consumer<BaseConfig<?>> handler = cfg-> {
            CompletableFuture<BaseConfig.Data> future = queueInit.remove(cfg.getClass());
            if (future != null) future.complete(cfg.get());
            if (queueReload.containsKey(cfg.getClass())) {
                Queue<Consumer<BaseConfig.Data>> reload = queueReload.get(cfg.getClass());
                while (!reload.isEmpty()) cfg.onReload(reload.poll());
            }
        };
        if (config.getExecutor() == null) config.onInit().thenAcceptAsync(handler);
        else config.onInit().thenAcceptAsync(handler, config.getExecutor());
        configs.put(config.getClass(), config);
    }

    /**
     * Checks if a config is registered.
     */
    public static <T extends BaseConfig<?>> boolean isRegistered(T config) {
        return configs.containsKey(config.getClass());
    }

    /**
     * Returns a future that completes when the config is initialized.
     * If not yet registered, completes upon registration.
     *
     * @param config the config class
     * @return future with the config data
     */
    @SuppressWarnings("unchecked")
    public static <D extends BaseConfig.Data> CompletableFuture<D> onInit(Class<? extends BaseConfig<D>> config) {
        BaseConfig<D> cfg = (BaseConfig<D>) configs.get(config);
        if (cfg != null) {
            if (cfg.getExecutor() == null) return cfg.onInit().thenApplyAsync(BaseConfig::get);
            return cfg.onInit().thenApplyAsync(BaseConfig::get, cfg.getExecutor());
        }
        else return (CompletableFuture<D>) queueInit.computeIfAbsent(config, k -> new CompletableFuture<>());
    }

    /**
     * Gets the config data synchronously. Assumes config is already initialized.
     *
     * @param config the config class
     * @return the config data
     * @throws IllegalStateException if not registered
     */
    @SuppressWarnings("unchecked")
    public static <D extends BaseConfig.Data> D get(Class<? extends BaseConfig<D>> config) {
        BaseConfig<D> cfg = (BaseConfig<D>) configs.get(config);
        if (cfg == null) throw new IllegalStateException("Config not registered: " + config.getName()   );
        return cfg.get();
    }

    /**
     * Reloads a config from disk. Skips if file unchanged (hash-based).
     *
     * @param config the config class to reload
     */
    public static synchronized void reload(Class<? extends BaseConfig<?>> config) {
        configs.get(config).reload();
    }

    /**
     * Saves the config data to disk.
     */
    public static void save(Class<? extends BaseConfig<?>> config) {
        configs.get(config).save();
    }

    /**
     * Reloads all registered configs sequentially.
     */
    public static synchronized void reloadAll() {
        for (BaseConfig<?> config : configs.values())
            config.reload();
    }

    /**
     * Registers a reload callback. Queued if config not yet registered.
     *
     * @param config   the config class
     * @param consumer callback receiving new config data on reload
     */
    @SuppressWarnings("unchecked")
    public static <D extends BaseConfig.Data> void onReload(Class<? extends BaseConfig<D>> config, Consumer<D> consumer) {
        BaseConfig<D> cfg = (BaseConfig<D>) configs.get(config);
        if (cfg != null) cfg.onReload(consumer);
        else queueReload.computeIfAbsent(config, k -> new ConcurrentLinkedQueue<>()).offer((Consumer<BaseConfig.Data>) consumer);
    }
}
