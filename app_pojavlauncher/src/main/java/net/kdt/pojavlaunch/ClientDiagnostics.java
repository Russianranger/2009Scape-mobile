package net.kdt.pojavlaunch;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import net.kdt.pojavlaunch.server.LocalClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Client-owned evidence for the local world's diagnostics export. */
public final class ClientDiagnostics {
    private static final String TAG = "ClientDiagnostics";
    private static final int LOG_LIMIT = 256 * 1024;
    private static final int TRACE_LIMIT = 2 * 1024 * 1024;

    private ClientDiagnostics() {}

    private static File directory(Context context) {
        return new File(context.getFilesDir(), "client-diagnostics");
    }

    private static String mode(Activity activity) {
        return activity instanceof MainActivity ? "hd" : "sd";
    }

    public static void request(Context context, String mode) {
        record(context, "requests", mode + " local play requested");
    }

    public static void phase(Activity activity, String message) {
        boolean local = activity.getIntent().getBooleanExtra(LocalClient.EXTRA, false);
        record(activity, mode(activity), (local ? "local " : "online ") + message);
    }

    public static void failure(Activity activity, Throwable error) {
        phase(activity, "startup failed " + describeFailure(error));
    }

    public static void handoffFailure(Context context, String mode, Throwable error) {
        record(context, "requests", mode + " handoff failed " + describeFailure(error));
    }

