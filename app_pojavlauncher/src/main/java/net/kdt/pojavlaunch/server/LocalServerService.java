package net.kdt.pojavlaunch.server;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import net.kdt.pojavlaunch.R;
import java.io.*;
import java.lang.Process;
import java.net.*;
import java.nio.channels.*;
import java.util.UUID;

/** Foreground supervisor in :server. The headless desktop JVM is its owned child process. */
public final class LocalServerService extends Service {
    public static final String START = "scape.server.START", STOP = "scape.server.STOP", FORCE = "scape.server.FORCE";
    public static final int STATUS = 1;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final StringBuilder tail = new StringBuilder();
    private volatile String state = "Stopped", detail = "Start a local world to play single-player.";
    private volatile Thread worker;
    private volatile Process child;
    private volatile boolean stopping, readyMarker, forced, saveCompleted, saveError;
    private volatile String token;
    private PowerManager.WakeLock wake;
    private ServerFiles files;
    private Runnable shutdownWarning;
    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(), msg -> {
        if (msg.what != STATUS || msg.replyTo == null) return false;
        Bundle data = new Bundle();
        data.putString("state", state); data.putString("detail", detail);
        data.putBoolean("busy", worker != null); data.putBoolean("ready", "Running".equals(state));
        // Avoid allocating and sending a log snapshot while diagnostics are collapsed.
        if (msg.getData().getBoolean("includeLog")) {
            synchronized (tail) { data.putString("log", tail.toString()); }
        }
        Message reply = Message.obtain(null, STATUS); reply.setData(data);
        try { msg.replyTo.send(reply); } catch (RemoteException ignored) {}
        return true;
    }));


    @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (STOP.equals(action)) { requestStop(); return START_NOT_STICKY; }
        if (FORCE.equals(action)) {
            if (child != null && stopping) { forced = true; log("[app] Force stop requested; recent changes may be lost."); child.destroyForcibly(); }
            return START_NOT_STICKY;
        }
        if (!START.equals(action) || worker != null) return START_NOT_STICKY;
        if (Build.VERSION.SDK_INT < 33 || !android.os.Process.is64Bit() || !Build.SUPPORTED_ABIS[0].equals("arm64-v8a")) {
            state = "Error"; detail = "The integrated server requires Android 13 or newer on ARM64."; stopSelf(); return START_NOT_STICKY;
        }
        stopping = false; readyMarker = false; forced = false; saveCompleted = false; saveError = false; token = UUID.randomUUID().toString();
        state = "Preparing"; detail = "Preparing local server";
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("singleplayer", "Single-player server", NotificationManager.IMPORTANCE_LOW));
        showNotification();
        wake = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":singleplayer");
        wake.acquire();
        final int heap = intent.getIntExtra("heap", 2048);
        worker = new Thread(() -> runServer(heap), "singleplayer-supervisor");
        worker.start();
        return START_NOT_STICKY;
    }

    private void showNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 310, new Intent(this, SingleplayerActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 311, new Intent(this, LocalServerService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        startForeground(310, new NotificationCompat.Builder(this, "singleplayer")
            .setSmallIcon(R.drawable.notif_icon_game).setContentTitle("2009Scape single-player")
            .setContentText(detail).setContentIntent(open).setOngoing(true)
            .addAction(0, "Save and stop", stop).build());
    }

    private void status(String next, String message) {
        state = next; detail = message; log("[app] " + message);
        main.post(() -> { if (worker != null) showNotification(); });
    }

    private void runServer(int heap) {
        Thread reader = null;
        try {
            files = new ServerFiles(this);
            // Same lock used by the native process and backup/restore. Never replace its inode.
            try (RandomAccessFile file = new RandomAccessFile(files.lock, "rw"); FileChannel channel = file.getChannel()) {
                FileLock lock = channel.tryLock();
                if (lock == null) throw new IOException("The world is busy with a server or backup operation.");
                try {
                    if (files.log.exists()) java.nio.file.Files.move(files.log.toPath(), files.previousLog.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    synchronized (tail) { tail.setLength(0); }
                    log("[app] Session " + token + "; Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + "; heap " + heap + " MiB");
                    files.install(this, text -> status("Preparing", text));
                } finally { lock.release(); }
            }
            if (stopping) { status("Stopped", "Setup finished; server start cancelled."); return; }
            if (portOpen()) throw new IOException("Port 43595 is already in use. Stop the other server first.");
            status("Starting", "Starting headless server; first startup can take a few minutes");
            ProcessBuilder builder = new ProcessBuilder(ServerLaunch.arguments(files.nativeDir, files.runtime, files.engine, files.root, heap, token))
                .directory(files.world).redirectErrorStream(true);
            builder.environment().putAll(ServerLaunch.environment(files.nativeDir, files.runtime, files.root));
            builder.environment().remove("_JAVA_OPTIONS"); builder.environment().remove("JAVA_TOOL_OPTIONS");
            builder.environment().remove("JDK_JAVA_OPTIONS"); builder.environment().remove("LD_PRELOAD");
            child = builder.start();
            Process running = child;
            reader = new Thread(() -> consume(running), "singleplayer-output"); reader.start();
            long deadline = SystemClock.elapsedRealtime() + 240000;
            while (running.isAlive() && !stopping && !readyMarker && SystemClock.elapsedRealtime() < deadline) Thread.sleep(200);
            if (!stopping && running.isAlive()) {
                if (!readyMarker || !portOpen()) {
                    status("Error", "Server startup timed out. See the log for details."); requestStop();
                } else status("Running", "Local world is ready · 127.0.0.1:43595");
            }
            if (stopping) sendStop(running);
            int exit = running.waitFor();
            reader.join(3000);
            log("[app] Server exited with code " + exit);
            if (forced) status("Error", "Server was forced to stop. Recent progress may be missing.");
            else if (stopping && readyMarker && exit == 0 && saveCompleted && !saveError) status("Stopped", "Server stopped after its normal save-and-shutdown sequence.");
            else if (exit != 0 || !readyMarker || saveError || (stopping && !saveCompleted)) status("Error", "Server exited (code " + exit + "). Open or export the log.");
            else status("Stopped", "Server stopped.");
        } catch (Throwable e) {
            log(android.util.Log.getStackTraceString(e));
            status("Error", e.getMessage() == null ? e.toString() : e.getMessage());
            Process process = child;
            if (process != null && process.isAlive()) { process.destroy(); }
        } finally {
            // If shutdown has not completed, retain supervision and lock ownership.
            Process process = child;
            if (process != null && process.isAlive()) {
                try { process.waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            if (process != null && !process.isAlive()) {
                close(process.getOutputStream()); close(process.getInputStream()); close(process.getErrorStream());
            }
            child = null;
            if (files != null) try { ServerFiles.write(new File(files.root, "last-session.txt"), state + "\n" + detail + "\n"); } catch (IOException ignored) {}
            main.post(() -> {
                if (shutdownWarning != null) main.removeCallbacks(shutdownWarning);
                shutdownWarning = null;
                worker = null;
                if (wake != null && wake.isHeld()) wake.release();
                wake = null; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
            });
        }
    }

    private void consume(Process process) {
        try (Reader input = new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
            StringBuilder line = new StringBuilder(); int c;
            while ((c = input.read()) != -1) {
                if (c == '\n' || line.length() >= 4096) {
                    String text = line.toString().replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
                    log(text);
                    if (text.equals("[scape] SERVER_READY " + token)) readyMarker = true;
                    if (text.contains("Server successfully terminated!")) saveCompleted = true;
                    if (text.contains("core.ServerStore.save(") || text.contains("ScriptException") || text.contains("Failed to save")) saveError = true;
                    line.setLength(0);
                } else if (c != '\r') line.append((char)c);
            }
            if (line.length() > 0) log(line.toString());
        } catch (IOException e) { log("[app] Output stream closed: " + e.getMessage()); }
    }

    private synchronized void log(String text) {
        synchronized (tail) {
            tail.append(text).append('\n');
            if (tail.length() > 24000) tail.delete(0, tail.length() - 24000);
        }
        if (files != null) try {
            if (files.log.length() > 8L * 1024 * 1024) java.nio.file.Files.move(files.log.toPath(), new File(files.root, "server-older.log").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try (FileOutputStream out = new FileOutputStream(files.log, true)) { out.write((text + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        } catch (IOException e) { android.util.Log.e("Singleplayer", "Cannot write server log", e); }
    }

    private void requestStop() {
        if (worker == null || stopping) return;
        stopping = true; status("Stopping", "Saving and stopping the local world");
        Process process = child;
        if (process != null) sendStop(process);
        shutdownWarning = () -> {
            if (worker != null && stopping && child == process && process != null && process.isAlive()) {
                detail = "Shutdown is taking longer than expected. View logs or force stop if necessary.";
                showNotification();
            }
        };
        main.postDelayed(shutdownWarning, 30000);
    }

    private static void close(Closeable stream) {
        try { stream.close(); } catch (IOException ignored) {}
    }

    private synchronized void sendStop(Process process) {
        try { process.getOutputStream().write("stop\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)); process.getOutputStream().flush(); }
        catch (IOException e) { log("[app] Console unavailable; requesting JVM shutdown signal."); process.destroy(); }
    }

    private boolean portOpen() {
        try (Socket socket = new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1", 43595), 250); return true; }
        catch (IOException e) { return false; }
    }

    @Override public void onDestroy() {
        requestStop();
        if (wake != null && wake.isHeld()) wake.release();
        super.onDestroy();
    }
}
