# Port status

The port compiles for NeoForge 21.1.262 with GeckoLib 4.9.3.
Exhaustive visual and multiplayer equivalence to the original is not claimed.

## Implemented source migration

- NeoForge mod entry point, deferred registration, attributes, server tick, damage,
  dimension, command, and creative-tab events.
- All eleven original AI goal/roll classes retained. The only source-body changes in
  those classes initially were the 1.21 step-height API, mob-griefing hook, and glass base-class names.
  Follow-ups also add an explicit chase-continuation guard and scale its initial leave timer.
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

## Follow-up: passive surface stalking and retreat

Idle surface hounds roll a 90% stalking chance immediately on their first tick,
then every 100 ticks for an eligible player within 64 blocks. Active chases and flee behavior are not interrupted. Stalking lasts
600–900 ticks, keeps a 16–24 block distance band at crawl speed, and backs away when
approached. The new goal holds movement/look control while combat targeting and all
melee attacks are suppressed. A synchronized fleeing pose replaces crawling for the
60-tick running retreat, followed by disappearance. Invalid/distant targets and leaving
the surface initiate retreat early. The normal lifetime timer pauses during this
sequence, and only the server controls timed removal.

All 7 server GameTests and 7 parity/resource checks pass. The added runtime test checks
attack-start exclusion, pathing toward a distant player without closing to melee range,
backoff pathing, waiting at the distance band, passive behavior at melee range, stalking
duration, transition to running, and eventual despawn. Original goal regression checks
explicitly allow the new chase-continuation guard while comparing the rest to the input.

## Follow-up: prefer stalking over attacking

The initial surface stalking check now precedes target acquisition on the first tick,
with a 90% acceptance chance (increased from 50%). Idle retries use the same 90%
chance every five seconds. Existing active chases and fleeing still cannot be interrupted.
A deterministic first-tick GameTest uses a roll between the old and new thresholds
with a survival player inside the proximity-attack radius; stalking wins and attacks
remain suppressed. All 8 server GameTests and 7 parity/resource checks pass.

## Follow-up: slightly more frequent spawns

Added `[Timers.Spawn].spawn_timer_multiplier` with default `0.8` and range 0.1–5.0.
It scales both ordinary spawn windows and the occasional long cooldown when a timer
is reset, without rewriting any of the original 32 option values. Existing configs
receive the new key through normal config correction. Surface timing still stacks
with this multiplier, while noise timers, spawn chance, entity cap and location rules
retain their existing behavior. Config reload recreates timers using the new pacing.

Build, all 9 server GameTests, and all 7 parity/resource checks pass. The added runtime
test covers an existing config receiving the default, custom base settings being scaled,
readiness at the shortened tick threshold, unchanged noise timing, cooldown scaling,
and restoring original pacing with multiplier 1.0. The regression baseline continues
to verify all original configuration defaults and categories, allowing only this new key.

## Follow-up: spotted retreat and restricted targets

Direct gaze by any valid player while stalking triggers the existing 60-tick running
retreat. The gaze check respects the spotting range, requires dot product > 0.99 and
line of sight, and is evaluated on the server. Once despawned, the dimension's spawn
timer starts a fresh wait at one quarter of its configured duration; the existing 0.8
spawn multiplier applies first. Noise timing is retained. The modifier does not persist
into ordinary subsequent spawn resets, and repeated ticks cannot reapply it after removal.

A centralized target policy now filters nearest-mob acquisition, target assignment,
attack eligibility/retaliation, and final melee damage. It permits players, sheep,
villagers, iron golems, wolves (wild and tamed), the supplied mod's defender and three caravan
entity types, and its UUID-marked caravan horses. It excludes ordinary horses, generic wandering traders, zombies, pillagers and other unlisted entities.
The earlier close-chase test now uses an allowed sheep instead of an excluded zombie.

Build and all 11 server GameTests pass both without the optional mod and with the
supplied Fantasy Feudalism: Mercenaries 1.18.2 JAR installed (this is its mod version;
the supplied JAR targets Minecraft 1.21.1/NeoForge 21.1.262). The compatibility run
creates all four actual registered optional entity types and verifies target eligibility.
The optional JAR is neither required nor redistributed. All 7 parity/resource checks pass.

The new runtime tests also cover looking away, occluded gaze, direct gaze, immediate
retreat, despawn, the exact one-off 75% reduction, unchanged noise timing, ordinary
subsequent timer resets, excluded targets not taking melee damage or provoking
retaliation, both tame and wild wolves being allowed, and marked versus ordinary horses.

## Follow-up: longer presence, roaming and navigation

- Added `presence_time_multiplier = 3.0` under Timers.Leave, applied to normal lifetime,
  chase leave time and stalking duration. Existing base configuration is retained and
  the new key is corrected into existing config files. Defaults become 15 minutes,
  90 seconds and 90–135 seconds respectively. Forced retreats remain 60 ticks and the
  spotted-respawn bonus is unchanged.
- A roam goal extends the original stroll eligibility rules, with configurable radius
  24 and attempt interval 40 under Behaviour (previously 10 and 120). It ranks sampled
  destinations using exploration distance and a penalty near the last eight destinations,
  and requests a completed route before selecting a waypoint.
- A Hound-specific WallClimberNavigation subclass preserves climbing, allows floating,
  increases the node-search multiplier from 4 to 6, and refreshes a route after 40 ticks
  without half a block of movement. It never teleports as part of recovery.
- Accessors clear both climbing fallback and regular recomputation destinations on stop.
  Candidate searches preserve the active destination so path planning alone cannot
  trigger phantom movement or redirect later recomputations.
- Stalking/retreat path selection evaluates up to four endpoints and can flank an obstacle.
  Ground snapping is bounded to +2/-6 blocks and loaded chunks. Unsafe/liquid endpoints
  are rejected; navigation costs discourage water/fire and prohibit lava/fire damage
  and powder-snow nodes. Original crawling, targeting and mob-griefing rules remain.

All 14 server GameTests pass, including a run with the supplied Mercenaries mod. Tests
cover the longer-but-finite lifetime, scaled stalking and unchanged forced retreat,
reachable roaming beyond the original radius, rebuilding a stale route around a new
wall, keeping stopped navigation stopped, path planning without movement, alternative
waypoints and rejecting a magma endpoint. All 7 parity/resource checks pass; original
config defaults and goal logic are compared allowing the documented added options
and the chase timer scaling. Full visual/multiplayer terrain testing remains manual.
