package net.kdt.pojavlaunch.server;

import android.app.Activity;
import android.content.Intent;
import net.kdt.pojavlaunch.Tools;
import org.json.JSONObject;
import java.io.File;

public final class LocalClient {
    public static final String EXTRA = "scape.singleplayer";
    public static String configPath(Activity activity) {
        return Tools.DIR_DATA + (activity.getIntent().getBooleanExtra(EXTRA, false) ? "/singleplayer-client.json" : "/config.json");
    }
    public static Intent intent(Activity activity, Class<?> client) throws Exception {
        JSONObject config = new JSONObject(ServerFiles.readFile(new File(Tools.DIR_DATA, "config.json")));
        config.put("ip_address", "127.0.0.1"); config.put("ip_management", "127.0.0.1");
        config.put("world", 1); config.put("server_port", 43594); config.put("wl_port", 43595); config.put("js5_port", 43595);
        File dest = new File(Tools.DIR_DATA, "singleplayer-client.json");
        File pending = new File(Tools.DIR_DATA, "singleplayer-client.json.pending");
        ServerFiles.write(pending, config.toString(2));
        java.nio.file.Files.move(pending.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return new Intent(activity, client).putExtra(EXTRA, true);
    }
    private LocalClient() {}
}
