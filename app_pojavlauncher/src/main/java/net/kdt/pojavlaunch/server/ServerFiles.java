package net.kdt.pojavlaunch.server;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class ServerFiles {
    public final File root, world, lock, log, previousLog, runtime, engine, nativeDir;
    public final JSONObject manifest;

    public ServerFiles(Context ctx) throws Exception {
        root = new File(ctx.getFilesDir(), "singleplayer");
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create server storage");
        world = new File(root, "world");
        lock = new File(root, "world.lock");
        log = new File(root, "server.log");
        previousLog = new File(root, "server-previous.log");
        nativeDir = new File(ctx.getApplicationInfo().nativeLibraryDir);
        try (InputStream in = ctx.getAssets().open("singleplayer/manifest.json")) {
            manifest = new JSONObject(read(in));
        }
        String id = manifest.getString("id");
        if (!id.matches("[0-9a-f]{16}")) throw new IOException("Invalid package ID");
        runtime = new File(root, "runtime-" + id);
        engine = new File(root, "engine-" + id);
    }

    public interface Progress { void update(String text); }

    public void install(Context ctx, Progress progress) throws Exception {
        progress.update("Checking packaged runtime");
        JSONObject nativeHashes = manifest.getJSONObject("native");
        Iterator<String> names = nativeHashes.keys();
        while (names.hasNext()) {
            String name = names.next();
            if (!name.matches("lib[a-zA-Z0-9_]+\\.so") || !sha(new File(nativeDir, name)).equals(nativeHashes.getString(name)))
                throw new IOException("Installed native library is missing or damaged: " + name);
        }
        installArchive(ctx, "runtime.zip", runtime, progress);
        installArchive(ctx, "engine.zip", engine, progress);
        // Every symlink is refreshed because nativeLibraryDir changes on APK updates.
        Properties links = new Properties();
        try (InputStream in = new FileInputStream(new File(runtime, "native-links.properties"))) { links.load(in); }
        for (String path : links.stringPropertyNames()) {
            File link = new File(runtime, path);
            String name = links.getProperty(path);
            if (path.startsWith("/") || Arrays.asList(path.split("/")).contains("..") || !nativeHashes.has(name))
                throw new IOException("Invalid native link");
            Files.createDirectories(link.getParentFile().toPath());
            Files.deleteIfExists(link.toPath());
            Files.createSymbolicLink(link.toPath(), new File(nativeDir, name).toPath());
        }
        if (world.exists()) {
            File marker = new File(world, ".world-version");
            if (!marker.isFile() || !readFile(marker).trim().equals(manifest.getString("worldVersion")))
                throw new IOException("World version needs migration. Export a backup before changing server versions.");
        } else {
            installArchive(ctx, "world.zip", world, progress);
            write(new File(world, ".world-version"), manifest.getString("worldVersion"));
        }
        // Headless/local settings are regenerated from the pinned engine on every launch.
        write(new File(world, "worldprops/default.conf"), readFile(new File(engine, "default.conf")));
        File tmp = new File(root, "tmp");
        if (!tmp.isDirectory() && !tmp.mkdirs()) throw new IOException("Cannot create temporary directory");
    }

    private void installArchive(Context ctx, String asset, File destination, Progress progress) throws Exception {
        if (new File(destination, ".complete").isFile()) return;
        if (destination.exists()) throw new IOException("Incomplete installation at " + destination.getName());
        progress.update("Preparing " + (asset.equals("world.zip") ? "world and cache" : asset.equals("runtime.zip") ? "Java runtime" : "server"));
        File stage = new File(root, destination.getName() + ".pending");
        SafeZip.delete(stage);
        File archive = new File(root, asset + ".pending");
        try {
            try (InputStream in = ctx.getAssets().open("singleplayer/" + asset); OutputStream out = new FileOutputStream(archive)) { copy(in, out); }
            if (!sha(archive).equals(manifest.getJSONObject("archives").getString(asset))) throw new IOException("Package checksum mismatch");
            try (InputStream in = new FileInputStream(archive)) { SafeZip.extract(in, stage, 1024L * 1024 * 1024); }
            if (asset.equals("world.zip")) write(new File(stage, ".world-version"), manifest.getString("worldVersion"));
            write(new File(stage, ".complete"), manifest.getString("id"));
            Files.move(stage.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            archive.delete();
            SafeZip.delete(stage);
        }
    }

    public static String sha(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] b = new byte[65536];
        try (InputStream in = new FileInputStream(file)) { int n; while ((n = in.read(b)) != -1) digest.update(b, 0, n); }
        StringBuilder result = new StringBuilder();
        for (byte v : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", v & 255));
        return result.toString();
    }
    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[65536]; int n;
        while ((n = in.read(b)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled");
            out.write(b, 0, n);
        }
    }
    public static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); copy(in, out); return out.toString("UTF-8");
    }
    public static String readFile(File f) throws IOException { try (InputStream in = new FileInputStream(f)) { return read(in); } }
    public static void write(File f, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); out.getFD().sync(); }
    }
}
