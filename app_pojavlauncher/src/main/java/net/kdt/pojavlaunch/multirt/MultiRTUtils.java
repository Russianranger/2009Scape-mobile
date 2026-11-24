package net.kdt.pojavlaunch.multirt;

import static net.kdt.pojavlaunch.Tools.NATIVE_LIB_DIR;
import static org.apache.commons.io.FileUtils.listFiles;

import android.system.Os;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.Architecture;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

public class MultiRTUtils {

    private static final ConcurrentHashMap<String, Runtime> sCache = new ConcurrentHashMap<>();

    private static final File RUNTIME_FOLDER = new File(Tools.MULTIRT_HOME);
    private static final String CLIENT_STAGE = ".client-java8.pending";
    private static final String JAVA_VERSION_STR = "JAVA_VERSION=\"";
    private static final String OS_ARCH_STR = "OS_ARCH=\"";

    public static List<Runtime> getRuntimes() {
        if(!RUNTIME_FOLDER.exists() && !RUNTIME_FOLDER.mkdirs()) {
            throw new RuntimeException("Failed to create runtime directory");
        }

        ArrayList<Runtime> runtimes = new ArrayList<>();
        File[] files = RUNTIME_FOLDER.listFiles();
        if(files != null) for(File f : files) {
            if (f.isDirectory() && !CLIENT_STAGE.equals(f.getName())) runtimes.add(read(f.getName()));
        }
        else throw new RuntimeException("The runtime directory does not exist");

        return runtimes;
    }

    public static String getExactJreName(int majorVersion) {
        List<Runtime> runtimes = getRuntimes();
        for(Runtime r : runtimes)
            if(r.javaVersion == majorVersion)return r.name;

        return null;
    }

    public static Runtime findClientRuntime() {
        return Runtime.selectClient(getRuntimes(), RUNTIME_FOLDER, Tools.DEVICE_ARCHITECTURE,
                LauncherPreferences.PREF_DEFAULT_RUNTIME);
    }

    static boolean isUsableClientRuntime(Runtime runtime) {
        return runtime.isUsableClient(new File(RUNTIME_FOLDER, runtime.name), Tools.DEVICE_ARCHITECTURE);
    }

    public static Runtime requireClientRuntime() throws IOException {
        Runtime runtime = findClientRuntime();
        if (runtime == null) {
            throw new IOException("The client needs an Android Java 8 runtime for "
                    + Architecture.archAsString(Tools.DEVICE_ARCHITECTURE)
                    + ". Reopen the launcher to retry client setup. If setup still fails, install the single-player APK as an update.");
        }
        return runtime;
    }

    public static String getNearestJreName(int majorVersion) {
        List<Runtime> runtimes = getRuntimes();
        int diff_factor = Integer.MAX_VALUE;
        String result = null;
        for(Runtime r : runtimes) {
            if(r.javaVersion < majorVersion) continue; // lower - not useful

            int currentFactor = r.javaVersion - majorVersion;
            if(diff_factor > currentFactor) {
                result = r.name;
                diff_factor = currentFactor;
            }
        }

        return result;
    }

