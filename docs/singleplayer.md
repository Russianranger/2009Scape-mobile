# Integrated single-player preview

Branch: `codex/integrated-singleplayer`. The Android client JAR and controller code are unchanged.

## On the device

The integrated server requires Android 13+ ARM64 (including AYN Thor). It runs without root,
Termux, proot or a desktop GUI. The existing client runtime must already be configured, as in 3.2.

1. Open **Single-player server** from the launcher.
2. Choose **Play HD locally** or **Play SD locally**. The app installs the bundled server,
   cache, Android Java 17 runtime and database driver on first use, then starts the server.
3. The existing client opens after that server reports readiness. Both game and cache
   connections use the private local profile. The normal launcher buttons keep using the
   existing connection configuration.
   Close a running online client before switching it to local play. Reopening the same local
   client from this screen brings its existing activity forward instead of creating another JVM.
4. Return to the server screen through its notification. Use **Save and stop** when finished.
5. While stopped, **Back up world** exports the complete world, including characters,
   economy and cache. **Restore world backup** accepts these version-matched backups and
   retains the previous world in a dated directory. Export backups before uninstalling.

This preview starts a new world. It does not import an existing Termux directory automatically.
The server continues while the client is closed or the server screen is in the background.
Android force-stop, process killing and power loss can still interrupt saving. The native runner
signals the child JVM if its supervisor dies, and a world lock prevents concurrent writers.

## Implementation

- `server/SingleplayerActivity.java`: controls, live log, memory selection, backup and restore.
- `server/LocalServerService.java`: foreground supervisor in Android process `:server`, partial
  wake lock, bounded logs, process-owned readiness token, normal `stop` command.
- `server/ServerFiles.java`: verifies assets and installs versioned engine/runtime directories
  atomically; world data has separate persistent storage and a compatibility marker.
- `server/LocalClient.java`: copies existing client options to a separate local connection
  profile. Both original HD and SD launch paths select it only for local launches.
- `server-runtime/native`: APK-installed process runner and Android Java image path adapter.
  Runtime library filenames are prefixed to avoid interfering with the client runtime.
- `server-runtime/src/scape/ServerMain.java`: desktop JVM bootstrap, SQLite and JavaScript
  engine preflight, server startup and readiness marker.
- `server-runtime/LocalServerPatcher.java`: a checked bytecode transformation changes the
  server's one listening socket to `127.0.0.1`. The pinned configuration disables websockets,
  server GUI, map preloading, daily restart, external account persistence and authentication.
- `scripts/prepare-singleplayer.py`: verifies every build input, supplies Android ARM64 SQLite,
  removes the server's old bundled SQLite classes, and packages Nashorn for Java 17 world saves.

The server's world-1 listener is 43595. The client uses upstream's port-base convention:
`server_port=43594`, `world=1`, `wl_port=43595`, `js5_port=43595`.

The first server memory default is `-Xms256m -Xmx2048m`; it is independent of client settings.
Only runtime data is extracted into writable app storage. Executable native libraries and the
runner are installed by Android from the APK. No shell commands, root or runtime downloads
are used on the device.

## Reproduce and validate

Use Java 17, Android SDK 33 / build tools 33.0.2, and NDK 25.2.9519653:

```sh
./gradlew :app_pojavlauncher:testDebugUnitTest :app_pojavlauncher:assembleDebug
python3 scripts/test-singleplayer.py
python3 scripts/verify-singleplayer.py app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk
```

GitHub Actions performs these checks and attaches the APK and reports to the branch run.
The host test checks two complete boot/shutdown cycles, local-only binding, the real JS5 cache
handshake, SQLite integrity, and world JSON persistence. It uses the host JVM, so it cannot
prove execution on the Thor. Device acceptance must cover login, movement, inventory/skill
changes, logout, save-and-stop, app relaunch, and character reload, followed by background and
screen-lock tests with both server and client active.

Known upstream behavior: the update worker can log `InterruptedException: sleep interrupted`
during normal shutdown. Do not treat exit code 0 alone as evidence that world saving succeeded;
the host persistence test also validates stored JSON. Export logs when reporting a device failure.

## Preview 2 interface and diagnostics

Single-player is in the launcher footer next to Settings. Its HD/SD buttons start the local world if needed and open the client when ready. World options contains the heap limit and backup/restore actions; Diagnostics contains log export and the live log. Save and stop remains visible in the main controls and notification.

Logs now include passive server JVM memory/GC/thread/RSS samples every 30 seconds. See [the Thor log review](diagnostics-2026-09-14.md) for interpretation, the fixed launcher listener retention, and a suggested longer device test. A short successful session cannot prove the absence of memory leaks.
