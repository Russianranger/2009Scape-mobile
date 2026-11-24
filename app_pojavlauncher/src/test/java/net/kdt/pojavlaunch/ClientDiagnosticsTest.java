package net.kdt.pojavlaunch;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.*;
import static org.junit.Assert.*;

public class ClientDiagnosticsTest {
    @Rule public TemporaryFolder dir = new TemporaryFolder();

    @Test public void loaderFailureKeepsCauseAndRemovesSecretLines() {
        Exception error = new IOException("launcher", new UnsatisfiedLinkError("dlopen failed: libjvm.so\npassword=excluded"));
        String report = ClientDiagnostics.describeFailure(error);
        assertTrue(report.contains("UnsatisfiedLinkError: dlopen failed: libjvm.so"));
        assertFalse(report.contains("excluded"));
        assertTrue(report.contains("at "));
    }

    @Test public void tombstoneBytesAreKeptWholeOrOmitted() throws IOException {
        byte[] bytes = new byte[]{0, -1, 7, 0};
        assertArrayEquals(bytes, ClientDiagnostics.boundedTrace(new ByteArrayInputStream(bytes)));
        assertNull(ClientDiagnostics.boundedTrace(new ByteArrayInputStream(new byte[2 * 1024 * 1024 + 1])));
    }

    @Test public void anrTraceIncludesOnlyTheClientProcess() throws IOException {
        String dump = "----- pid 20 at t -----\nother app\n----- end 20 -----\n"
                + "----- pid 2 at t -----\nclient stack\n----- end 2 -----\n";
        byte[] result = ClientDiagnostics.anrTrace(new ByteArrayInputStream(dump.getBytes(StandardCharsets.UTF_8)), 2);
        String text = new String(result, StandardCharsets.UTF_8);
        assertTrue(text.contains("client stack"));
        assertFalse(text.contains("other app"));
    }

    @Test public void exportsBoundedStartAndEndOfClientLog() throws IOException {
        File file = dir.newFile("latestlog.txt");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("start\nsession_id=SECRET\n".getBytes(StandardCharsets.UTF_8));
            byte[] block = new byte[1024]; Arrays.fill(block, (byte)'x'); block[1023] = '\n';
            for (int i=0;i<300;i++) out.write(block);
            out.write("end".getBytes(StandardCharsets.UTF_8));
        }
        File zip = dir.newFile("logs.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
            ClientDiagnostics.exportLog(out, file, "client/latestlog.txt");
        }
        try (ZipFile saved = new ZipFile(zip)) {
            ZipEntry entry = saved.getEntry("client/latestlog.txt");
            byte[] result = new byte[(int)entry.getSize()];
            try (DataInputStream in = new DataInputStream(saved.getInputStream(entry))) { in.readFully(result); }
            String text = new String(result, StandardCharsets.UTF_8);
            assertTrue(text.startsWith("start\n"));
            assertTrue(text.contains("middle omitted"));
            assertTrue(text.endsWith("end"));
            assertFalse(text.contains("SECRET"));
        }
    }
}
