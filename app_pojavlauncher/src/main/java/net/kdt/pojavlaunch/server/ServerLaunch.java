package net.kdt.pojavlaunch.server;

import java.io.File;
import java.util.*;

/** JVM arguments belong to the server; client Java/graphics preferences are untouched. */
public final class ServerLaunch {
    public static List<String> arguments(File nativeDir, File runtime, File engine, File root, int heap, String token) {
        if (heap < 512 || heap > 4096) throw new IllegalArgumentException("Server memory must be 512–4096 MiB");
        return Arrays.asList(new File(nativeDir, "libscape_server.so").getAbsolutePath(),
            "-Xms256m", "-Xmx" + heap + "m", "-Djava.awt.headless=true",
            "-Djava.home=" + runtime, "-Duser.home=" + root, "-Djava.io.tmpdir=" + new File(root, "tmp"),
            "-Djava.library.path=" + runtime + "/lib:" + runtime + "/lib/server:" + nativeDir,
            "-Dsun.boot.library.path=" + runtime + "/lib:" + nativeDir,
            "-Dorg.sqlite.lib.path=" + nativeDir, "-Dorg.sqlite.lib.name=libscape_sqlitejdbc.so",
            "-Dscape.session=" + token, "-XX:ErrorFile=" + root + "/hs_err_pid%p.log", "-XX:-CreateCoredumpOnCrash",
            "-cp", new File(engine, "bootstrap.jar") + ":" + new File(engine, "sqlite.jar") + ":" + engine + "/lib/*:" + new File(engine, "server.jar"),
            "scape.ServerMain", "worldprops/default.conf");
    }

    public static Map<String, String> environment(File nativeDir, File runtime, File root) {
        Map<String, String> env = new HashMap<>();
        env.put("JAVA_HOME", runtime.getAbsolutePath());
        env.put("HOME", root.getAbsolutePath());
        env.put("TMPDIR", new File(root, "tmp").getAbsolutePath());
        env.put("PATH", "/system/bin");
        env.put("LANG", "en_US.UTF-8");
        env.put("LD_LIBRARY_PATH", runtime + "/lib/server:" + runtime + "/lib:" + nativeDir);
        env.put("SCAPE_JLI_PATH", new File(nativeDir, "libscape_rt_jli.so").getAbsolutePath());
        env.put("SCAPE_JVM_PATH", new File(nativeDir, "libscape_rt_jvm.so").getAbsolutePath());
        env.put("SCAPE_WORLD_LOCK", new File(root, "world.lock").getAbsolutePath());
        return env;
    }
    private ServerLaunch() {}
}
