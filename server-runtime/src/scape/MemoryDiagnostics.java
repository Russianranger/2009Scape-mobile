package scape;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.management.*;
import java.time.Instant;

/** Bounded, passive server JVM measurements. Never forces GC or retains samples. */
final class MemoryDiagnostics {
    static void start() {
        sample("startup");
        Thread sampler = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(30_000);
                    sample("periodic");
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "scape-memory-diagnostics");
        sampler.setDaemon(true);
        sampler.start();
    }

    static synchronized void sample(String phase) {
        try {
            MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memory.getHeapMemoryUsage();
            long count = 0, time = 0, afterGc = 0;
            int pools = 0;
            for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (collector.getCollectionCount() >= 0) count += collector.getCollectionCount();
                if (collector.getCollectionTime() >= 0) time += collector.getCollectionTime();
            }
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                if (pool.getType() != MemoryType.HEAP) continue;
                MemoryUsage collected = pool.getCollectionUsage();
                if (collected != null) { afterGc += collected.getUsed(); pools++; }
            }
            // Each pool's last collection may have occurred at a different time.
            // These are trend data, not a synchronized live-object/leak measurement.
            System.out.println("[scape-memory] {\"time\":\"" + Instant.now() + "\",\"phase\":\"" + phase
                + "\",\"uptimeMs\":" + ManagementFactory.getRuntimeMXBean().getUptime()
                + ",\"heapUsedBytes\":" + heap.getUsed() + ",\"heapCommittedBytes\":" + heap.getCommitted()
                + ",\"heapMaxBytes\":" + heap.getMax() + ",\"nonHeapUsedBytes\":" + memory.getNonHeapMemoryUsage().getUsed()
                + ",\"lastCollectionUsedBytes\":" + (pools == 0 ? -1 : afterGc) + ",\"collectionPools\":" + pools
                + ",\"gcCount\":" + count + ",\"gcTimeMs\":" + time
                + ",\"threads\":" + ManagementFactory.getThreadMXBean().getThreadCount()
                + ",\"rssKiB\":" + rssKiB() + "}");
        } catch (RuntimeException | LinkageError unavailable) {
            // A diagnostic failure must not prevent playing or saving a world.
            System.out.println("[scape] Memory diagnostics unavailable: " + unavailable.getClass().getSimpleName());
        }
    }

    private static long rssKiB() {
        try (BufferedReader reader = Files.newBufferedReader(Path.of("/proc/self/status"))) {
            String line;
            while ((line = reader.readLine()) != null)
                if (line.startsWith("VmRSS:")) return Long.parseLong(line.trim().split("\\s+")[1]);
        } catch (Exception ignored) { /* procfs may be restricted; report unknown */ }
        return -1;
    }

    private MemoryDiagnostics() {}
}
