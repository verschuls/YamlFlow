package me.verschuls.ylf.test;

import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;
import me.verschuls.ylf.BaseData;
import me.verschuls.ylf.CIdentifier;
import me.verschuls.ylf.CMI;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LoadTime {

    private static final int FILE_COUNT = 500;
    private static final Path TEST_DIR = Path.of("./test-configs");

    public static void main(String[] args) throws Exception {

        //cleanup();
        //generateTestFiles();
        //if (true) return;
        System.out.println("=== CMI Load Time Test ===\n");

        long start = System.currentTimeMillis();

        // Test sync load
        CMI<String, TestData> cmiSync = CMI.newBuilder(TEST_DIR, TestData.class, CIdentifier.fileName(), false)
                .build();
        cmiSync.onInit().thenAccept(d->{
            System.out.println("Sync load:  " + (System.currentTimeMillis()-start) + "ms. Files: "+d.size());
        });

        // Test async load
        CMI<String, TestData> cmiAsync = CMI.newBuilder(TEST_DIR, TestData.class, CIdentifier.fileName(), true)
                .build();
        cmiAsync.onInit().thenAccept(d->{
            System.out.println("ASync load:  " + (System.currentTimeMillis()-start) + "ms. Files: "+d.size());
        }).join();
    }

    private static void generateTestFiles() throws IOException {
        System.out.println("Generating " + FILE_COUNT + " test files...");
        Files.createDirectories(TEST_DIR);

        Random rand = new Random();
        List<String> names = List.of("Alice", "Bob", "Charlie", "David", "Eve", "Frank", "Grace", "Henry");
        List<String> ranks = List.of("MEMBER", "VIP", "ADMIN", "OWNER", "MOD", "HELPER");
        List<String> achievements = List.of("CardMaster", "Builder", "CatLover", "DogLover", "PotionMaster", "Enchanter", "Magician");

        for (int i = 0; i < FILE_COUNT; i++) {
            Path path = TEST_DIR.resolve("player_"+i+".yml");
            YamlConfigurations.update(path, TestData.class);
            TestData data = TestData.ofNew(names.get(rand.nextInt(names.size())) + i,
                    rand.nextInt(100), Double.parseDouble(rand.nextInt(1000)+".0"),
                    ranks.get(rand.nextInt(ranks.size())), UUID.randomUUID().toString(),
                    System.currentTimeMillis() - rand.nextLong(1000000),
                    rand.nextInt(500), new ArrayList<>(List.of(achievements.get(rand.nextInt(achievements.size())))), rand.nextBoolean(), rand.nextBoolean(), rand.nextBoolean());
            YamlConfigurations.save(path, TestData.class, data);
        }

        System.out.println("Generated " + FILE_COUNT + " files.\n");
    }


    private static void cleanup() throws IOException {
        if (Files.exists(TEST_DIR)) {
            Files.walk(TEST_DIR)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(p -> {
                        try { Files.delete(p); }
                        catch (IOException ignored) {}
                    });
        }
    }

    public static class TestData extends BaseData {
        public String name = "";
        public Integer level = 0;
        public Double balance = 0.0;
        public String rank = "MEMBER";
        public String uuid = "";
        public Long lastLogin = 0L;
        public Integer playtime = 0;
        public List<String> achievements = List.of();
        public Settings settings = new Settings();

        @Configuration
        public static class Settings {
            public Settings() {}
            public Boolean notifications = true;
            public Boolean privateMessages = true;
            public Boolean showScoreboard = true;
            private static Settings ofNew(Boolean notifications, Boolean privateMessages, Boolean showScoreboard) {
                Settings s = new Settings();
                s.notifications = notifications;
                s.privateMessages = privateMessages;
                s.showScoreboard = showScoreboard;
                return s;
            }
        }
        public static TestData ofNew(String name, Integer level, Double balance, String rank, String uuid, Long lastLogin, Integer playtime, List<String> achievements, Boolean notifications, Boolean privateMessages, Boolean showScoreboard) {
            TestData td = new TestData();
            td.name = name;
            td.level = level;
            td.balance = balance;
            td.rank = rank;
            td.uuid = uuid;
            td.lastLogin = lastLogin;
            td.playtime = playtime;
            td.achievements = achievements;
            td.settings = Settings.ofNew(notifications, privateMessages, showScoreboard);
            return td;
        }
    }
}
