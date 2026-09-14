package net.kdt.pojavlaunch.server;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import net.kdt.pojavlaunch.MainActivity;
import net.kdt.pojavlaunch.Tools;
import org.json.JSONObject;
import java.io.File;

public final class LocalClient {
    public static final String EXTRA = "scape.singleplayer";
    public static String configPath(Activity activity) {
        // Record the mode of the JVM being launched so returning from the server
        // notification cannot create a second JVM in an already-running client.
        try {
            JSONObject mode = new JSONObject().put("pid", android.os.Process.myPid())
                .put("local", activity.getIntent().getBooleanExtra(EXTRA, false));
            File record = modeFile(activity.getClass());
            ServerFiles.write(record, mode.toString());
        } catch (Exception e) { android.util.Log.w("Singleplayer", "Cannot record client mode", e); }
        return Tools.DIR_DATA + (activity.getIntent().getBooleanExtra(EXTRA, false) ? "/singleplayer-client.json" : "/config.json");
    }
    public static Intent intent(Activity activity, Class<?> client) throws Exception {
        File record = modeFile(client);
        int pid = -1;
        if (client == MainActivity.class) {
            ActivityManager manager = (ActivityManager)activity.getSystemService(android.content.Context.ACTIVITY_SERVICE);
            java.util.List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
            if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes)
                if (process.processName.equals(activity.getPackageName() + ":game")) pid = process.pid;
        } else pid = android.os.Process.myPid();
        if (record.isFile()) {
            JSONObject mode = new JSONObject(ServerFiles.readFile(record));
            if (mode.optInt("pid", -2) == pid && !mode.optBoolean("local"))
                throw new java.io.IOException("Close the running client before switching it to the local server.");
        }
        JSONObject config = new JSONObject(ServerFiles.readFile(new File(Tools.DIR_DATA, "config.json")));
        config.put("ip_address", "127.0.0.1"); config.put("ip_management", "127.0.0.1");
        config.put("world", 1); config.put("server_port", 43594); config.put("wl_port", 43595); config.put("js5_port", 43595);
        File dest = new File(Tools.DIR_DATA, "singleplayer-client.json");
        File pending = new File(Tools.DIR_DATA, "singleplayer-client.json.pending");
        ServerFiles.write(pending, config.toString(2));
        java.nio.file.Files.move(pending.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return new Intent(activity, client).putExtra(EXTRA, true)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    private static File modeFile(Class<?> client) {
        return new File(Tools.DIR_DATA, client == MainActivity.class ? "client-hd-mode.json" : "client-sd-mode.json");
    }
    private LocalClient() {}
}
