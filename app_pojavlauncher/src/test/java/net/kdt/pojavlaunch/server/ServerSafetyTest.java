package net.kdt.pojavlaunch.server;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.*;
import static org.junit.Assert.*;

public class ServerSafetyTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private InputStream zip(String path, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(path)); zip.write(content.getBytes("UTF-8")); zip.closeEntry();
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }
    @Test public void rejectsZipTraversal() throws Exception {
        File root = temp.newFolder();
        try { SafeZip.extract(zip("../escape.txt", "bad"), root, 100); fail(); }
        catch (IOException expected) { assertFalse(new File(root.getParentFile(), "escape.txt").exists()); }
    }
    @Test public void rejectsOversizedArchive() throws Exception {
        try { SafeZip.extract(zip("file", "123456789"), temp.newFolder(), 4); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("large")); }
    }
    @Test public void extractsWorldData() throws Exception {
        File root = temp.newFolder(); SafeZip.extract(zip("data/players/example.json", "{}"), root, 100);
        assertEquals("{}", new String(Files.readAllBytes(new File(root, "data/players/example.json").toPath()), "UTF-8"));
    }
    @Test public void headlessArgumentsKeepRuntimeAndDriverIndependent() {
        File root = new File("/private/world with spaces");
        List<String> args = ServerLaunch.arguments(new File("/apk/lib"), new File("/private/runtime"), new File("/private/engine"), root, 2048, "owned-session");
        assertTrue(args.contains("-Djava.awt.headless=true"));
        assertTrue(args.contains("-Dorg.sqlite.lib.name=libscape_sqlitejdbc.so"));
        assertTrue(args.contains("-Duser.home=/private/world with spaces"));
        assertTrue(args.get(args.indexOf("-cp") + 1).contains("/lib/*"));
        assertFalse(args.toString().contains("rt4.jar"));
        assertEquals("scape.ServerMain", args.get(args.size() - 2));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsUnboundedHeap() {
        File f = new File("/tmp"); ServerLaunch.arguments(f, f, f, f, 32000, "test");
    }
}
