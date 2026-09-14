# Single-player preview 1

An experimental build from `codex/integrated-singleplayer`, with a bundled headless
2009scape server and automatic local client launch. This is a preview for device testing.

- Open **Single-player server** from the launcher, then **Play HD locally** or **Play SD locally**.
- Server, cache, Android Java runtime, SQLite and world-save dependencies install automatically.
- Use the notification to return to status/logs; use **Save and stop** when finished.
- World backup/restore and log export are available on the server screen.
- The client JAR, plugins and controller bindings are preserved.

Server requirements: Android 13+ ARM64. No Termux, Ubuntu or root required. The client uses its
existing configured runtime. This build installs over 3.2 with the same package/signing key.
It starts a fresh local world and does not automatically migrate your Termux saves.

Validation: Android build and unit tests, two host server boot/shutdown cycles, local-only
listener, real JS5 handshake, SQLite integrity and world JSON persistence. Android runtime
startup and interactive client login, gameplay and character reload require testing on the Thor.

Please test a new character, make an inventory/skill change, log out, save and stop, then relaunch
and confirm it is preserved. Export logs if startup or connection fails. Back up worlds before
uninstalling or clearing app data.
