package net.kdt.pojavlaunch.server;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.zip.*;

/** Backups hold the same OS file lock as the JVM. Restore keeps the previous world. */
public final class WorldBackup {
    public static void exportWorld(ServerFiles files, OutputStream output) throws Exception {
        try (RandomAccessFile f = new RandomAccessFile(files.lock, "rw"); FileChannel ch = f.getChannel()) {
            FileLock lock = ch.tryLock();
            if (lock == null) throw new IOException("Save and stop the server before backing up.");
            try {
                if (!new File(files.world, ".world-version").isFile()) throw new IOException("Start the server once to prepare a world.");
                try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) { add(zip, files.world, files.world); }
            } finally { lock.release(); }
        }
    }

    private static void add(ZipOutputStream zip, File root, File file) throws IOException {
        if (Files.isSymbolicLink(file.toPath())) throw new IOException("Unexpected link in world data");
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot read world directory");
            for (File child : children) add(zip, root, child);
        } else {
            zip.putNextEntry(new ZipEntry(root.toPath().relativize(file.toPath()).toString()));
            try (InputStream input = new FileInputStream(file)) { ServerFiles.copy(input, zip); }
            zip.closeEntry();
        }
    }

    public static void restore(ServerFiles files, InputStream input) throws Exception {
        try (RandomAccessFile f = new RandomAccessFile(files.lock, "rw"); FileChannel ch = f.getChannel()) {
            FileLock lock = ch.tryLock();
            if (lock == null) throw new IOException("Save and stop the server before restoring.");
            File stage = new File(files.root, "world-restore.pending");
            File previous = new File(files.root, "world-before-restore-" + System.currentTimeMillis());
            boolean moved = false;
            try {
                SafeZip.delete(stage);
                SafeZip.extract(input, stage, 2L * 1024 * 1024 * 1024);
                if (!ServerFiles.readFile(new File(stage, ".world-version")).trim().equals(files.manifest.getString("worldVersion")))
                    throw new IOException("This backup belongs to a different server version.");
                if (!new File(stage, "data/cache/main_file_cache.dat2").isFile() || !new File(stage, "worldprops/default.conf").isFile())
                    throw new IOException("The backup is incomplete.");
                // Restore data only; the app's pinned local/headless settings remain authoritative.
                File config = new File(files.world, "worldprops/default.conf");
                if (config.isFile()) ServerFiles.write(new File(stage, "worldprops/default.conf"), ServerFiles.readFile(config));
                if (files.world.exists()) { Files.move(files.world.toPath(), previous.toPath(), StandardCopyOption.ATOMIC_MOVE); moved = true; }
                try { Files.move(stage.toPath(), files.world.toPath(), StandardCopyOption.ATOMIC_MOVE); }
                catch (Exception e) { if (moved) Files.move(previous.toPath(), files.world.toPath(), StandardCopyOption.ATOMIC_MOVE); throw e; }
            } finally { SafeZip.delete(stage); lock.release(); }
        }
    }
    private WorldBackup() {}
}
