# Building from source

Use JDK 25 and the included Gradle 9.6.0 wrapper. Set `JAVA_HOME` to your JDK before running Gradle.

## Local dependencies

Supply these compile dependencies in a local directory:

| Dependency | File name |
|---|---|
| Vitrail | `vitrail-0.13.0-beta.vultrum.4+mc26.3.jar` |
| Sodium | `sodium-fabric-0.9.3-beta.1+mc26.3.jar` |
| Mod Menu | `modmenu-21.0.0.jar` |

The build checks their SHA-256 hashes against [reference-dependencies.json](../gradle/reference-dependencies.json). These JARs are compile-only dependencies and are excluded from the mod JAR.

The shader adapter test also requires an unmodified `ComplementaryUnbound_r5.9.3.zip`. Supply the pack separately under its own license. The build reads it for the test and leaves it unchanged.

Place the three JARs and the shader ZIP in `local-dependencies/`, or create `gradle-local.properties` in the project root:

```properties
referenceDirectory=/path/to/mod-jars
referencePack=/path/to/ComplementaryUnbound_r5.9.3.zip
```

Use forward slashes for Windows paths, such as `D:/Minecraft/mods`. Both the local properties file and `local-dependencies/` are excluded from Git.

## Commands

On Windows:

```powershell
.\gradlew.bat --no-daemon build
```

On Linux or macOS:

```sh
./gradlew --no-daemon build
```

You can override the local dependency paths on the command line:

```sh
./gradlew --no-daemon build -PreferenceDirectory=/path/to/mod-jars -PreferencePack=/path/to/ComplementaryUnbound_r5.9.3.zip
```

Gradle downloads Minecraft, Fabric Loader, JUnit and the build tooling on the first build. Add `--offline` when those dependencies are already cached.

`build` compiles the mod and runs 41 unit tests. Results are written to `build/reports/tests/test/`. The shader adapter test checks the supplied pack's star source hash as well as the patch behavior.

The output is `build/libs/sleep-trails-0.1.0-dev.10+mc26.3.jar`. The adjacent `-sources.jar` contains source code and is not an installable mod.

## Development client

```sh
./gradlew runClient
```

This uses `run/client/`. The compile-only mods and shader pack are not installed into that client automatically. Add them separately if you want to test their integrations.
