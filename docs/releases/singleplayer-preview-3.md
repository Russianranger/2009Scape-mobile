# Single-player preview 3

Preview 2 could start the local server and then stop with a missing `config.json` when you chose **Play HD locally** or **Play SD locally**. The APK lacked the version file the launcher reads before installing the client. Preview 3 packages that file with its matching LWJGL JAR so client setup can finish.

On a fresh installation, the launcher can install its Java 8 client runtime directly from the APK. It includes the same Android binpack and license notices shipped in the [upstream 2.4 release](https://github.com/2009scape/2009Scape-mobile/releases/tag/hd-2.4). The server continues to use its own Java 17 runtime.

Use **Save and stop** if your server is running, then install the APK over preview 2. This is an in-place update for `net.kdt.pojavlaunch.debug` (version code `9950005`). The build verifies the same signing certificate as preview 2. Your local world and characters stay in the existing installation.

On the Odin 3, open **Single-player** and choose the same HD or SD option that previously failed. The device check is reaching the client login screen and entering the existing world.
