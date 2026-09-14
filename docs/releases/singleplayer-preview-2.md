# Single-player preview 2

The launcher now places Single-player beside Settings, clear of the detail-selection artwork. The local-world screen uses the launcher's brown-and-gold palette, prominent HD/SD play buttons, a separate status panel, and expandable world options and diagnostics. Picking a detail level still starts the server and opens the existing client automatically.

This preview fixes retained launcher progress listeners, removes Activity references from background backup/log operations, closes child-process streams after exit, and clears the delayed shutdown callback. Hidden diagnostics no longer copy the live log over IPC every second; unchanged text is not redrawn.

Server logs now include passive memory samples every 30 seconds: JVM heap used/committed/maximum, non-heap usage, last collection pool usage, garbage collection count/time, thread count, and process RSS. No garbage collection is forced. The samples help investigate trends; they do not certify that the app or server is leak-free.

The supplied Thor log showed successful startup in 26.8 seconds, gameplay, and normal saving/shutdown with exit code 0. It contained no OOM or fatal crash. See `docs/diagnostics-2026-09-14.md` for the warning review and memory limits of that evidence.

Install over preview 1. Application ID and signing certificate match preview 1 and release 3.2. Existing world data is retained. Android 13+ ARM64 is required for the server. Keep using Save and stop when finished.

The build workflow runs Android unit tests, two host server boot/save/restart cycles with real memory samples and a JS5 handshake, SQLite/world persistence checks, and APK version/signature/content verification. The new UI and longer memory trends still need device validation.
