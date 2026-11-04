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
   twelve animation selections; test sound delivery with two players.
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

## Follow-up: private highlighting, tunnel clearance and crawl stance

- Added `/pale_hound highlight [seconds]` (default 30, range 1–300) and `highlight off`
  for OP/creative players. A bounded client payload highlights loaded current-dimension
  Hounds only for the requester. State clears on expiration, world switch or logout;
  server glowing flags remain unchanged. Existing administration commands remain OP-only.
- Standing clearance now checks the entire bounding box, including partial overlap with
  a roof edge, and anticipates low path nodes. Goal cleanup and temporary navigation
  posture restoration cannot expand the Hound into a ceiling. A real one-block tunnel
  test reproduced clipping before this fix and traverses the tunnel without clipping afterward.
- Idle roaming bypasses vanilla RandomStrollGoal's no-action-time lockout. A targetless
  Hound with no path for 100 ticks clears stale encounter state and requests another roam.
- Replaced the swimming-style crawl with a bent-limb, four-point gait, a stationary
  crawl pose, limited head turns and a compressed tunnel variation. The neck has no
  translation away from its shoulder attachment. Moving limbs have planted stance phases
  and lifted return phases; controller playback scales with horizontal velocity.
  Other original animations and model/texture assets are retained.

Validation: all 19 server GameTests pass with and without the supplied Mercenaries mod;
all eight Python checks pass. Client tests exercise
command-to-packet delivery, private glow rendering, off and expiry. The client asset
smoke loads all twelve used animations. A disposable integrated world is available for
visual inspection with `runClient -PclientCrawlPreview=WorldName`; it spawns a forced
crawl fixture alternating movement and rest, with head turns. Use a disposable world,
since the fixture moves the spectator camera and creates entities. The animation was
inspected in Minecraft; broader terrain/modpack and multiplayer testing remains manual.

## Follow-up: flared elbows, 20% slowdown and cave navigation

- Re-solved both front limb chains with outward elbow bends while preserving paw
  positions/orientations, shoulder attachments and the connected head. All four crawl
  variants share the new limb poses.
- The base movement attribute is 80% of the configured value, including on world join
  with existing configs. The default effective value is 0.56. Reapplication sets the
  same value instead of stacking the reduction; posture speed multipliers are retained.
- A high-ledge descent test reproduced the stall: fall-damage immunity alone did not
  raise the pathfinder's allowed drop. The Hound now permits drops up to 32 blocks.
  Ground snapping uses the same downward bound. Roaming removes the reward for gaining
  height and tries nearby projected ground endpoints if random land samples fail.
- Navigation reaches waypoint centers before turning in narrow passages. A completed
  crawling path cannot use the spider navigator's straight-line climbing fallback, and
  an unreachable replan clears the stale destination when climbing is unavailable.

All 22 required server GameTests and eight resource checks pass. New coverage includes
physical descent from a five-block cave ledge, traversal of a one-block-high L-shaped
passage without collision, and a completed crawl path adjacent to a wall. Attribute
checks verify the 20% reduction and idempotent reapplication. The virtual-display client
preview checks the flared elbows in the actual renderer. Terrain-specific modpack
behavior remains subject to in-game testing.

Highlight follow-up: lookup now uses the registered entity type in the requesting
player's actual dimension, including invisible and dying-but-not-removed entities.
A redirected command source cannot silently search a different dimension. Feedback
gives the nearest coordinates, or the searched dimension and counts in other loaded
dimensions when none are found locally. The integrated client test now creates its
Hound through the actual natural encounter spawn helper and verifies private glow,
off and expiry. This passes in the Overworld; the reported visible-Hound/zero-count
case was not reproduced in this environment. The user subsequently confirmed that
highlighting found the Hound; the outline remains subject to client tracking range.
These changes shipped in 1.10.1.

## Follow-up: taller crouch (1.10.2)

The open-ground four-point crouch raises the hips and shoulders, restores the front
limbs to their original lengths, and re-solves bent limbs with outward front elbows.
Paw positions/orientations are preserved through the moving gait; the head remains
connected and projects forward. The neutral skull reaches about 2.15 blocks, with
shoulders about 1.85 blocks above the ground. The former regular crawl is retained as
a medium tunnel pose, and its compressed variant remains the lowest pose.

