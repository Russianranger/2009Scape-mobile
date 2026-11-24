# Thor session review — 14 September 2026

Source: user-supplied `2009scape-server-logs.zip`, session `16d8f703-ad20-4ecf-9748-ccea9d097432`, Android 13 / AYN Thor, preview 1. The supplied logs and character data are not committed to the repository.

## What the log establishes

- Runtime/world preparation and the native world lock succeeded.
- Headless server startup completed in 26,811 ms. The owned readiness token was received and the app connected to the local port.
- One player logged in and played for approximately five minutes. The server ran for approximately six minutes after readiness.
- Normal shutdown ran through networking/bot/pulse termination, saved data, reported successful termination, and exited with code 0.
- No out-of-memory exception, fatal signal, database exception or failed-save exception appears in the supplied log.

## Warnings

| Log entry | Assessment |
| --- | --- |
| Android linker ignores `DT_RPATH` | Nonfatal in this session: the configured library paths worked and startup completed. Preserve the tested, checksum-pinned runtime binaries. |
| SLF4J has no provider | Some third-party logging falls back to a no-op logger. The server's own logs are present. This is a logging limitation, not evidence of a memory leak or startup failure. |
| Save missing **or** corrupt on login | Consistent with a new character's first login. The message cannot distinguish that from a damaged save. A returning character unexpectedly starting fresh would need separate investigation and a backup inspection. |
| Eight long pulse/bot warnings, 30–52 ms | Isolated slower work was reported. This log alone does not measure overall tick or client-frame performance. |
| `Major Update Worker` interrupted during shutdown | Occurs immediately after the normal stop sequence requests worker termination. Saving then succeeds and the process exits 0. Retain this diagnostic; do not blanket-filter exceptions. |

## Memory findings and changes

The 2048 MiB figure is the configured heap **limit**, not measured usage. The attachment contains no heap, RSS/PSS, GC, or thread-count time series, and no client-process log. It cannot establish absence of leaks in either desktop JVM or Android UI.

Code review found a definite retention path: static `ProgressKeeper` lists held `ScapeLauncher`'s progress view and its per-task observers after `onDestroy`. Preview 2 unregisters all three listener registrations. The background log/backup operations now use application context and a weak Activity reference so long file operations do not retain a closed screen. Server process streams and delayed shutdown notifications are also explicitly cleaned up.

Preview 2 prints a `[scape-memory]` JSON record at startup, readiness, and every 30 seconds. The sampler is a single daemon thread, does not retain historical samples, and never forces GC. Existing bounded log rotation/export applies. Heap and GC metrics use the JDK management interfaces; RSS comes from the server process's `/proc/self/status`, with `-1` meaning unavailable.

`heapUsedBytes` includes garbage not yet collected. `lastCollectionUsedBytes` sums available heap-pool collection snapshots, which may refer to different collections; `collectionPools` records how many pools reported. RSS includes heap, native memory, stacks and mapped pages, and is not a substitute for PSS. These are server-JVM measurements, not measurements of the client or Android Activity heaps. See the [JDK memory interface](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryMXBean.html) and [memory-pool interface](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryPoolMXBean.html).

For a useful follow-up, play for 30–60 minutes, revisit the same areas, return to the launcher/server screen several times, then use Save and stop and export diagnostics. Compare memory after natural GC across similar workloads. Sustained growth of post-collection usage, thread count or RSS warrants heap/native profiling; an increasing committed heap during warmup alone does not prove a leak. A separate Android/client memory capture is needed to validate those processes dynamically. This is consistent with Android's [Activity lifecycle cleanup guidance](https://developer.android.com/guide/components/activities/activity-lifecycle).
