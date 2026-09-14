package net.kdt.pojavlaunch.server;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.*;

/** Bounded extraction into a new directory; used for APK data and save restores. */
public final class SafeZip {
    public static void extract(InputStream input, File directory, long limit) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create " + directory);
        String prefix = directory.getCanonicalPath() + File.separator;
        Set<String> seen = new HashSet<>();
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry entry;
            byte[] buffer = new byte[65536];
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > 20000) throw new IOException("Too many archive entries");
                String name = entry.getName();
                File target = new File(directory, name);
                if (name.startsWith("/") || name.contains("\\") || !target.getCanonicalPath().startsWith(prefix)
                        || !seen.add(target.getCanonicalPath())) throw new IOException("Unsafe archive entry: " + name);
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Cannot create directory");
                    continue;
                }
                if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) throw new IOException("Cannot create directory");
                try (OutputStream out = new FileOutputStream(target)) {
                    int n;
                    while ((n = zip.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled");
                        total += n;
                        if (total > limit) throw new IOException("Archive is too large");
                        out.write(buffer, 0, n);
                    }
                }
            }
        }
    }

    public static void delete(File file) throws IOException {
        if (!file.exists()) return;
        // Never follow a link into the installed native libraries.
        if (java.nio.file.Files.isSymbolicLink(file.toPath())) {
            java.nio.file.Files.delete(file.toPath());
            return;
        }
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        if (!file.delete()) throw new IOException("Cannot remove " + file);
    }

    private SafeZip() {}
}
