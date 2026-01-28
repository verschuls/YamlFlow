package me.verschuls.ylf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class YLUtils {

    public static String getVersion(Path file) throws IOException {
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
}
