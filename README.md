# The Pale Hound — NeoForge 1.21.1

Target: Minecraft **1.21.1**, NeoForge **21.1.262**, Java **21**, GeckoLib **4.9.3**.

This is a source migration from the supplied `Pale_Hound-1.20.1-1.10.0.jar`.
The port builds against the target loader and supplied GeckoLib JAR. See
[validation status](docs/PORT_STATUS.md) for checks and remaining manual comparisons.

## Sensory hunting (1.11.2)

The Hound now investigates footsteps, sprinting, doors, player injuries, landings,
eating/drinking, audible equipment changes and other disturbances;
searches last-known positions after losing sight; and disengages after an unsuccessful
search. Quiet movement and cover can work. Repeated disturbances increase interest.
Held torches/lanterns and bright surroundings increase sight range from 64 to 96
blocks. Solid cover still blocks detection.

He starts without learned hazard fear. Actual injuries teach avoidance of lava, heat,
explosives, damaging terrain and web traps. Repeated experience can make him cautious
about the corresponding held items. Learning carries into future encounters in the
same world by default; individual-only learning is configurable. Original damage,
health, speed and target eligibility remain unchanged.

`/pale_hound inspect` shows the nearest loaded Hound's state, suspicion, evidence and
learned hazards. See [the hunting guide](docs/HUNTING.md) for behavior, hearing ranges,
configuration and persistence. The existing close-range surface attack and escape
rules still apply; a recognized danger can take priority over approaching its holder.

During quiet roaming or distant surface stalking, he occasionally approaches a
visible dropped item and lowers into his lowest pose to examine it briefly. Sounds,
nearby players, danger, lost visibility and stalled movement interrupt curiosity.
Items are not taken or consumed. Bats and other non-player creatures cannot supply
hearing distractions. Clear-ground path smoothing now applies to ordinary movement
as well as escape routes; failed search paths no longer trigger arrival pauses.

## Story difficulty

Select **Story** in the new-world difficulty selector or the in-game Options difficulty
selector. Operators can use `/difficulty story` on any server; `/difficulty` reports
Story while active. Choose Peaceful, Easy, Normal or Hard to leave it. Story persists
with the world and uses Easy combat rules. Hardcore worlds do not support Story.
Dedicated servers can enable it using the command or `/gamerule paleHoundStoryMode true`;
vanilla `server.properties` difficulty values remain unchanged.

Only allowlisted hostile types can spawn in Story. The default allows just
`pale_hound:pale_hound`. Passive mobs, villagers, wolves and iron golems are unaffected.
Natural spawning, spawners, eggs, summons and mobs loaded from saved chunks all obey
the filter. Enabling Story removes existing disallowed hostiles without drops.
Pale Hound encounter commands obey the allowlist too. Its normal biome, light and
spawn-timer rules continue to apply to natural encounters.

Edit `config/pale_hound-server.toml` in the game/server instance (or the world's
`serverconfig` override if you use one):

```toml
[Behaviour]
story_hostile_allowlist = ["pale_hound:pale_hound"]
```

Add exact entity IDs, for example `"minecraft:zombie"` or an optional mod's ID, to
permit more hostiles. An empty list blocks every hostile, including the Hound.
Reloading the server config applies the list and removes newly disallowed hostiles.
Hostiles are recognized by Minecraft's Monster category or Enemy interface; unusual
modded mobs that declare themselves passive need compatibility support. This is a
saved mode over vanilla Easy, so other mods reading vanilla difficulty see Easy.
Install this update on both client and server; the network protocol changed.

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

Build output is `build/libs/Pale_Hound-1.11.2-neoforge-1.21.1.jar`.
In this managed cloud, `tools/cloud-gradle.sh` selects the retained JDK and proxy trust settings.
The supplied NeoForge installer identifies the target loader; it is not a mod dependency
or a replacement for the NeoForge development toolchain used by Gradle.

## Checks

