# Runtime and server provenance

All binary build inputs are listed with immutable URLs and SHA-256 hashes in `dependencies.json`.
Generated binaries and game/cache assets are build output, not committed to this repository.

- 2009scape single-player distribution: `f00fcb7e8f8916ed016cc8eb7a9f0bb19f490b60`,
  https://gitlab.com/2009scape/singleplayer/windows. Its AGPL license is included in engine.zip.
  The sole server bytecode change is the localhost listener transformation in LocalServerPatcher.java.
- Android OpenJDK 17.0.20 runtime: Russianranger/wurm-android release
  `runtime-17.0.20-android-1`, source `8cbbca61432426a3441aa08838d930ef954ea1ba`,
  recipe `b9a87bedf1defb09ebdf661a16b5b7c8462559ee`. Runtime legal notices are preserved.
  Source and build recipe: https://github.com/Russianranger/wurm-android/tree/b9a87bedf1defb09ebdf661a16b5b7c8462559ee/runtime-build
- JVM image path adapter and world lock adapted from Russianranger/wurm-android
  `2e41fb091ee75a76b9116e934abecff90bc735d9`, `runtime-probe/native/`.
- Xerial SQLite JDBC 3.53.2.1 and its Android native artifact, Apache-2.0;
  upstream artifacts and notices are preserved. https://github.com/xerial/sqlite-jdbc
- OpenJDK Nashorn 15.4 (GPL-2.0 with Classpath Exception) and ASM 7.3.1 (BSD),
  unmodified JARs with their included notices. https://github.com/openjdk/nashorn
- ASM 9.7.1 is used only at build time to patch the single listener.

Native runtime filenames are changed when packaging, with app-private symlinks preserving
the JRE's expected image layout. Native binary contents remain byte-identical.
