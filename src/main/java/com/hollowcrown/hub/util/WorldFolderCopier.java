package com.hollowcrown.hub.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Copies / deletes server world folders (session.lock / uid.dat skipped on copy).
 */
public final class WorldFolderCopier {

    private WorldFolderCopier() {
    }

    public static void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path src : walk.toList()) {
                Path relative = source.relativize(src);
                Path dst = target.resolve(relative.toString());
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dst);
                } else {
                    String fileName = src.getFileName().toString();
                    if ("session.lock".equalsIgnoreCase(fileName) || "uid.dat".equalsIgnoreCase(fileName)) {
                        continue;
                    }
                    Files.createDirectories(dst.getParent());
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    public static void deleteDirectory(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    /**
     * @return true if directory gone or did not exist
     */
    public static boolean deleteDirectoryBestEffort(Path root, int maxAttempts, long sleepMs) {
        for (int i = 0; i < maxAttempts; i++) {
            try {
                deleteDirectory(root);
                if (!Files.exists(root)) {
                    return true;
                }
            } catch (IOException ignored) {
            }
            try {
                Thread.sleep(Math.max(0, sleepMs));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !Files.exists(root);
    }
}