```sh
python3 -m unittest discover -s tests -v
./gradlew processResources
```

The nine Python checks compare assets, original AI goal bodies, configuration defaults
and category order against a baseline recovered from the original JAR; check the data
resource migration, sound/animation references, and the supplied dependency checksum.
They do not validate Java type compatibility, mixin application, rendering, spawning,
networking, or game behavior. Use `runGameTestServer` for sixty-nine in-game server smoke tests.
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
He watches the player at a distance, turning his body and head toward them while
holding position up to 48 blocks away. Beyond that range he follows toward a
40-block standoff. Inside 16 blocks he slowly backs away while facing the player,
and stops backing off once 20 blocks away.

A visible eligible player within **12 blocks** ends passive stalking and triggers a
committed chase and melee attack. This takes priority over a pending gaze reaction,
and does not require the player to look at him. Creative/Spectator protections remain
for ordinary encounters; the explicit command-recipient exception still applies.
A full running retreat remains non-attacking.
After 180–270 seconds by default he runs away. He runs for at least three seconds and
must stay out of sight of every nearby player for one second before despawning.
Looking away alone does not remove him. Losing the player or leaving the surface
starts the retreat early. His normal lifetime countdown pauses
during this sequence so it can finish; removal is controlled by the server.

## Spawn pacing

`[Timers.Spawn].spawn_timer_multiplier` defaults to `0.8`, making spawn waits and
occasional long cooldowns 20% shorter. The original base timer settings are retained;
existing configs automatically receive the new setting when missing. With the original
base values, the ordinary window is approximately 10m40s–16m and the long cooldown
is 18m40s. These are the cave/base waits; the surface multipliers apply additionally.
Eligible player/location checks and the maximum entity count still apply.
Use `1.0` to restore the original spawn pacing. Noise timers are unaffected.

## Being spotted and attack targets

Distant eye contact no longer makes a surface stalker flee. After the eight-second
arrival grace, direct eye contact inside **14 blocks** begins a **one-second reaction
pause**. The reaction can continue up to 16 blocks away, giving the player time to
cross into the 12-block attack radius. Crossing that boundary wins over retreat.
Losing eye contact or moving beyond 16 blocks cancels the pending reaction. A player
who holds that close gaze without entering attack range causes him to run away.
Once he escapes out of sight and despawns, that dimension's next spawn wait resets to
25% of the configured wait (including the spawn-timer multiplier). This bonus applies
once; later successful spawns use normal timing. Normal spawn eligibility rules remain.

Attack targets are restricted to players, sheep, villagers, iron golems, wolves (wild and tamed),
and Fantasy Feudalism: Mercenaries entities `villagerscontract:defender`,
`caravan_villager`, `caravan_trader`, and `caravan_mule`. Horses bearing that mod's
`FFMCaravan` UUID are also recognized as caravan members. Ordinary horses,
wandering traders and other mobs are excluded, including from retaliation. Passive stalking ends when a visible player crowds inside 12 blocks. Running
retreat still suppresses attacks against every target.

The Mercenaries mod is optional, and its JAR is not bundled. For compatibility testing
with your own copy, use `./gradlew runGameTestServer -PmercenariesJar=/absolute/path/to/mod.jar`.

Crawling now uses separate visual bounds and a 128-block base draw distance, so its
small tunnel collision box no longer makes the tall model vanish around 32 blocks.
The video render-distance setting and loaded/tracked chunks still limit visibility.

## Longer presence and improved navigation

`[Timers.Leave].presence_time_multiplier` defaults to `3.0`. With the original base
settings this gives a 15-minute normal lifetime, 90-second chase leave timer, and
180–270-second stalking duration with the additional default surface-stalk multiplier. Eye-contact retreats still take priority. Active stalking/fleeing pauses the normal
lifetime and vanilla random distance despawn; escape removal requires loss of visibility.
Normal chunk loading rules still apply.
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
The outline is private and requires the client to receive the model. A private HUD
also shows direction, distance and live coordinates for up to three nearest selected
Hounds, including those outside view. Positions refresh twice per second without
extending the requested duration. The outline resumes automatically on returning to
tracking range. Actual removal clears the display and reports that no selected Hound
is loaded. Highlight never forces chunks or reveals unloaded Hounds. Other
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
movement states, including chasing and retreating. Backward stalking uses a slower 0.22 multiplier;
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

