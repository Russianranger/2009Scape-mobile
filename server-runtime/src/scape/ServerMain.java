package scape;

/** Desktop JVM entry point. Nothing here executes inside Android ART. */
public final class ServerMain {
    public static void main(String[] args) {
        try {
            System.setProperty("java.awt.headless", "true");
            MemoryDiagnostics.start();
            // Exercise the actual database driver before boot, including its JNI library.
            Class.forName("org.sqlite.JDBC");
            try (java.sql.Connection db = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
                db.createStatement().execute("CREATE TABLE android_preflight(value INTEGER)");
            }
            // ServerStore uses JavaScript for JSON formatting during save. Java 17
            // needs the separately packaged Nashorn provider; fail before boot if absent.
            javax.script.ScriptEngine javascript = new javax.script.ScriptEngineManager().getEngineByName("JavaScript");
            if (javascript == null) throw new IllegalStateException("World-save JavaScript engine is missing");
            javascript.eval("JSON.stringify(JSON.parse('{\"check\":true}'), null, 2)");
            core.Server.main(args);
            if (!core.Server.getRunning()) throw new IllegalStateException("Server did not finish startup");
            System.out.println("[scape] SERVER_READY " + System.getProperty("scape.session", "host-test"));
            MemoryDiagnostics.sample("ready");
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }
}
