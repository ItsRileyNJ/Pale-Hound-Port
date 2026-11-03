# The Pale Hound — NeoForge 1.21.1

Target: Minecraft **1.21.1**, NeoForge **21.1.262**, Java **21**, GeckoLib **4.9.3**.

This is a source migration from the supplied `Pale_Hound-1.20.1-1.10.0.jar`.
The port builds against the target loader and supplied GeckoLib JAR. See
[validation status](docs/PORT_STATUS.md) for checks and remaining manual comparisons.

## Build and run

With a Java 21 JDK installed and `JAVA_HOME` set:

```sh
./gradlew build
./gradlew runClient
./gradlew runServer
./gradlew runGameTestServer
```

On Windows use `gradlew.bat`. The server requires accepting Minecraft's EULA yourself
in its generated run directory. The supplied GeckoLib JAR is in `libs/` for compilation
and development launches. It is not embedded in the output mod: install GeckoLib 4.9.3
alongside the built mod on both the client and server.

Build output is `build/libs/Pale_Hound-1.10.0-neoforge-1.21.1.jar`.
In this managed cloud, `tools/cloud-gradle.sh` selects the retained JDK and proxy trust settings.
The supplied NeoForge installer identifies the target loader; it is not a mod dependency
or a replacement for the NeoForge development toolchain used by Gradle.

## Checks

```sh
python3 -m unittest discover -s tests -v
./gradlew processResources
```

The seven Python checks compare assets, original AI goal bodies, configuration defaults
and category order against a baseline recovered from the original JAR; check the data
resource migration, sound/animation references, and the supplied dependency checksum.
They do not validate Java type compatibility, mixin application, rendering, spawning,
networking, or game behavior. Use `runGameTestServer` for four in-game server smoke tests.
Use `./gradlew runClient -PclientAssetSmoke` to check model and animation loading
and exit automatically once the title screen is reached. On first launch, dismiss
the accessibility welcome screen. A display/OpenGL context is required. Test classes and their
structure template are excluded from the distributable JAR. Manual multiplayer and
visual/behavior comparisons against the original remain separate from these checks.

## Provenance

The original JAR declares the MIT license and credits TheSpukuz, Llamii_Llama, Gargin,
Cadentem, and Schpalrbo. Original models, textures, sounds, and logo are preserved byte for byte.
Animation values/timings are retained with a malformed easing expression repaired;
translations retain existing entries and add the original subtitle text. Original credits are retained in mod metadata.
The source was recovered with Vineflower 1.11.1, checked against CFR 0.152, and mapped
from 1.20.1 SRG names to Mojang names before API migration. The baseline records the
original JAR SHA-256. Original author source was not supplied.
