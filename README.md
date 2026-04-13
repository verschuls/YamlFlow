# YamlFlow

Lightweight wrapper for [ConfigLib](https://github.com/Exlll/ConfigLib) with centralized config management. Java 21

## Installation

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.verschuls</groupId>
    <artifactId>YamlFlow</artifactId>
    <version>v1.2.7</version>
</dependency>
```

### Gradle

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.verschuls:YamlFlow:v1.2.7'
}
```

## Features

- **Hash-based reloading** - Only reloads when file content changes (SHA-256)
- **Async initialization** - Non-blocking config loading with CompletableFuture
- **Reload callbacks** - React to config changes
- **Thread-safe** - All operations are properly synchronized
- **Bulk loading** - Load multiple configs from a directory
- **Config versioning** - Automatic backup and migration on version mismatch
- **Resource files** - Load default configs from JAR resources
- **Null policies** - Annotation-driven null handling

## CM - Single Config Management

Use `CM` for named config files (settings.yml, messages.yml, etc.)

```java
public class ServerConfig extends BaseConfig<ServerConfig.Data> {
    public ServerConfig(Path dir) {
        super(dir, "server", Data.class, null);
    }

    @Header("Server Configuration")
    @Footer("End of config")
    public static class Data extends BaseData {
        public String host = "localhost";
        public int port = 8080;
    }
}

// Register
CM.register(new ServerConfig(Path.of("./config")));

// Wait for init
CM.onInit(ServerConfig.class).thenAccept(data -> {
    System.out.println("Server: " + data.host + ":" + data.port);
});

// Get data (after init)
ServerConfig.Data data = CM.get(ServerConfig.class);

// Reload & callbacks
CM.reload(ServerConfig.class);
CM.reloadAll();
CM.onReload(ServerConfig.class, data -> System.out.println("Reloaded!"));

// Save changes to disk
CM.save(ServerConfig.class);

// Check registration
CM.isRegistered(myConfig);
```

### Custom Executor

Pass an `Executor` to control which thread the async init callback runs on:

```java
public class ServerConfig extends BaseConfig<ServerConfig.Data> {
    public ServerConfig(Path dir, Executor executor) {
        super(dir, "server", Data.class, executor);
    }
    // ...
}
```

### BaseConfig Overrides

BaseConfig provides override methods for customizing serialization behavior:

```java
public class ServerConfig extends BaseConfig<ServerConfig.Data> {
    public ServerConfig(Path dir) {
        super(dir, "server", Data.class, null);
    }

    @Override
    protected Map<Class<?>, Serializer<?, ?>> serializers() {
        return Map.of(UUID.class, new UUIDSerializer());
    }

    @Override
    protected Map<Class<?>, Function<? super SerializerContext, ? extends Serializer<?, ?>>> serializersFactory() {
        return Map.of(MyType.class, ctx -> new MyTypeSerializer(ctx));
    }

    @Override
    protected NameFormatter nameFormatter() {
        return NameFormatters.LOWER_KEBAB_CASE;
    }

    @Override
    protected Predicate<Field> fieldFilter() {
        return field -> !Modifier.isTransient(field.getModifiers());
    }

    @Override
    protected void advanced(YamlConfigurationProperties.Builder<?> properties) {
        properties.charset(StandardCharsets.UTF_16);
    }

    public static class Data extends BaseData {
        public String host = "localhost";
    }
}
```

## Resource Files

Annotate a data class with `@ResourceFile` to copy a default config from JAR resources when the file doesn't exist yet. Set the class loader once at startup:

```java
// Set the class loader (e.g. from your plugin/application)
CM.setResourceLoader(getClass().getClassLoader());

public class MessagesConfig extends BaseConfig<MessagesConfig.Data> {
    public MessagesConfig(Path dir) {
        super(dir, "messages", Data.class, null);
    }

    @ResourceFile
    public static class Data extends BaseData {
        public String greeting = "Hello!";
    }
}
```

The config file path is resolved relative to the class loader's resources. If the file doesn't exist on disk, it is copied from the JAR resource before loading.

## Null Policy

Control null handling with the `@NullPolicy` annotation on your data class:

```java
@NullPolicy(NullPolicy.Type.FULL)
public static class Data extends BaseData {
    public String optional = null; // nulls read from and written to YAML
}
```

| Type | Description |
|------|-------------|
| `INPUT` | Allow null values when reading from YAML |
| `OUTPUT` | Allow null values when writing to YAML |
| `FULL` | Allow nulls in both directions |

## Config Versioning

Add automatic version tracking and backup on version mismatch using the `@CVersion` annotation. Works with both `CM` and `CMI`.

```java
public class VersionedConfig extends BaseConfig<VersionedConfig.Data> {
    public VersionedConfig(Path dir) {
        super(dir, "config", Data.class, null);
    }

    @CVersion("1.0.0")
    public static class Data extends BaseData {
        public String setting = "default";
    }
}
```

**Behavior:**
- On load, the file's `version` field is compared against the `@CVersion` value
- If versions don't match, the old config is backed up to `old/config-v1.0.0-xxxx.yml`
- The config is then updated with the new version and default values for new fields

**Custom backup directory:**
```java
@CVersion(value = "1.0.0", backupDir = "backups")
public static class Data extends BaseData {
    // backups will go to backups/config-vX.X.X-xxxx.yml
}
```

**Custom version comparison:**
```java
// Set custom comparator (called once at startup)
CM.setVersionComparator((fileVersion, configVersion) ->
    fileVersion.equals(configVersion)  // return true = versions match, skip backup
);
```

## CMI - Bulk Config Loading

Use `CMI` for loading multiple similar configs from a directory (players/, kits/, etc.)

```java
@Configuration
public class PlayerData extends BaseData {
    public String name = "Unknown";
    public int level = 1;
}

// Using the builder pattern
CMI<String, PlayerData> players = CMI.newBuilder(
        Path.of("./players"),
        PlayerData.class,
        CIdentifier.fileName()
    )
    .filter(CFilter.underScores())
    .inputNulls(true)
    .build();

// Access configs
Optional<PlayerData> player = players.get("steve");
HashMap<String, ConfigInfo<PlayerData>> all = players.get();

// Access config with path info
Optional<ConfigInfo<PlayerData>> info = players.getInfo("steve");
info.ifPresent(i -> {
    System.out.println("Path: " + i.getPath());
    System.out.println("Name: " + i.getData().name);
});

// Find configs by condition
List<PlayerData> highLevel = players.getWhere(info -> info.getData().level > 50);

// Create & save
ConfigInfo<PlayerData> newPlayer = players.create("alex", "alex");
newPlayer.getData().name = "Alex";
players.save("alex", newPlayer.getData());

// Reload
players.reload();
players.onReload(all -> System.out.println("Reloaded " + all.size() + " players"));
```

CMI also supports versioning with `@CVersion`:

```java
@Configuration
@CVersion(value = "2.0", backupDir = "old_players")
public class PlayerData extends BaseData {
    public String name = "Unknown";
    public int level = 1;
    public int xp = 0; // new field in v2.0
}

// Custom version comparator for this CMI instance
CMI<String, PlayerData> players = CMI.newBuilder(Path.of("./players"), PlayerData.class, CIdentifier.fileName())
    .setVersionCompare((fileVersion, configVersion) -> fileVersion.equals(configVersion))
    .build();
```

### Builder Options

| Method | Description |
|--------|-------------|
| `filter(CFilter)` | Exclude files from loading |
| `executor(Executor)` | Set executor for async init callback |
| `setVersionCompare(VersionCompare)` | Custom version comparator (default: `VersionCompare.basic()`) |
| `inputNulls(boolean)` | Allow null values from YAML |
| `outputNulls(boolean)` | Write null values to YAML |
| `acceptNulls(boolean)` | Shorthand for both inputNulls and outputNulls |
| `addSerializer(Class, Serializer)` | Custom type serializer |
| `addSerializerFactory(Class, Function)` | Context-aware serializer factory |
| `setFieldFilter(Predicate<Field>)` | Control which fields are serialized |
| `setNameFormatter(NameFormatter)` | Custom field-to-YAML-key naming |

## CIdentifier - Key Strategies

Control how configs are identified in CMI:

```java
// File name as key: "player.yml" -> "player"
CIdentifier.fileName()

// Parse file name as UUID: "550e8400-e29b-...yml" -> UUID
CIdentifier.fileNameUUID()

// Auto-increment IDs: 0, 1, 2, ...
CIdentifier.simpleID(new AtomicInteger())

// Custom logic
(file, config) -> config.customId
```

## CFilter - File Filtering

Filter which files to load:

```java
// Load all files
CFilter.none()

// Skip files like "_template_.yml"
CFilter.underScores()

// Custom filter (return true to exclude)
(file, config) -> file.getName().startsWith("backup")
```

## Header & Footer Annotations

Add headers/footers to generated YAML files:

```java
@Header("=== My Config ===\nEdit with care")
@Footer("Generated by YamlFlow")
public static class Data extends BaseData {
    public String value = "default";
}
```

Output:
```yaml
# === My Config ===
# Edit with care

value: default

# Generated by YamlFlow
```