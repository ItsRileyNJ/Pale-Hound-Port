# Port status

The port compiles for NeoForge 21.1.262 with GeckoLib 4.9.3.
Exhaustive visual and multiplayer equivalence to the original is not claimed.

## Implemented source migration

- NeoForge mod entry point, deferred registration, attributes, server tick, damage,
  dimension, command, and creative-tab events.
- All eleven original AI goal/roll classes retained. The only source-body changes in
  those classes are the 1.21 step-height API, mob-griefing hook, and glass base-class names.
- NeoForge client-bound custom payload registration replaces Forge SimpleChannel.
  The payload retains the original resource ID, position, volume, and pitch fields.
  The client keeps the original hard-coded playback volume/pitch (2.0/1.0), rather
  than silently changing existing sound behavior to honor the transmitted values.
- Client-only renderer registration and GeckoLib 4.9.3 packages/render color API.
- SynchedEntityData.Builder, resource-location factories, entity dimensions, and spawn API.
  NeoForge EntityJoinLevelEvent replaces the removed Forge onAddedToWorld hook,
  applying configured attributes whenever a Pale Hound joins a level.
- Step-height addition (0.4 on a vanilla 0.6 base) becomes STEP_HEIGHT = 1.0.
  The original depth-strider bonus divided by three becomes WATER_MOVEMENT_EFFICIENCY.
  This conversion and all movement behavior still need in-game comparison.
- Three navigation/movement mixins retained. The old local-variable depth-strider injection
  is replaced by the attribute above; the travel cancellation for staring remains.
- Resource pack format 34 and singular `loot_table` directory for 1.21.1.
  The original loot table uses an unregistered `pale_hound:pale_hound` context type;
  it now uses `minecraft:entity`, retaining all pools, rolls, conditions and probabilities.
  This repairs malformed input and may enable loot that failed to load in the old JAR;
  exact old-runtime loot behavior has not been measured.
- Biome convention reference `forge:is_spooky` becomes `c:is_spooky`.

## Verified

- Java 21 JDK download matches the vendor SHA-256.
- Gradle 8.14.3 wrapper downloads with its official SHA-256 pinned.
- All 34 Java source files parse with JavaParser configured for Java 21.
- Gradle `processResources` succeeds.
- Seven Python parity/resource tests pass, including byte equality of unchanged original assets,
  the original configuration static initialization/category sequence, and original AI bodies
  after normalization of the three necessary API renames.

## Manual comparison checklist

1. Confirm the built JAR in your normal modpack with NeoForge 21.1.262 and GeckoLib 4.9.3.
2. Test the distributable in a normal dedicated multiplayer server; the GameTest
   server already validates server-side loading and mixin behavior.
3. Launch a client and confirm the spawn egg, model scale, head rotation, eyes, and all
   nine animation selections; test sound delivery with two players.
4. Compare original and port spawning by light, height, dimension, biome, distance,
   path checks, maximum entity count, timers, cooldowns and surface multiplier.
5. Compare stroll, proximity/direct-gaze acquisition, stare transitions, chase attacks,
   flee, disappearance, climbing, crawling, crouching, swimming, block breaking and
   mob-griefing rules; verify riding, invisibility, damage immunity, teleport and loot.
6. Check config reload, `/pale_hound` commands and singleplayer/dedicated multiplayer.

## Runtime validation results

- `build runGameTestServer`: successful. All **4 required GameTests passed**.
  Entity creation, config attributes, step/water attributes, standing/crawling dimensions,
  fall/suffocation immunity, stare movement cancellation, pathfinding crawling-state
  restoration, and positional sound payload round-trip were exercised in Minecraft.
- `build runClient -PclientAssetSmoke`: successful with Mesa software OpenGL 4.5
  on a virtual display. The client reached the title screen, loaded the model and
  **all 9 used animations**, and exited normally with the test success marker.
- The first client run exposed malformed original `number+easeOutSine` expressions
  in three calm-idle upper-left-leg keyframes. They now use GeckoLib's explicit
  `vector`/`easing` format with unchanged numeric values and timestamps. Original
  subtitle strings are added as translation keys with identical display text.
  These two necessary asset migrations have separate structural regression checks.
- The distributable was checked to contain mod classes/metadata and to exclude
  development test classes, their template, and the external GeckoLib dependency.
- Online account/profile checks logged DNS failures in this offline development
  session; they did not prevent either test run. The machine has no physical audio
  device, so audible playback and authenticated multiplayer were not verified.

The checklist above remains useful for exhaustive original-versus-port comparison;
the automated tests do not establish complete visual or multiplayer equivalence.

## Follow-up: more crawling

Crawling now also replaces the crouch posture in low tunnels and activates during
aggressive CHASE behavior within 12 blocks of a living target. The client animation
uses the synchronized crawling state so the new behavior shows the crawl animation.
Existing crawl dimensions and speed are retained. Two additional server GameTests
verify tunnel entry/exit, close/distant chase transitions, and no proximity-only crawl
while staring. All 6 required GameTests and all 7 parity/resource checks pass.