Collision-shape checks over the body's full width and forward-projecting head select
tall, medium or low moving and idle poses from the available headroom. This also handles slabs and partial
roof-edge overlaps. The existing navigation clearance and speed reduction are retained.
All 23 server GameTests and eight resource checks pass; added coverage checks all
three pose selections and the slab/roof-edge transitions. All fourteen used animations
load through GeckoLib, and the tall pose is inspected from player eye height in the
disposable integrated client world.

## Follow-up: broad biome eligibility and forest bonus (1.10.3)

- The default surface blacklist now contains desert, badlands, wooded badlands and
  eroded badlands. The old untouched default blacklist migrates on config load/reload
  and is saved; custom lists, whitelists and datapack modes remain additional controls.
- A shared biome policy excludes desert and badlands/Mesa families using datapack and
  common tags plus a name fallback for untagged modded variants. It runs during player
  eligibility checks and at the actual candidate location before entity creation,
  covering underground biomes and biome-border candidates too.
- Added `forest_spawn_chance_multiplier = 1.25` in Spawn Conditions. Eligible forested
  player biomes receive a 25% higher ready-to-spawn roll; probability is capped at 1.
  Vanilla/common forest, taiga, jungle and tree tags, plus mangrove swamps, identify
  forested biomes. Spawn/noise timers, cooldowns and remaining eligibility checks are retained.
- Biome tags have matching data-generation definitions.

Build and all 27 server GameTests pass. New tests cover every vanilla biome, forest
bonuses, wooded-badlands exclusion, synthetic modded tags and names, one-time default
config migration, and retaining custom settings. A native spawn-helper integration
test rejects a desert candidate and successfully spawns at the same position after
switching its biome to plains under identical nighttime conditions. The runtime log
and saved TOML confirm the original default list migrated to the four exclusions.
All eight resource/config regression checks pass, allowing the intentional blacklist
change and new forest setting.

## Follow-up: supplied ambient and encounter audio (1.10.4)

The 13 supplied WAVs are converted to streaming 48 kHz stereo Ogg Vorbis without
changing their timing or applying gain. `USER_AUDIO.json` records input hashes,
durations, filenames and output paths. Four random-selection pools cover five crying
clips, three underground stalking clips, three general player-detection clips and
two aboveground detection clips. Original event definitions/assets remain present;
new subtitle translations identify the four pools.

Crying replaces the dimension noise playback and calm entity ambient playback. The
existing noise timing and only-target option are preserved. An encounter audio
controller plays personal detection cues on player acquisition/surface-stalk start,
with a 45-second repeat guard for the same player. The existing passive underground
stare/approach state plays its stalking pool after 18–23 seconds, then at 20–35-second
intervals. The controller does not choose targets or change aggression. Combat,
fleeing, invalid/lost targets, silent entities and removal stop stalking playback.

A fixed-size client payload carries cue kind and source UUID. The client keeps one
supplied playback channel: detection can interrupt lower priorities, while equal or
lower priority requests are dropped. Stop messages match the source and stalking
kind. Crying uses Ambient and other cues use Hostile sliders; playback is listener-
relative and preserves stereo. State clears on world switch/logout, completion or
the longest clip's 15-second safety deadline. Original vocal/footstep sounds can still
play alongside these personal cues.

All 29 server GameTests and nine Python resource checks pass. Added coverage verifies
cue serialization, one-time detection, surface/cave pool selection, delayed/throttled
underground playback, no combat-state changes, and stopping on combat/flee. The
integrated client smoke fully decodes all 13 complete streams using Minecraft's
JOrbis decoder and checks real payload delivery, sound-engine playback, priority,
source-scoped stopping and logout cleanup. OpenAL runs on a virtual null output, so
physical listening/speaker balance remains for user testing.

## Follow-up: reduce surface frequency (1.10.5)

Added `surface_spawn_timer_multiplier = 2.0` under Timers.Spawn. The additional factor
applies only when the timer's selected player is on the surface and composes with
the original common surface multiplier. It doubles ordinary surface waits and long
cooldowns without changing cave pacing, noise/crying timing, forest bonuses or biome
rules. The new key is corrected into existing saved configs normally. The spotted
quarter-wait reduction retains the surface factor.

