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
    <version>v1.2.0</version>
</dependency>
```

### Gradle

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.verschuls:YamlFlow:v1.2.0'
}
```

## Features

- **Hash-based reloading** - Only reloads when file content changes
- **Async initialization** - Non-blocking config loading with CompletableFuture
- **Reload callbacks** - React to config changes
- **Thread-safe** - All operations are properly synchronized
- **Bulk loading** - Load multiple configs from a directory
- **Config versioning** - Automatic backup and migration on version mismatch

## CM - Single Config Management

Use `CM` for named config files (settings.yml, messages.yml, etc.)

```java
public class ServerConfig extends BaseConfig<ServerConfig.Data> {
    public ServerConfig(Path dir, Executor executor) {
        super(dir, "server", Data.class, executor);
    }

    @Header("Server Configuration")
    @Footer("End of config")
    public static class Data extends BaseConfig.Data {
        public String host = "localhost";
        public int port = 8080;
    }
}

// Register
CM.register(new ServerConfig(Path.of("./config"), executor));

// Wait for init
CM.onInit(ServerConfig.class).thenAccept(data -> {
    System.out.println("Server: " + data.host + ":" + data.port);
});

// Get data (after init)
ServerConfig.Data data = CM.get(ServerConfig.class);

// Reload & callbacks
CM.reload(ServerConfig.class);
CM.onReload(ServerConfig.class, data -> System.out.println("Reloaded!"));
```

## Config Versioning

Add automatic version tracking and backup on version mismatch using the `@CVersion` annotation:

```java
public class VersionedConfig extends BaseConfig<VersionedConfig.Data> {
    public VersionedConfig(Path dir, Executor executor) {
        super(dir, "config", Data.class, executor);
    }

    @CVersion("1.0.0")
    public static class Data extends BaseConfig.Data {
        public String setting = "default";
    }
}
```

**Behavior:**
- On load, the file's `version` field is compared against the `@CVersion` value
- If versions don't match, the old config is backed up as `config-v1.0.0-xxxx.yml`
- The config is then updated with the new version and default values for new fields

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
public class PlayerData {
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
    .executor(executor)
    .inputNulls(true)
    .build();

// Access configs
Optional<PlayerData> player = players.get("steve");
HashMap<String, PlayerData> all = players.get();

// Create & save
PlayerData newPlayer = players.create("alex", "alex");
newPlayer.name = "Alex";
players.save("alex", newPlayer);

// Reload
players.reload();
players.onReload(all -> System.out.println("Reloaded " + all.size() + " players"));
```

### Builder Options

| Method | Description |
|--------|-------------|
| `filter(CFilter)` | Exclude files from loading |
| `executor(Executor)` | Executor for async init callback |
| `inputNulls(boolean)` | Allow null values from YAML |
| `outputNulls(boolean)` | Write null values to YAML |
| `acceptNulls(boolean)` | Shorthand for both inputNulls and outputNulls |
| `addSerializer(Class, Serializer)` | Custom type serializer |
| `addSerializerFactory(Class, Function)` | Context-aware serializer factory |
| `setFieldFilter(FieldFilter)` | Control which fields are serialized |
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
public static class Data extends BaseConfig.Data {
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

## Coming Soon

- **Async CMI** - Non-blocking bulk config loading
- **CMI Versioning** - Version support for bulk configs
- **Optimizations** - Performance improvements
- ...and more
