package net.kdt.pojavlaunch.server;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.tasks.AsyncAssetManager;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.zip.*;

public final class SingleplayerActivity extends BaseActivity {
    private static final int LOG_EXPORT = 51, BACKUP = 52, RESTORE = 53;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Messenger server;
    private boolean bound, resumed, busy, ready, awaitingStartAck, clientDispatched;
    private volatile boolean operation;
    private final PendingClientLaunch<Intent> pendingClient = new PendingClientLaunch<>();
    private TextView status, detail, log;
    private View diagnostics, worldTools;
    private boolean supported;
    private Button start, hd, sd, stop, backup, restore, force;
    private Spinner memory;
    private final int[] heaps = {512, 1024, 1536, 2048, 3072, 4096};
    private final Messenger receiver = new Messenger(new Handler(Looper.getMainLooper(), message -> {
        if (isDestroyed() || !resumed) return true;
        Bundle data = message.getData();
        busy = data.getBoolean("busy"); ready = data.getBoolean("ready");
        if (busy) awaitingStartAck = false;
        String state = data.getString("state", "Stopped");
        setTextIfChanged(status, state);
        setTextIfChanged(detail, data.getString("detail", ""));
        String text = data.getString("log", "");
        if (!text.isEmpty() && diagnostics.getVisibility() == View.VISIBLE) setTextIfChanged(log, text);
        start.setEnabled(!busy && !operation); memory.setEnabled(!busy && !operation);
        hd.setEnabled(!operation && !clientDispatched && !state.equals("Stopping")); sd.setEnabled(hd.isEnabled());
        stop.setEnabled(busy && !state.equals("Stopping"));
        backup.setEnabled(!busy && !operation); restore.setEnabled(!busy && !operation);
        force.setVisibility(state.equals("Stopping") ? View.VISIBLE : View.GONE);
        if (state.equals("Stopping") || (!awaitingStartAck && (state.equals("Error") || (state.equals("Stopped") && !busy)))) pendingClient.select("");
        if (ready && resumed && !pendingClient.mode().isEmpty()) launchClient();
        return true;
    }));
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) { server = new Messenger(binder); poll(); }
        @Override public void onServiceDisconnected(ComponentName name) {
            server = null; ready = false; busy = false; pendingClient.select("");
            status.setText("Server supervisor disconnected. Open the logs before restarting.");
        }
    };
    private final Runnable refresh = new Runnable() {
        @Override public void run() { if (resumed) { poll(); handler.postDelayed(this, 1000); } }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) pendingClient.select(state.getString("pendingClient", ""));
        setContentView(R.layout.activity_singleplayer);
        findViewById(R.id.server_back).setOnClickListener(v -> finish());
        status = findViewById(R.id.server_state); detail = findViewById(R.id.server_detail);
        log = findViewById(R.id.server_log);
        diagnostics = findViewById(R.id.server_diagnostics); worldTools = findViewById(R.id.server_tools);
        start = findViewById(R.id.server_start); hd = findViewById(R.id.server_hd); sd = findViewById(R.id.server_sd);
        stop = findViewById(R.id.server_stop); backup = findViewById(R.id.server_backup);
        restore = findViewById(R.id.server_restore); force = findViewById(R.id.server_force);
        memory = findViewById(R.id.server_memory);
        supported = Build.VERSION.SDK_INT >= 33 && android.os.Process.is64Bit() && Build.SUPPORTED_ABIS[0].equals("arm64-v8a");
        for (View control : new View[]{start, hd, sd, stop, backup, restore, memory, findViewById(R.id.server_export)}) control.setEnabled(false);
        findViewById(R.id.server_tools_toggle).setOnClickListener(v -> toggle(worldTools, (Button)v,
            R.string.singleplayer_tools_show, R.string.singleplayer_tools_hide));
        findViewById(R.id.server_logs_toggle).setOnClickListener(v -> {
            toggle(diagnostics, (Button)v, R.string.singleplayer_diagnostics_show, R.string.singleplayer_diagnostics_hide);
            poll();
        });
        if (state != null) {
            if (state.getBoolean("tools")) findViewById(R.id.server_tools_toggle).performClick();
            if (state.getBoolean("diagnostics")) findViewById(R.id.server_logs_toggle).performClick();
            awaitingStartAck = state.getBoolean("awaitingStartAck");
        }
        if (!supported) {
            status.setText("Local world unavailable"); detail.setText(R.string.singleplayer_requirement); return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 54);
        start.setOnClickListener(v -> startServer(""));
        hd.setOnClickListener(v -> startServer("hd")); sd.setOnClickListener(v -> startServer("sd"));
        stop.setOnClickListener(v -> { pendingClient.select(""); command(LocalServerService.STOP); });
        memory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"512 MiB", "1024 MiB", "1536 MiB", "2048 MiB", "3072 MiB", "4096 MiB"}));
        int selected = getPreferences(MODE_PRIVATE).getInt("heap", 2048);
        for (int i = 0; i < heaps.length; i++) if (heaps[i] == selected) memory.setSelection(i);
        findViewById(R.id.server_export).setEnabled(true);
        findViewById(R.id.server_export).setOnClickListener(v -> createDocument(LOG_EXPORT, "application/zip", "2009scape-diagnostics.zip"));
        backup.setOnClickListener(v -> createDocument(BACKUP, "application/zip", "2009scape-world-backup.zip"));
        restore.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Restore a world backup?").setMessage("The selected backup becomes the active world. A copy of the current world will be retained on this device.")
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton("Choose backup", (d, w) -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, RESTORE);
            }).show());
        force.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Force stop server?")
            .setMessage("Use this only if normal shutdown is stuck. Recent progress may be lost.")
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton("Force stop", (d, w) -> command(LocalServerService.FORCE)).show());
        bound = bindService(new Intent(this, LocalServerService.class), connection, BIND_AUTO_CREATE);
        // File reads must not keep a destroyed Activity alive.
        Context app = getApplicationContext();
        WeakReference<SingleplayerActivity> screen = new WeakReference<>(this);
        Handler ui = handler;
        new Thread(() -> {
            try {
                ServerFiles files = new ServerFiles(app);
                if (files.log.isFile()) {
                    String tail = readTail(files.log, 24000);
                    ui.post(() -> {
                        SingleplayerActivity activity = screen.get();
                        if (activity != null && !activity.isDestroyed()
                            && activity.log.getText().toString().equals(activity.getString(R.string.singleplayer_log_empty)))
                            activity.log.setText(tail);
                    });
                }
            } catch (Exception ignored) {}
        }, "singleplayer-previous-log").start();
    }

    private static void setTextIfChanged(TextView view, String text) {
        if (!android.text.TextUtils.equals(view.getText(), text)) view.setText(text);
    }
    private void toggle(View section, Button control, int show, int hide) {
        boolean open = section.getVisibility() != View.VISIBLE;
        section.setVisibility(open ? View.VISIBLE : View.GONE); control.setText(open ? hide : show);
    }
    private void poll() {
        if (server == null || !resumed) return;
        Message message = Message.obtain(null, LocalServerService.STATUS); message.replyTo = receiver;
        Bundle request = new Bundle(); request.putBoolean("includeLog", diagnostics.getVisibility() == View.VISIBLE); message.setData(request);
        try { server.send(message); } catch (RemoteException e) { status.setText("Cannot reach server controls"); }
    }
    private void command(String action) { startService(new Intent(this, LocalServerService.class).setAction(action)); }

    private void startServer(String client) {
        if (operation || clientDispatched) return;
        if (!client.isEmpty()) ClientDiagnostics.request(this, client);
        pendingClient.select(client);
        if (ready) { if (!client.isEmpty()) launchClient(); return; }
        if (busy) { status.setText("The client will open when the server is ready."); return; }
        int heap = heaps[memory.getSelectedItemPosition()]; getPreferences(MODE_PRIVATE).edit().putInt("heap", heap).apply();
        busy = true; awaitingStartAck = true; start.setEnabled(false); backup.setEnabled(false); restore.setEnabled(false);
        status.setText("Preparing local world…");
        ContextCompat.startForegroundService(this, new Intent(this, LocalServerService.class).setAction(LocalServerService.START).putExtra("heap", heap));
    }

    private void launchClient() {
        try {
            if (!AsyncAssetManager.clientReady(this)) {
                setTextIfChanged(detail, "Preparing client files…");
                return; // The next server status update resumes this Play request.
            }
        } catch (IOException e) {
            ClientDiagnostics.handoffFailure(this, pendingClient.mode(), e);
            pendingClient.select("");
            error(e);
            return;
        }
        Context app = getApplicationContext();
        Class<?> client = pendingClient.mode().equals("sd") ? JavaGUILauncherActivity.class : MainActivity.class;
        try {
            Intent intent = pendingClient.poll(PojavApplication.sExecutorService, () -> {
                MultiRTUtils.requireClientRuntime();
                return LocalClient.intent(app, client);
            });
            if (intent == null) {
                setTextIfChanged(detail, "Preparing local client connection…");
                return;
            }
            startActivity(intent);
            clientDispatched = true;
            hd.setEnabled(false);
            sd.setEnabled(false);
        } catch (Exception e) {
            ClientDiagnostics.handoffFailure(this, client == MainActivity.class ? "hd" : "sd", e);
            error(e);
        }
    }
    @Override protected void onResume() { super.onResume(); resumed = true; clientDispatched = false; if (supported) handler.post(refresh); }
    @Override protected void onPause() { resumed = false; ready = false; handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("pendingClient", pendingClient.mode()); state.putBoolean("awaitingStartAck", awaitingStartAck);
        state.putBoolean("tools", worldTools.getVisibility() == View.VISIBLE);
        state.putBoolean("diagnostics", diagnostics.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { pendingClient.select(""); handler.removeCallbacksAndMessages(null); if (bound) unbindService(connection); server = null; super.onDestroy(); }

    private void createDocument(int code, String type, String name) {
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, name), code);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null || (request != LOG_EXPORT && request != BACKUP && request != RESTORE)) return;
        Uri uri = data.getData(); operation = true; pendingClient.select("");
        Context app = getApplicationContext();
        WeakReference<SingleplayerActivity> screen = new WeakReference<>(this);
        Handler ui = handler;
        new Thread(() -> {
            try {
                ServerFiles files = new ServerFiles(app);
                if (request == RESTORE) {
                    try (InputStream input = app.getContentResolver().openInputStream(uri)) {
                        if (input == null) throw new IOException("Could not open backup");
                        WorldBackup.restore(files, input);
                    }
                } else try (OutputStream output = app.getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new IOException("Could not open destination");
                    if (request == BACKUP) WorldBackup.exportWorld(files, output);
                    else exportLogs(app, files, output);
                }
                ui.post(() -> Toast.makeText(app, request == RESTORE ? "World restored" : "Export complete", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                ui.post(() -> {
                    SingleplayerActivity activity = screen.get();
                    if (activity != null && !activity.isDestroyed()) activity.error(e);
                });
            } finally {
                ui.post(() -> {
                    SingleplayerActivity activity = screen.get();
                    if (activity != null && !activity.isDestroyed()) { activity.operation = false; activity.poll(); }
                });
            }
        }, "singleplayer-file-operation").start();
    }
    private static void exportLogs(Context app, ServerFiles files, OutputStream output) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            ClientDiagnostics.export(app, zip);
            zip.putNextEntry(new ZipEntry("package.json")); zip.write(files.manifest.toString(2).getBytes("UTF-8")); zip.closeEntry();
            File[] entries = files.root.listFiles();
            if (entries != null) for (File file : entries) if (file.isFile() && (file.getName().endsWith(".log") || file.getName().equals("last-session.txt"))) {
                zip.putNextEntry(new ZipEntry(file.getName())); zip.write(readTail(file, 512 * 1024).getBytes("UTF-8")); zip.closeEntry();
            }
        }
    }
    private static String readTail(File file, int limit) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long count = Math.min(in.length(), limit); in.seek(in.length() - count);
            byte[] bytes = new byte[(int)count]; in.readFully(bytes); return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private void error(Exception e) { if (!isFinishing() && !isDestroyed()) new AlertDialog.Builder(this).setTitle("Single-player").setMessage(e.getMessage()).setPositiveButton(android.R.string.ok, null).show(); }
}