Timer readiness and the command use shared effective-target methods, so `get_timer`
now reports the real adjusted threshold and remaining seconds rather than the base
threshold. Raw timer access remains available for existing internal callers.

All 30 server GameTests and nine resource/config checks pass. New coverage verifies
the new default in an existing config, doubled surface readiness boundaries, unchanged
cave/noise timing, composition with the original multiplier, quarter-wait scaling,
and restoring previous surface pacing with multiplier 1.0.

## Follow-up: forced encounters, longer stalking and directed retreat (1.10.6)

- Added OP-only `/pale_hound encounter [player]`, including console use with an explicit
  player. It finds a nearby clear spawn point, prefers the player's rear, bypasses
  normal eligibility/cap/chance/timer gates, and finalizes the mob with the triggered
  spawn reason. The selected Creative/Spectator player is an explicit per-Hound target
  exception; global natural-target policy and native player invulnerability remain.
- A persistent command-recipient UUID marks timer-exempt encounters. Natural spawn
  counting excludes them, and the spotted-retreat reset is suppressed for them.
  Creation and removal do not reset spawn/noise timers.
- Added `surface_stalk_duration_multiplier = 2.0` under Behaviour, composing with the
  presence multiplier to give 180–270 seconds by default. Forced retreats stay 60 ticks.
- Added a stable retreat planner with safe completed routes, outward endpoint progress
  and a limit on returning close to the threat. The surface goal keeps an active
  escape route instead of rerolling random headings every 20 ticks. Normal fleeing
  uses the same planner and movement/look flags, resets its state when restarted,
  and releases movement cleanly when stopped.
- Moved the vanilla melee fallback from target selection to the movement goal selector
  and restricted it to chase. Chase continuation yields to flee; final attack guards
  also reject flee states. Running speed now stays active during normal fleeing.

All 34 server GameTests and nine resource checks pass. The command test disables
natural chance/caps/surface spawning/dimension eligibility and invokes the real
dispatcher for a Creative operator. It checks permission denial, safe placement, the
player argument, AI startup, NBT timer-exemption persistence, and unchanged timers
after command creation and spotted retreat/despawn. Physical tests verify surface
and normal fleeing gain distance on stable headings without melee interference;
a wall test verifies flanking without returning toward the threat. Existing lifecycle
coverage verifies the longer stalking bounds and finite three-second retreat.
The intentionally rewritten original flee body is now covered by runtime checks
rather than its old byte-normalized source hash.


## Follow-up: Story difficulty and superflat visibility (1.10.7)

Story is selectable in both world creation and the singleplayer Options difficulty
cycle, and via OP-only `/difficulty story` on integrated or dedicated servers. Its
persistent `paleHoundStoryMode` game rule backs the mode; combat uses vanilla Easy,
without modifying the four serialized vanilla difficulty IDs. Standard difficulty
commands and accepted vanilla difficulty packets leave Story, including selecting
Easy when the underlying ID is already Easy. Locked GUI changes are rejected and
Hardcore does not support Story. New-world selection saves the rule and Easy base;
startup restores Easy even if a dedicated server's properties specify another base.

The server config adds `Behaviour.story_hostile_allowlist`, defaulting to only
`pale_hound:pale_hound`. Exact optional mod IDs are supported without dependencies,
and an empty list permits no hostiles. Placement checks reject disallowed Monster
types early; a server entity-join guard also covers Enemy implementations, spawners,
eggs, summons, conversions and NBT-loaded mobs. Enabling Story and config reload prune
loaded disallowed hostiles without drops. Passive mobs and standard difficulties
are unaffected. The native spawn helper now respects a rejected entity join instead
of reporting a successful spawn and resetting timers for a mob that never entered
the world. Private encounter commands already checked join success.

