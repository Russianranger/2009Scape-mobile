# Single-player preview 4

**Play SD locally** could freeze at “Started 3d Library” because the bundled ViewDistance plugin called OpenGL while drawing the login screen in software mode. ViewDistance 6.1 uses fog controls only in HD. The SD launcher now explicitly selects software rendering, waits for the first frame and reconnects the frame reader when Android recreates the surface.

**Play HD locally** could crash in the graphics driver when the default GL4ES renderer mistook a memory address for an index-buffer offset. Local HD now disables the GL4ES buffer optimization that triggered this crash. The World 1 login screen rendered in high detail with the fix on an Android 15 emulator.

Both local play modes prepare their connection off the launcher’s UI thread and start Java with an initial heap of up to 256 MiB. The client can grow to the maximum selected in Settings. This update also fixes a native signal handler that could crash on a closed pipe and reports which library failed if Android cannot load the Java runtime.

Use **Save and stop** before installing the update over preview 3. The app uses the same package and signing certificate and retains the existing world and client settings.

If Android closes the client or reports that it is unresponsive, reopen **Single-player → Diagnostics → Export client and server logs**. You can export immediately after updating to look for Android’s record of an earlier failure. The ZIP includes recent client startup stages and, when Android retains them, the process exit reason and an ANR trace or native crash report.
