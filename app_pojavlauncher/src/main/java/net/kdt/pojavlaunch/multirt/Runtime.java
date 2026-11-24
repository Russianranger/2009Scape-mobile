package net.kdt.pojavlaunch.multirt;

import net.kdt.pojavlaunch.utils.Architecture;
import java.io.File;
import java.util.List;
import java.util.Objects;

public class Runtime {
    public final String name;
    public final String versionString;
    public final String arch;
    public final int javaVersion;
    public Runtime(String name) {
        this.name = name;
        this.versionString = null;
        this.arch = null;
        this.javaVersion = 0;
    }
    Runtime(String name, String versionString, String arch, int javaVersion) {
        this.name = name;
        this.versionString = versionString;
        this.arch = arch;
        this.javaVersion = javaVersion;
    }

    public String librarySubdirectory(File home) {
        if (new File(home, "lib/" + arch).isDirectory()) return "lib/" + arch;
        if (Architecture.archAsInt(arch) == Architecture.ARCH_X86) {
            for (String variant : new String[]{"i586", "i486", "i386"}) {
                if (new File(home, "lib/" + variant).isDirectory()) return "lib/" + variant;
            }
        }
        return "lib";
    }

    static Runtime selectClient(List<Runtime> installed, File root, int architecture, String preferred) {
        Runtime fallback = null;
        for (Runtime runtime : installed) {
            if (!runtime.isUsableClient(new File(root, runtime.name), architecture)) continue;
            if (runtime.name.equals(preferred)) return runtime;
            if (fallback == null || "Internal".equals(runtime.name)) fallback = runtime;
        }
        return fallback;
    }

    boolean isUsableClient(File home, int architecture) {
        if (javaVersion != 8 || arch == null || Architecture.archAsInt(arch) != architecture) return false;
        File lib = new File(home, librarySubdirectory(home));
        return hasFile(home, "lib/rt.jar") && hasFile(lib, "jli/libjli.so")
                && (hasFile(lib, "server/libjvm.so") || hasFile(lib, "client/libjvm.so"));
    }

    private static boolean hasFile(File directory, String path) {
        File file = new File(directory, path);
        return file.isFile() && file.length() > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Runtime runtime = (Runtime) o;
        return name.equals(runtime.name);
    }
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