Client/server mode state and selector requests use authenticated NeoForge payloads.
GUI requests require OP2 or the integrated owner, unlocked difficulty and a non-
Hardcore world. The protocol is now version 3; update both ends. Client state clears
on logout. A custom choice cycle keeps vanilla wire/save codecs intact; other mods
reading vanilla Difficulty still observe Easy, and modded hostile types must declare
the Monster category or Enemy interface to participate in the filter.

Crawling previously reduced vanilla render distance to about 32 blocks because its
physical box is only 0.5 blocks wide/tall. Rendering now uses a 128-block base range
(with vanilla view-scale and entity-scale adjustments) and a separate visual culling
box covering the tall model, while preserving the small tunnel collision dimensions.
The new visibility regression failed at 48 blocks before the fix and passes after it.
A disposable superflat client check verifies native and command placement on pristine
negative-Y ground; sounds alone do not imply below-ground spawning. Chunk tracking and
video render distance remain separate limits.

All 37 dedicated-server GameTests and nine Python resource/config checks pass. Story
coverage exercises default and custom/empty allowlists, passive mobs, saved-entity
joins, placement checks for natural/spawner/egg/command reasons, loaded-hostile cleanup,
NBT game-rule persistence, protocol round trips, command permissions, locked changes,
and transitions to Easy and Hard. The actual client menus passed automated cycling,
server packet activation/state synchronization, leaving Story for Easy, new-world
Story selection and Hardcore/logout checks. Development smoke tests and structures
are excluded from the distributable.


## Follow-up: heavy stalking, backward crawl and out-of-sight escapes (1.10.8)

Command encounters now prefer 48/40/32-block ground locations, with a 32-block air
fallback. Surface natural spawning widens its search to 40 blocks per horizontal
axis and enforces at least 32 blocks from the nearest player; larger configured
minimums still win and cave minimums remain configurable as before. Commands preserve
their timer exemption and other bypasses, subject to the Story hostile allowlist.

Above-ground stalking holds position and faces the player within a 28–48-block watch
band. It follows toward a 40-block ring when farther away. A close approach inside
28 blocks starts slow backward crawling until 36 blocks away, without changing to
full fleeing or attacking. A dedicated movement control follows an outward path
with reverse/lateral input while the body stays facing the player. Backoff uses 0.22
speed versus 0.35 following and rejects routes requiring a drop of more than one
block per node. Three reverse-loop assets retain the tall/tunnel/low rig poses and
reverse the planted-limb cycle, rather than playing a forward gait during retreat.

Arrival gets 160 ticks of gaze grace. Direct gaze outside 36 blocks still triggers
escape after that; close approaches refresh 60 ticks of grace and use backoff.
The existing shortened next-spawn wait still applies once after a naturally spawned,
spotted Hound actually disappears; command encounters do not touch the timers.

Surface retreat no longer counts down to removal at 60 ticks. Both it and ordinary
fleeing require sustained loss of visibility from all nearby players for 20 ticks,
with a minimum run duration. Looking away does not count. Visibility includes the
exposed head, and uses the server-scaled entity tracking radius clamped by each
player's requested/server view distance, with a small departure margin. Vanilla
random despawn and the ordinary lifetime cannot interrupt active stalking/fleeing.
Chunk loading rules still apply. Open-ground routes skip intermediate block centers
only across sampled full-body clearance, safe solid ground and no hazards; cave
corner traversal stays precise. This removes the alternating cardinal headings on
straight diagonal escapes while preserving obstacle avoidance and stable paths.

Animation playback is multiplied by 0.75 following the user's choice. The controller
now blends for 12 ticks instead of three, and pose expansion waits for ten ticks of
stable headroom. Physical dimensions still adjust immediately for collision safety.
The backing state is synchronized; network protocol 4 requires updating both ends.

All 42 server GameTests and ten resource/config checks pass. New coverage physically
checks reverse movement/facing and diagonal heading stability, arrival grace, watching,
visible-versus-occluded removal including the tall head, and wall/hole rejection for
straight shortcuts. Existing tests now cover farther command placement, unchanged
timers, extended retreat completion and stable headroom expansion. A larger 128-block
arena keeps the new distances inside GameTest boundary barriers, and movement-only
cave fixtures run separately from surface-player fixtures. Client checks compile the
three new reverse assets with GeckoLib, exercise the actual animation-state handler
for all poses and idle stopping, and verify the 25% speed and 12-tick blend settings.
Development fixtures and structure NBT files are excluded from the release JAR.


