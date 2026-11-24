package net.kdt.pojavlaunch.multirt;

import net.kdt.pojavlaunch.utils.Architecture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class ClientRuntimeTest {
    @Rule public final TemporaryFolder temp = new TemporaryFolder();
    private static final int ARM64 = Architecture.ARCH_ARM64;

    private Runtime install(String name, String arch) throws IOException {
        File home = temp.newFolder(name);
        for (String entry : new String[]{"lib/rt.jar", "lib/" + arch + "/jli/libjli.so",
                "lib/" + arch + "/server/libjvm.so"}) {
            File target = new File(home, entry);
            Files.createDirectories(target.getParentFile().toPath());
            Files.write(target.toPath(), new byte[]{1});
        }
        return new Runtime(name, "1.8.0", arch, 8);
    }

    @Test public void acceptsConfiguredRuntimeAndFallsBackToInternal() throws IOException {
        Runtime imported = install("my-java8.tar.xz", "aarch64");
        Runtime internal = install("Internal", "aarch64");
        assertSame(imported, Runtime.selectClient(Arrays.asList(internal, imported), temp.getRoot(), ARM64, imported.name));
        assertSame(internal, Runtime.selectClient(Arrays.asList(imported, internal), temp.getRoot(), ARM64, "removed"));
    }

    @Test public void incompleteInternalDoesNotHideAnImportedRuntime() throws IOException {
        Runtime internal = install("Internal", "aarch64");
        Files.delete(new File(temp.getRoot(), "Internal/lib/aarch64/server/libjvm.so").toPath());
        Runtime imported = install("installed-java8", "aarch64");
        assertSame(imported, Runtime.selectClient(Arrays.asList(internal, imported), temp.getRoot(), ARM64, internal.name));
    }

    @Test public void ignoresWrongAbiWrongJavaVersionAndMissingInstall() throws IOException {
        Runtime x86 = install("intel", "amd64");
        Runtime java17 = new Runtime(install("newer", "aarch64").name, "17", "aarch64", 17);
        assertNull(Runtime.selectClient(Arrays.asList(x86, java17, new Runtime("Internal")), temp.getRoot(), ARM64, x86.name));
        assertNull(Runtime.selectClient(Collections.emptyList(), temp.getRoot(), ARM64, "Internal"));
    }
}