    static String describeFailure(Throwable error) {
        StringBuilder report = new StringBuilder();
        for (int depth = 0; depth < 3 && error != null; depth++, error = error.getCause()) {
            if (depth > 0) report.append("Caused by ");
            report.append(error.getClass().getName()).append(": ");
            String message = error.getMessage();
            if (message != null) report.append(message, 0, Math.min(1024, message.length()));
            report.append('\n');
            StackTraceElement[] frames = error.getStackTrace();
            for (int i = 0; i < Math.min(8, frames.length); i++)
                report.append("  at ").append(frames[i]).append('\n');
        }
        return new String(redact(report.toString().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    private static synchronized void record(Context context, String stream, String message) {
        try {
            File dir = directory(context);
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            File log = new File(dir, stream + ".log");
            if (log.length() >= 16 * 1024 && !rotate(log)) return;
            String line = System.currentTimeMillis() + " pid=" + Process.myPid() + " " + message + "\n";
            try (FileOutputStream out = new FileOutputStream(log, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Could not record client phase", e);
        }
    }

    public static boolean rotate(File file) {
        return !file.isFile() || file.renameTo(new File(file.getParentFile(), file.getName() + ".previous"));
    }

    public static void beginLog() throws IOException {
        File file = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
        if (file.length() > 0) rotate(file);
        Logger.begin(file.getAbsolutePath());
    }

    public static String errorFile(Activity activity) {
        File file = new File(directory(activity), mode(activity) + "-hotspot.log");
        rotate(file);
        return file.getAbsolutePath();
    }

    public static void export(Context app, ZipOutputStream zip) throws IOException {
        File dir = directory(app);
        File external = new File(Tools.DIR_GAME_HOME);
        File data = new File(Tools.DIR_DATA);
        write(zip, "client/device.txt", (BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                + Build.MANUFACTURER + " " + Build.MODEL + " Android " + Build.VERSION.RELEASE + " API "
                + Build.VERSION.SDK_INT + " ABI " + Arrays.toString(Build.SUPPORTED_ABIS) + " pageSize=" + Os.sysconf(OsConstants._SC_PAGESIZE) + "\n")
                .getBytes(StandardCharsets.UTF_8));
        for (String name : new String[]{"requests.log", "hd.log", "sd.log", "hd-hotspot.log", "sd-hotspot.log"})
            exportLogPair(zip, dir, name, "client/");
        exportLogPair(zip, external, "latestlog.txt", "client/");
        exportLogPair(zip, external, "latestcrash.txt", "client/");
        exportLogPair(zip, data, "latestcrash.txt", "client/private-");
        // Preview 3 used the JVM default in its private working directory.
        File[] legacy = data.listFiles((parent, name) -> name.matches("hs_err_pid[0-9]+\\.log"));
        if (legacy != null) {
            Arrays.sort(legacy, Comparator.comparingLong(File::lastModified).reversed());
            for (int i = 0; i < Math.min(2, legacy.length); i++) exportLog(zip, legacy[i], "client/" + legacy[i].getName());
        }
        if (Build.VERSION.SDK_INT >= 30) exportExits(app, zip);
    }

    private static void exportLogPair(ZipOutputStream zip, File dir, String name, String prefix) throws IOException {
        for (String suffix : new String[]{"", ".previous"})
            exportLog(zip, new File(dir, name + suffix), prefix + name + suffix);
    }

    static void exportLog(ZipOutputStream zip, File file, String name) throws IOException {
        if (!file.isFile()) return;
        byte[] data;
        try (RandomAccessFile source = new RandomAccessFile(file, "r")) {
            int count = (int)Math.min(source.length(), LOG_LIMIT);
            data = new byte[count];
            if (source.length() > count) {
                source.readFully(data, 0, count / 2);
                source.seek(source.length() - count / 2);
                source.readFully(data, count / 2, count / 2);
                byte[] gap = "\n[... middle omitted ...]\n".getBytes(StandardCharsets.UTF_8);
                System.arraycopy(gap, 0, data, count / 2 - gap.length, gap.length);
            } else source.readFully(data);
        } catch (IOException e) {
            Log.w(TAG, "Could not read " + file.getName(), e);
            return;
        }
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(file.lastModified());
        zip.putNextEntry(entry);
        zip.write(redact(data));
        zip.closeEntry();
    }

    // Drop entire credential-bearing lines, including possible JVM property values.
    private static byte[] redact(byte[] data) {
        String s = new String(data, StandardCharsets.UTF_8);
        return s.replaceAll("(?im)^.*(?:password|passwd|authorization:|(?:access|refresh|auth|bearer|github|session)[ _-]?token|session[ _-]?id|cookie:|--username).*$",
                "[redacted]").getBytes(StandardCharsets.UTF_8);
    }

    private static void write(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    @androidx.annotation.RequiresApi(30)
    private static void exportExits(Context app, ZipOutputStream zip) throws IOException {
        StringBuilder summary = new StringBuilder("Recent Android exit history for launcher and client processes. Times are Unix milliseconds.\n");
        int traces = 0;
        try {
            Set<String> names = new HashSet<>();
            for (Class<?> activity : new Class<?>[]{MainActivity.class, JavaGUILauncherActivity.class, ScapeLauncher.class}) {
                names.add(app.getPackageManager().getActivityInfo(new ComponentName(app, activity), 0).processName);
            }
            ActivityManager manager = app.getSystemService(ActivityManager.class);
            for (ApplicationExitInfo exit : manager.getHistoricalProcessExitReasons(app.getPackageName(), 0, 16)) {
                if (!names.contains(exit.getProcessName()) || exit.getPackageUid() != Process.myUid()) continue;
                summary.append(exit.getTimestamp()).append(" process=").append(exit.getProcessName())
                        .append(" pid=").append(exit.getPid()).append(" reason=").append(reason(exit.getReason()))
                        .append(" status=").append(exit.getStatus()).append(" pssKiB=").append(exit.getPss())
                        .append(" rssKiB=").append(exit.getRss()).append(" description=").append(exit.getDescription()).append('\n');
                if (traces >= 2) continue;
                boolean proto = Build.VERSION.SDK_INT >= 31 && exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE;
                byte[] trace;
                try (InputStream in = exit.getTraceInputStream()) {
                    if (in == null) continue;
                    trace = proto ? boundedTrace(in) : anrTrace(in, exit.getPid());
                } catch (IOException | RuntimeException e) {
                    summary.append("  Android trace unavailable\n");
                    continue;
                }
                if (trace == null) {
                    summary.append("  Trace exceeds 2 MiB or contains no matching process section\n");
                    continue;
                }
                String path = "client/android-" + exit.getTimestamp() + "-" + exit.getPid() + (proto ? ".tombstone.pb" : ".trace.txt");
                write(zip, path, proto ? trace : redact(trace));
                summary.append("  ").append(path).append('\n');
                traces++;
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException e) {
            summary.append("Android exit history unavailable: ").append(e.getClass().getSimpleName()).append('\n');
        }
        write(zip, "client/android-exits.txt", redact(summary.toString().getBytes(StandardCharsets.UTF_8)));
    }

    // Some Android versions attach dumps for several processes. Keep only the client PID.
    static byte[] anrTrace(InputStream in, int pid) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while (out.size() < TRACE_LIMIT
                && (count = in.read(buffer, 0, Math.min(buffer.length, TRACE_LIMIT - out.size()))) != -1)
            out.write(buffer, 0, count);
        String text = out.toString("UTF-8");
        if (!text.startsWith("----- pid ") && !text.contains("\n----- pid "))
            return out.toByteArray(); // Android also returns a single app-scoped dump without headers.
        StringBuilder own = new StringBuilder();
        boolean matching = false;
        for (String line : text.split("\n")) {
            if (line.startsWith("----- pid ")) matching = line.startsWith("----- pid " + pid + " ");
            if (matching) own.append(line).append('\n');
            if (line.startsWith("----- end ")) matching = false;
        }
        return own.length() == 0 ? null : own.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] boundedTrace(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (out.size() + count > TRACE_LIMIT) return null;
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private static String reason(int code) {
        switch (code) {
            case ApplicationExitInfo.REASON_ANR: return "ANR";
            case ApplicationExitInfo.REASON_CRASH: return "Java crash";
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "native crash";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "low memory";
            case ApplicationExitInfo.REASON_SIGNALED: return "signal";
            case ApplicationExitInfo.REASON_EXIT_SELF: return "self exit";
            case ApplicationExitInfo.REASON_USER_REQUESTED: return "user requested";
            default: return String.valueOf(code);
        }
    }
}