## Follow-up: uninterrupted exclusive audio, live highlights and escape gait (1.10.9)

The supplied channel previously stopped a stalking clip immediately upon STOP_STALKING,
and imposed a 300-world-tick lifetime. State changes now let active clips complete;
completion follows the audio engine, with a monotonic timeout only for playback that
never starts. Server clock jumps therefore cannot truncate streaming clips.

A client PlaySoundEvent guard includes every pale_hound-namespaced sound, including
vanilla entity sound packets, legacy cave cues, original vocals and footsteps. Only
one Hound sound is allowed per listener; busy requests are dropped, with no delayed
queue or priority interruption. One-shot EntityBoundSoundInstances are detached from
entity ticking while preserving sample resolution, volume, pitch, category and position,
so losing the model does not stop the clip. World changes/logout still clear playback.

Highlight sessions retain selected UUIDs on the server, send private positions twice
per second, and respect the original fixed expiry, dimension and OP/Creative access.
No chunks are loaded. The HUD lists direction, distance, coordinates and whether the
client has the model, for the three nearest selected Hounds. Private glowing remains
for tracked models and resumes upon re-tracking. Actual entity removal or unloading
ends the selection and clears stale locations; losing permission, logout, dimension
change and server shutdown also clear sessions. Payload location counts and finite
coordinates are validated; the protocol is now version 5.

Fleeing uses a three-tick entry transition and horizontal-speed-driven 1x–3x running playback (up to 6x for the longer crawl loop),
rather than the 0.75 normal multiplier. It also wins over stale aggressive flags.
Standing/crawl blends remain twelve ticks, with the heavy normal playback preserved.

Validation includes all 42 dedicated-server GameTests and ten resource/config checks.
Highlight serialization now includes precise signed positions. Integrated client
checks cover exclusive native/custom playback, all 13 full Vorbis decodes, clock jumps,
completion after a stop message, and survival of one-shot audio after entity removal.
The highlight client check moves a real server Hound outside tracking range, verifies
live coordinates without a model, returns it to restore private glow, then checks off,
fixed expiry and actual removal. Animation checks verify running-speed response and
short flee entry while retaining the slower posture blends. OpenAL uses a virtual
null output; physical listening remains for user testing.


## Follow-up: close approaches can provoke surface attacks (1.10.10)

A non-retreating surface stalker checks for a visible eligible player at or inside
12 blocks before any gaze reaction or passive-state cleanup. It atomically ends
stalking, clears backoff/flee flags and routes, selects CHASE and commits to that
player. A target UUID permits the chase to start without player eye contact and
keeps ordinary target selection from rerolling this commitment into random fleeing.
Changing/clearing the target clears that commitment. Existing chase leave timing,
attack reach/damage, allowed target policy and full-retreat attack suppression remain.
Ordinary Creative/Spectator players and players behind walls cannot trigger it;
explicit command-recipient targeting remains supported.

Slow backoff now starts inside 16 blocks and ends at 20, replacing 28/36. Long-range
eye contact no longer triggers surface fleeing. After the existing arrival grace,
a direct gaze inside 14 blocks begins a 20-tick reaction that continues only while
gaze is maintained within 16 blocks. A close approach into 12 blocks interrupts
that reaction in favor of attacking. Thus a walking/rushing player has an actual
chance to provoke combat before the Hound commits to running. Timer expiry and
other encounter-ending conditions still start the normal out-of-sight retreat.

All 45 dedicated-server GameTests and ten resource/config checks pass. New tests
cover the exact 12-block boundary, attack priority during the reaction pause, chase
startup against a player looking away, actual physical pursuit and melee damage,
protection/line-of-sight checks and a nearby intruder taking priority over the
watched player. Existing tests verify distant observation, the one-second reaction,
backward movement, straight escape, despawn/quarter-wait behavior and command timer
exemption under the new distances. The physical fake player explicitly disables its
permanent test-only spawn protection. No wire or asset formats changed; protocol 5
and the previously verified audio/highlight/animation behavior are retained.