## Supplied playback sounds (1.10.4)

All 13 WAV clips from `Pale hound Sounds.zip` are included as streaming, 48 kHz stereo
Ogg Vorbis. Their timing and mix are retained; playback uses occasional clips rather
than continuous loops. [Audio provenance and file mapping](docs/USER_AUDIO.json) records
the supplied filenames, hashes, durations and converted asset paths.

| Supplied names | Playback trigger |
| --- | --- |
| Crying #1–5 | Random ambient playback through the existing noise timer and a nearby calm Hound's ambient intervals. |
| Monster Sees Player #1–3 | When he acquires a player underground. |
| Monster Sees Player Aboveground #1–2 | When he acquires a player on the surface, including the start of passive surface stalking. |
| Stalking Underground #1–3 | During his existing non-attacking underground stare/approach behavior. |

Spotting cues target the affected player and are throttled to prevent repeats for the
same player within 45 seconds. Underground stalking cues begin 18–23 seconds after
detection and repeat every 20–35 seconds while that behavior continues. Combat, flee,
target loss and entity removal end the stalking sequence; an active clip finishes naturally. This adds audio to the existing
underground behavior; it does not introduce a new passive cave encounter.

These are personal stereo playback cues. Crying uses the Ambient/Environment volume
slider; detection and stalking use Hostile Creatures. One Hound sound plays per
client at a time across supplied clips, original vocals and footsteps. Busy channels
drop new requests rather than interrupting or queueing stale sounds. Clips finish
naturally even if stalking ends or he leaves tracking range; world changes/logout
still clear playback. The audio engine determines completion rather than a server-
time deadline. Original audio assets and event definitions remain intact.

Use `./gradlew runClient -PclientAudioSmoke=DisposableWorld` for the optional integrated
sound test. It fully decodes all 13 clips through Minecraft's JOrbis decoder and checks
real packet delivery, exclusive native/custom playback, natural completion, clock jumps, loss of entity tracking and logout cleanup.
Use a disposable world: the fixture removes existing test Hounds and sets creative mode.

## Less frequent surface encounters (1.10.5)

`[Timers.Spawn].surface_spawn_timer_multiplier` defaults to `2.0`, doubling surface
spawn waits and cooldowns. Existing configs receive this new setting automatically.
With the other default settings, ordinary surface waits are about 21m20s–32m and the
long cooldown is 37m20s. Cave waits remain about 10m40s–16m, with an 18m40s cooldown.
Set the new value to `1.0` to restore the previous surface frequency.

This spawn-only setting composes with the original `[Timers].surface_timer_multiplier`
and the global spawn multiplier. It does not change noise/crying intervals, the forest
chance bonus or biome eligibility. Being spotted still reduces the next wait by 75%,
with the surface factor applying to that shortened wait too.

`/pale_hound get_timer <dimension> spawn` now displays elapsed / effective target,
including the surface factors for the timer's selected player, plus seconds remaining.
The noise query also includes its existing surface factor. The dimension still shares
one timer, and eligibility/valid spawn locations are still required after it is ready.

## Start an encounter and improved retreat (1.10.6)

OPs can use `/pale_hound encounter` to start an encounter for themselves, or
`/pale_hound encounter <player>` for one player. The console must specify a player.
The command searches loaded, collision-free ground nearby, preferring a position
behind the player. Clear nearby air is a fallback for flying players; if every
location is obstructed it reports that a safer area is needed.

