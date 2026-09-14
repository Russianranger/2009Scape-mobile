package net.kdt.pojavlaunch.server;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import java.io.*;
import java.util.zip.*;

public final class SingleplayerActivity extends BaseActivity {
    private static final int LOG_EXPORT = 51, BACKUP = 52, RESTORE = 53;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Messenger server;
    private boolean bound, resumed, busy, ready, awaitingStartAck;
    private volatile boolean operation;
    private String pendingClient = "";
    private TextView status, log;
    private Button start, hd, sd, stop, backup, restore, force;
    private Spinner memory;
    private final int[] heaps = {512, 1024, 1536, 2048, 3072, 4096};
    private final Messenger receiver = new Messenger(new Handler(Looper.getMainLooper(), message -> {
        Bundle data = message.getData();
        busy = data.getBoolean("busy"); ready = data.getBoolean("ready");
        if (busy) awaitingStartAck = false;
        String state = data.getString("state", "Stopped");
        status.setText(state + "\n" + data.getString("detail", ""));
        String text = data.getString("log", "");
        if (!text.isEmpty()) log.setText(text);
        start.setEnabled(!busy && !operation); memory.setEnabled(!busy && !operation);
        hd.setEnabled(!operation && !state.equals("Stopping")); sd.setEnabled(hd.isEnabled());
        stop.setEnabled(busy && !state.equals("Stopping"));
        backup.setEnabled(!busy && !operation); restore.setEnabled(!busy && !operation);
        force.setVisibility(state.equals("Stopping") ? View.VISIBLE : View.GONE);
        if (state.equals("Error") || (state.equals("Stopped") && !busy && !awaitingStartAck)) pendingClient = "";
        if (ready && resumed && !pendingClient.isEmpty()) launchClient();
        return true;
    }));
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) { server = new Messenger(binder); poll(); }
        @Override public void onServiceDisconnected(ComponentName name) {
            server = null; ready = false; busy = false; pendingClient = "";
            status.setText("Server supervisor disconnected. Open the logs before restarting.");
        }
    };
    private final Runnable refresh = new Runnable() {
        @Override public void run() { if (resumed) { poll(); handler.postDelayed(this, 1000); } }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) pendingClient = state.getString("pendingClient", "");
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(12)); root.setBackgroundColor(Color.rgb(28, 25, 20));
        setContentView(root);
        TextView title = text(getString(R.string.singleplayer_title), 22); root.addView(title);
        if (Build.VERSION.SDK_INT < 33 || !android.os.Process.is64Bit() || !Build.SUPPORTED_ABIS[0].equals("arm64-v8a")) {
            root.addView(text(getString(R.string.singleplayer_requirement), 16)); return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 54);
        root.addView(text(getString(R.string.singleplayer_intro), 14));
        status = text("Connecting to server controls…", 16); root.addView(status);
        LinearLayout controls = row(root);
        start = button(controls, R.string.singleplayer_start, v -> startServer(""));
        hd = button(controls, R.string.singleplayer_hd, v -> startServer("hd"));
        sd = button(controls, R.string.singleplayer_sd, v -> startServer("sd"));
        stop = button(controls, R.string.singleplayer_stop, v -> { pendingClient = ""; command(LocalServerService.STOP); });
        LinearLayout options = row(root);
        options.addView(text(getString(R.string.singleplayer_memory), 14));
        memory = new Spinner(this);
        memory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"512 MiB", "1024 MiB", "1536 MiB", "2048 MiB", "3072 MiB", "4096 MiB"}));
        int selected = getPreferences(MODE_PRIVATE).getInt("heap", 2048);
        for (int i = 0; i < heaps.length; i++) if (heaps[i] == selected) memory.setSelection(i);
        options.addView(memory);
        button(options, R.string.singleplayer_logs, v -> createDocument(LOG_EXPORT, "application/zip", "2009scape-server-logs.zip"));
        backup = button(options, R.string.singleplayer_backup, v -> createDocument(BACKUP, "application/zip", "2009scape-world-backup.zip"));
        restore = button(options, R.string.singleplayer_restore, v -> new AlertDialog.Builder(this)
            .setTitle("Restore a world backup?").setMessage("The selected backup becomes the active world. A copy of the current world will be retained on this device.")
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton("Choose backup", (d, w) -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, RESTORE);
            }).show());
        force = button(options, R.string.singleplayer_force, v -> new AlertDialog.Builder(this).setTitle("Force stop server?")
            .setMessage("Use this only if normal shutdown is stuck. Recent progress may be lost.")
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton("Force stop", (d, w) -> command(LocalServerService.FORCE)).show());
        force.setVisibility(View.GONE);
        root.addView(text(getString(R.string.singleplayer_preservation), 12));
        ScrollView scroll = new ScrollView(this);
        log = text("Server logs will appear here.", 12); log.setTypeface(android.graphics.Typeface.MONOSPACE); log.setTextIsSelectable(true);
        scroll.addView(log); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        start.setEnabled(false); hd.setEnabled(false); sd.setEnabled(false); stop.setEnabled(false);
        bound = bindService(new Intent(this, LocalServerService.class), connection, BIND_AUTO_CREATE);
        new Thread(() -> {
            try {
                ServerFiles files = new ServerFiles(this);
                if (files.log.isFile()) {
                    String tail = readTail(files.log);
                    handler.post(() -> { if (log.getText().toString().equals("Server logs will appear here.")) log.setText(tail); });
                }
            } catch (Exception ignored) {}
        }, "singleplayer-previous-log").start();
    }

    private LinearLayout row(LinearLayout parent) {
        HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setFillViewport(false);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        scroll.addView(row); parent.addView(scroll); return row;
    }
    private Button button(LinearLayout parent, int label, View.OnClickListener click) {
        Button button = new Button(this); button.setText(label); button.setTextSize(12); button.setAllCaps(false);
        button.setOnClickListener(click); parent.addView(button, new LinearLayout.LayoutParams(-2, dp(48))); return button;
    }
    private TextView text(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.rgb(235, 226, 207)); return t;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void poll() {
        if (server == null) return;
        Message message = Message.obtain(null, LocalServerService.STATUS); message.replyTo = receiver;
        try { server.send(message); } catch (RemoteException e) { status.setText("Cannot reach server controls"); }
    }
    private void command(String action) { startService(new Intent(this, LocalServerService.class).setAction(action)); }

    private void startServer(String client) {
        if (operation) return;
        if (!client.isEmpty()) {
            try {
                if (MultiRTUtils.forceReread("Internal") == null) throw new IOException("The existing client runtime is missing. Configure it in launcher settings first.");
            } catch (Exception e) { error(e); return; }
        }
        pendingClient = client;
        if (ready) { if (!client.isEmpty()) launchClient(); return; }
        if (busy) { status.setText("The client will open when the server is ready."); return; }
        int heap = heaps[memory.getSelectedItemPosition()]; getPreferences(MODE_PRIVATE).edit().putInt("heap", heap).apply();
        busy = true; awaitingStartAck = true; start.setEnabled(false); backup.setEnabled(false); restore.setEnabled(false);
        status.setText("Preparing local world…");
        ContextCompat.startForegroundService(this, new Intent(this, LocalServerService.class).setAction(LocalServerService.START).putExtra("heap", heap));
    }

    private void launchClient() {
        String client = pendingClient; pendingClient = "";
        try { startActivity(LocalClient.intent(this, client.equals("sd") ? JavaGUILauncherActivity.class : MainActivity.class)); }
        catch (Exception e) { error(e); }
    }
    @Override protected void onResume() { super.onResume(); resumed = true; if (status != null) handler.post(refresh); }
    @Override protected void onPause() { resumed = false; handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("pendingClient", pendingClient); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { handler.removeCallbacks(refresh); if (bound) unbindService(connection); super.onDestroy(); }

    private void createDocument(int code, String type, String name) {
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, name), code);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null || (request != LOG_EXPORT && request != BACKUP && request != RESTORE)) return;
        Uri uri = data.getData(); operation = true; pendingClient = "";
        new Thread(() -> {
            try {
                ServerFiles files = new ServerFiles(this);
                if (request == RESTORE) {
                    try (InputStream input = getContentResolver().openInputStream(uri)) {
                        if (input == null) throw new IOException("Could not open backup");
                        WorldBackup.restore(files, input);
                    }
                } else try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new IOException("Could not open destination");
                    if (request == BACKUP) WorldBackup.exportWorld(files, output);
                    else exportLogs(files, output);
                }
                handler.post(() -> Toast.makeText(this, request == RESTORE ? "World restored" : "Export complete", Toast.LENGTH_LONG).show());
            } catch (Exception e) { handler.post(() -> error(e)); }
            finally { operation = false; handler.post(this::poll); }
        }, "singleplayer-file-operation").start();
    }
    private void exportLogs(ServerFiles files, OutputStream output) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("package.json")); zip.write(files.manifest.toString(2).getBytes("UTF-8")); zip.closeEntry();
            File[] entries = files.root.listFiles();
            if (entries != null) for (File file : entries) if (file.isFile() && (file.getName().endsWith(".log") || file.getName().equals("last-session.txt"))) {
                zip.putNextEntry(new ZipEntry(file.getName())); zip.write(readTail(file).getBytes("UTF-8")); zip.closeEntry();
            }
        }
    }
    private static String readTail(File file) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long count = Math.min(in.length(), 512 * 1024); in.seek(in.length() - count);
            byte[] bytes = new byte[(int)count]; in.readFully(bytes); return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private void error(Exception e) { if (!isFinishing()) new AlertDialog.Builder(this).setTitle("Single-player").setMessage(e.getMessage()).setPositiveButton(android.R.string.ok, null).show(); }
}