    public static void installRuntimeNamed(String nativeLibDir, InputStream runtimeInputStream, String name) throws IOException {
        File dest = new File(RUNTIME_FOLDER,"/"+name);
        if(dest.exists()) FileUtils.deleteDirectory(dest);
        uncompressTarXZ(runtimeInputStream,dest);
        runtimeInputStream.close();
        unpack200(nativeLibDir,RUNTIME_FOLDER + "/" + name);
        ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME);
        forceReread(name);
    }

    public static void postPrepare(String name) throws IOException {
        File dest = new File(RUNTIME_FOLDER,"/" + name);
        if(!dest.exists()) return;
        Runtime runtime = read(name);
        String libFolder = "lib";
        if(new File(dest,libFolder + "/" + runtime.arch).exists()) libFolder = libFolder + "/" + runtime.arch;
        File ftIn = new File(dest, libFolder + "/libfreetype.so.6");
        File ftOut = new File(dest, libFolder + "/libfreetype.so");
        if (ftIn.exists() && (!ftOut.exists() || ftIn.length() != ftOut.length())) {
            if(!ftIn.renameTo(ftOut)) throw new IOException("Failed to rename freetype");
        }

        // Refresh libraries
        copyDummyNativeLib("libawt_xawt.so", dest, libFolder);
    }

    /** Publish the built-in runtime only after both archives and native libraries are installed. */
    public static void installBundledJava8(InputStream universal, InputStream nativeBins) throws IOException {
        File stage = new File(RUNTIME_FOLDER, CLIENT_STAGE);
        try {
            if (stage.exists()) FileUtils.deleteDirectory(stage);
            uncompressTarXZ(universal, stage);
            uncompressTarXZ(nativeBins, stage);
            unpack200(NATIVE_LIB_DIR, stage.getPath());
            forceReread(CLIENT_STAGE);
            postPrepare(CLIENT_STAGE);
            if (!isUsableClientRuntime(forceReread(CLIENT_STAGE)))
                throw new IOException("The bundled Java 8 runtime is incomplete for this device");

            // Preserve any existing entries, including a user's incompatible or damaged Internal.
            File destination = new File(RUNTIME_FOLDER, "Internal");
            for (int i = 1; destination.exists(); i++)
                destination = new File(RUNTIME_FOLDER, "2009Scape-Java8-" + i);
            if (!stage.renameTo(destination)) throw new IOException("Cannot install " + destination);
            forceReread(destination.getName());
        } finally {
            sCache.remove(CLIENT_STAGE);
            if (stage.exists()) FileUtils.deleteDirectory(stage);
        }
    }

    public static void removeRuntimeNamed(String name) throws IOException {
        File dest = new File(RUNTIME_FOLDER,"/"+name);
        if(dest.exists()) {
            FileUtils.deleteDirectory(dest);
            sCache.remove(name);
        }
    }

    public static File getRuntimeHome(String name) {
        File dest = new File(RUNTIME_FOLDER, name);
        Log.i("MiltiRTUitls", "Dest exists? "+dest.exists());
        if((!dest.exists()) || MultiRTUtils.forceReread(name).versionString == null) throw new RuntimeException("Selected runtime is broken!");
        return dest;
    }

    public static Runtime forceReread(String name) {
        sCache.remove(name);
        return read(name);
    }

    public static Runtime read(String name) {
        Runtime returnRuntime = sCache.get(name);
        if(returnRuntime != null) return returnRuntime;
        File release = new File(RUNTIME_FOLDER,name+"/release");
        if(!release.exists()) {
            return new Runtime(name);
        }
        try {
            String content = Tools.read(release.getAbsolutePath());
            String javaVersion = Tools.extractUntilCharacter(content, JAVA_VERSION_STR, '"');
            String osArch = Tools.extractUntilCharacter(content, OS_ARCH_STR, '"');
            if(javaVersion != null && osArch != null) {
                String[] javaVersionSplit = javaVersion.split("\\.", -1);
                int javaVersionInt;
                javaVersionInt = Integer.parseInt(javaVersionSplit.length > 1 && javaVersionSplit[0].equals("1")
                        ? javaVersionSplit[1] : javaVersionSplit[0]);
                returnRuntime = new Runtime(name, javaVersion, osArch, javaVersionInt);
            }else{
                returnRuntime =  new Runtime(name);
            }
        }catch(IOException | NumberFormatException e) {
            returnRuntime =  new Runtime(name);
        }
        sCache.put(name, returnRuntime);
        return returnRuntime;
    }

    /**
     * Unpacks all .pack files into .jar Serves only for java 8, as java 9 brought project jigsaw
     * @param nativeLibraryDir The native lib path, required to execute the unpack200 binary
     * @param runtimePath The path to the runtime to walk into
     */
    private static void unpack200(String nativeLibraryDir, String runtimePath) {

        File basePath = new File(runtimePath);
        Collection<File> files = listFiles(basePath, new String[]{"pack"}, true);

        File workdir = new File(nativeLibraryDir);

        ProcessBuilder processBuilder = new ProcessBuilder().directory(workdir);
        for(File jarFile : files){
            try{
                Process process = processBuilder.command("./libunpack200.so", "-r", jarFile.getAbsolutePath(), jarFile.getAbsolutePath().replace(".pack", "")).start();
                process.waitFor();
            }catch (InterruptedException | IOException e) {
                Log.e("MULTIRT", "Failed to unpack the runtime !");
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static void copyDummyNativeLib(String name, File dest, String libFolder) throws IOException {
        File fileLib = new File(dest, "/"+libFolder + "/" + name);
        try (InputStream is = new FileInputStream(new File(NATIVE_LIB_DIR, name));
             FileOutputStream os = new FileOutputStream(fileLib)) {
            IOUtils.copy(is, os);
        }
    }

    private static void uncompressTarXZ(final InputStream tarFileInputStream, final File dest) throws IOException {
        if(dest.isFile()) throw new IOException("Attempting to unpack into a file");
        if(!dest.exists() && !dest.mkdirs()) throw new IOException("Failed to create destination directory");

        String root = dest.getCanonicalPath();
        byte[] buffer = new byte[8192];
        try (TarArchiveInputStream tarIn = new TarArchiveInputStream(
                new XZCompressorInputStream(tarFileInputStream)
        )) {
            TarArchiveEntry tarEntry = tarIn.getNextTarEntry();
            // tarIn is a TarArchiveInputStream
            while (tarEntry != null) {

                final String tarEntryName = tarEntry.getName();
                // publishProgress(null, "Unpacking " + tarEntry.getName());
                ProgressLayout.setProgress(ProgressLayout.UNPACK_RUNTIME, 100, R.string.global_unpacking, tarEntryName);

                File destPath = new File(dest, tarEntry.getName());
                String path = destPath.getCanonicalPath();
                if (!path.equals(root) && !path.startsWith(root + File.separator))
                    throw new IOException("Runtime entry outside installation directory: " + tarEntryName);
                File destParent = destPath.getParentFile();
                if (tarEntry.isSymbolicLink()) {
                    if(destParent != null && !destParent.exists() && !destParent.mkdirs())
                        throw new IOException("Failed to create parent directory for symlink");
                    try {
                        String link = tarEntry.getLinkName();
                        if (new File(link).isAbsolute() ||
                                !new File(destParent, link).getCanonicalPath().startsWith(root + File.separator))
                            throw new IOException("Runtime link outside installation directory: " + tarEntryName);
                        Os.symlink(link, destPath.getPath());
                    } catch (android.system.ErrnoException e) {
                        throw new IOException("Cannot install runtime link " + tarEntryName, e);
                    }

                } else if (tarEntry.isDirectory()) {
                    if(!destPath.exists() && !destPath.mkdirs())
                        throw new IOException("Failed to create directory");
                } else if (!destPath.exists() || destPath.length() != tarEntry.getSize()) {
                    if(destParent != null && !destParent.exists() && !destParent.mkdirs())
                        throw new IOException("Failed to create parent directory for file");

                    try (FileOutputStream os = new FileOutputStream(destPath)) {
                        IOUtils.copyLarge(tarIn, os, buffer);
                    }

                }
                tarEntry = tarIn.getNextTarEntry();
            }
        }
    }
}
