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

Build output is `build/libs/Pale_Hound-1.10.3-neoforge-1.21.1.jar`.
In this managed cloud, `tools/cloud-gradle.sh` selects the retained JDK and proxy trust settings.
The supplied NeoForge installer identifies the target loader; it is not a mod dependency
or a replacement for the NeoForge development toolchain used by Gradle.

## Checks

```sh
python3 -m unittest discover -s tests -v
./gradlew processResources
```

The eight Python checks compare assets, original AI goal bodies, configuration defaults
and category order against a baseline recovered from the original JAR; check the data
resource migration, sound/animation references, and the supplied dependency checksum.
They do not validate Java type compatibility, mixin application, rendering, spawning,
networking, or game behavior. Use `runGameTestServer` for twenty-seven in-game server smoke tests.
Use `./gradlew runClient -PclientAssetSmoke` to check model and animation loading
and exit automatically once the title screen is reached. On first launch, dismiss
the accessibility welcome screen. A display/OpenGL context is required. Test classes and their
structure templates are excluded from the distributable JAR. Manual multiplayer and
visual/behavior comparisons against the original remain separate from these checks.

## Provenance

The original JAR declares the MIT license and credits TheSpukuz, Llamii_Llama, Gargin,
Cadentem, and Schpalrbo. Original models, textures, sounds, and logo are preserved byte for byte.
Other animation values/timings are retained with a malformed easing expression repaired;
the crawl is intentionally replaced by a grounded gait and stationary pose;
translations retain existing entries and add the original subtitle text. Original credits are retained in mod metadata.
The source was recovered with Vineflower 1.11.1, checked against CFR 0.152, and mapped
from 1.20.1 SRG names to Mojang names before API migration. The baseline records the
original JAR SHA-256. Original author source was not supplied.

## Crawling behavior

The Hound now crawls in two-block-high tunnels and during an active chase within
12 blocks of a living target. He returns to standing when neither condition applies and his entire standing bounding box fits.
He anticipates low ceilings along his path and remains low until clear of roof edges.
The crawl animation follows the synchronized posture, with a tall, hunched four-point stance,
bent limbs, planted-foot phases and a still idle pose. The neck stays attached to the shoulders
and skull; head turns are limited while crawling. The open-ground head is about 2.15 blocks
high, with shoulders about 1.85 blocks high. Collision-shape checks choose the previous
medium pose under two-block ceilings and a compressed pose in tighter passages,
including slab ceilings, tunnel entrances and partially overlapping roof edges.
Gait playback follows movement speed; the crawl posture multiplier remains 0.35.
This is an intentional behavior change requested after the original port.

## Surface stalking

On his first tick after spawning, an eligible surface Hound has a 90% chance to
begin stalking a player within 64 blocks before normal attack targeting runs.
If he remains idle, he retries at 90% every five seconds. This does not interrupt a chase or flee.
He crawls after the player, aims to stay 16–24 blocks away, and backs off when approached.
All attacks are blocked during stalking and retreat, including at melee distance.
After 90–135 seconds by default he runs away, then despawns after three seconds. Losing the player
or leaving the surface starts the retreat early. His normal lifetime countdown pauses
during this sequence so it can finish; removal is controlled by the server.

## Spawn pacing

`[Timers.Spawn].spawn_timer_multiplier` defaults to `0.8`, making spawn waits and
occasional long cooldowns 20% shorter. The original base timer settings are retained;
existing configs automatically receive the new setting when missing. With the original
base values, the ordinary window is approximately 10m40s–16m and the long cooldown
is 18m40s. Eligible player/location checks and the maximum entity count still apply.
Use `1.0` to restore the original spawn pacing. Noise timers are unaffected.

## Being spotted and attack targets

Direct eye contact from a valid player during stalking starts the running retreat
immediately. It requires clear line of sight and respects the spotting-range setting.
After the three-second retreat/despawn, that dimension's next spawn wait is reset to
25% of the configured wait (including the spawn-timer multiplier). This bonus applies
once; later successful spawns use normal timing. Normal spawn eligibility rules remain.

Attack targets are restricted to players, sheep, villagers, iron golems, wolves (wild and tamed),
and Fantasy Feudalism: Mercenaries entities `villagerscontract:defender`,
`caravan_villager`, `caravan_trader`, and `caravan_mule`. Horses bearing that mod's
`FFMCaravan` UUID are also recognized as caravan members. Ordinary horses,
wandering traders and other mobs are excluded, including from retaliation. Stalking
and retreat still suppress attacks against every target.