This bypasses the scheduled timer, chance, daylight, biome/dimension eligibility
and natural entity cap. It starts the normal encounter AI, including the surface
stalking preference and existing cave behavior. The selected player can participate
in Creative/Spectator; other Creative/Spectator players retain their normal protection,
and Minecraft's native invulnerability is unchanged.

Command-created Hounds are tagged in saved entity data and excluded from the natural
spawn cap. Neither their creation nor their spotted retreat/despawn resets the
scheduled spawn/noise timers or applies the natural spotted-respawn bonus. The
ordinary timers continue normally, so independent timed encounters can still occur.

`[Behaviour].surface_stalk_duration_multiplier = 2.0` doubles surface stalking to
180–270 seconds with the default presence multiplier, before running out of sight.
It does not extend cave encounters. Existing gaze/target-loss
conditions can still end stalking early.

Retreat keeps a stable escape heading and follows a working route to completion,
rather than choosing random destinations every second. Routes must finish farther
from the threat and cannot loop back close to it. Alternate routes can flank walls.
The normal flee goal also uses this planner, claims movement/look control, and no
longer competes with chase/melee movement. Retreats cannot perform melee attacks.


## Heavy movement and farther encounters (1.10.8)

Command encounters prefer 48 blocks away, then 40 or 32 if the terrain requires it.
The clear-air fallback for flying players is also 32 blocks away. Natural surface
spawns keep at least 32 blocks from players (or a larger configured minimum) and
search a wider area. Cave minimums still use `spawn_distance`.

Normal animation playback runs at 75% of its previous speed, as requested. Fleeing uses movement-matched playback to keep its gait aligned. Posture
blends increase from three to twelve animation ticks, and rising out of a low crawl
requires half a second of consistent clearance. This smooths standing/crawl/low-crawl
changes without slowing physical collision adjustments in tunnels. New reverse gait
loops cover tall, medium and low crawling, retaining the connected head and wide limbs.
Movement speed remains at the existing 20% reduction; slow backoff is additional.

Escape paths skip the block-center staircase on open ground only when sampled full-
body clearance, solid ground and hazard checks allow a straight segment. Tight caves
keep their careful waypoint traversal. Working paths and the escape heading stay
stable; obstacles can still require a detour. Both surface and normal fleeing keep
running while visible, considering the exposed head as well as the small crawl eye.
Passing beyond each player's effective entity tracking range also counts as leaving
sight. Network protocol 4 requires this update on both client and server.


## Audio, distant highlight tracking and fleeing gait (1.10.9)

An active crying/stalking clip no longer cuts off when the encounter switches to
fleeing. The audio channel now includes original entity vocals, footsteps and legacy
cues as well as supplied clips, and permits one Hound sound at a time. One-shot entity
sounds detach from the model's tracking lifetime so leaving view does not cancel a
clip. New sounds are skipped while the current sound plays; they are not queued to
replay after the encounter. Server time jumps cannot terminate playback.

Highlight distinguishes a server-loaded Hound from an available client model. Its
private live location HUD remains when the model leaves view; when the Hound actually
despawns, the session clears instead of leaving a stale marker. The command still
uses the same OP/Creative restrictions and 1–300-second duration.

The slow standing/crawl posture blends remain. Running away now enters its gait
with a three-tick transition and scales standing playback from 1x to 3x with actual horizontal
movement; the longer crawl loop can scale up to 6x during a fast escape. This prevents a long, slowed blend from looking frozen while he sprints.
Network protocol 5 requires updating both client and server.


## Close-range surface attacks (1.10.10)

Surface stalking now permits close approaches: slow backoff starts at 16 blocks,
gaze-triggered running starts around 14 blocks after a one-second reaction, and a
visible player at or inside 12 blocks triggers attack first. The ordinary proximity
roll cannot randomly turn this committed attack into fleeing, and the chase can
start even while the player faces away. Walls, invisible-target configuration and
normal Creative/Spectator protections are respected. Existing stalking duration,
out-of-sight retreat removal and command timer exemption are retained.