The Mercenaries mod is optional, and its JAR is not bundled. For compatibility testing
with your own copy, use `./gradlew runGameTestServer -PmercenariesJar=/absolute/path/to/mod.jar`.

## Longer presence and improved navigation

`[Timers.Leave].presence_time_multiplier` defaults to `3.0`. With the original base
settings this gives a 15-minute normal lifetime, 90-second chase leave timer, and
90–135-second stalking duration. Eye-contact retreats and their three-second despawn
still take priority. Normal distance-based despawning and chunk loading rules apply.
Set the multiplier to `1.0` to restore the previous durations. It scales saved base
settings without rewriting them; ordinary lifetime and chase timers are set when a
Hound/goal is created, and stalking duration when a stalking sequence starts.

`[Behaviour].roam_radius = 24` widens idle searches from the original 10 blocks per
horizontal axis. `roam_interval = 40` lowers the original 120-tick attempt interval.
The roam goal remembers eight destinations, favors unexplored ground, and chooses
reachable destinations rather than accepting a random blocked point.

The climbing navigator refreshes a route after two seconds without movement, supports
a larger bounded node budget, and clears its destination when stopped. Evaluating a
candidate path does not itself start movement or change the active destination.
Stalking/retreat movement can try up to four alternate waypoints, including flanking
positions, and prefers completed paths on safe ground. Fire, lava, magma, powder snow
and water receive avoidance checks/costs. Waypoint destinations must lie in loaded chunks;
pathfinding does not guarantee a route through every terrain layout.

## Highlight command

OPs (permission level 2) and creative players can use `/pale_hound highlight` to outline
loaded Hounds in their current dimension for 30 seconds and report the nearest coordinates. `/pale_hound highlight 120`
sets a duration of 1–300 seconds; `/pale_hound highlight off` clears it early.
The outline is visible only to the requester and only for entities the client receives
within tracking distance. It does not load chunks or reveal unloaded Hounds. Other
Pale Hound administration commands still require OP permissions. If none are found,
the command names the searched dimension and reports loaded Hounds in other dimensions.

Idle roaming continues after vanilla's inactivity threshold. If a targetless Hound is
stalled with no active path for five seconds, he returns to roaming and requests a new
route. Stalking and active encounters retain their own movement rules.

## Elbows, movement speed and cave ledges

Front elbows flare outward in both moving and stationary crawl poses, including the
compressed tunnel variants. The front paw positions stay the same.

Movement speed is now 80% of `[Attributes].movement_speed`: the original setting
`0.7` produces a base attribute of `0.56`. This applies to existing configs and all
movement states, including chasing and retreating. Posture multipliers are unchanged;
applying attributes again does not compound the slowdown.

The fall-immune Hound can now plan descents of up to 32 blocks, rather than becoming
stranded on cave ledges above vanilla's small drop limit. Roaming favors horizontal
exploration over climbing higher, and tries nearby ground projections when random
land samples fail. Ground projections remain bounded to loaded chunks and safe
landing endpoints. Narrow turns follow waypoint centers, and a finished crawling
path no longer falls back to pushing straight toward a wall. Failed replanning clears
the stale destination when climbing is unavailable.

Version 1.10.2 raises the open-ground crouch using the original long front limbs, higher
hips and shoulders, while keeping the front elbows flared and the paws grounded.
Moving and stationary poses use the same heights. The lower tunnel poses, movement
speed reduction and cave navigation improvements remain available.

## Biomes and forest preference (1.10.3)

All biomes are eligible by default except deserts and badlands/Mesa, including wooded
and eroded badlands. The exclusions apply to both the selected player's biome and
the actual spawn candidate, so a biome-border encounter cannot place him in a desert.
Modded variants are recognized through common biome tags and biome paths containing
`desert`, `badlands` or `mesa`. Existing dimension, light, height, path and entity-cap
checks still control whether an eligible biome can produce an encounter.

Forested biomes get a 25% bonus to the per-tick spawn roll after the timer finishes: the
default 0.5% becomes 0.625% when the selected player is in a forested biome. Forests,
taigas, jungles, mangrove swamps and appropriately tagged modded forests qualify.
Timers and cooldowns use their existing settings. `["Spawn Conditions"].forest_spawn_chance_multiplier`
defaults to `1.25`; set it to `1.0` for equal chances.

Existing configs using the original default surface blacklist automatically migrate to
the four excluded vanilla biomes on load/reload. Custom lists and whitelist/datapack
modes are retained as additional restrictions. Datapacks can extend
`pale_hound:blocked_spawn_biomes` and `pale_hound:forested_spawn_biomes`.
