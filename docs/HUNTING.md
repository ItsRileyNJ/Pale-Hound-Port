# Hunting behavior — 1.11.1

The Hound now follows evidence, loses targets, checks remembered locations and
winds down after unsuccessful searches. It retains the existing model, movement
speed, audio channel, target whitelist, Story difficulty and spawn pacing.

## What it notices

Normal discovery uses a forward vision cone and a 64-block maximum range. A player
holding a torch or lantern, standing in block light of at least eight, or standing
in bright daylight can be seen up to 96 blocks away. Solid cover still blocks sight.
Both hands count. Other luminous blocks are included in the
`pale_hound:illuminating_items` item tag, which datapacks can extend.

Quiet crouching reduces initial discovery distance in darkness. It does not erase
an established clear view. Very close visible contact remains detectable even
behind the Hound, preserving the 12-block close-approach attack rule.

It hears actual Minecraft game events. These are approximate starting ranges before
cover and the configured hearing multiplier:

| Disturbance | Open-air range |
| --- | ---: |
| Walking | 10 blocks |
| Sprinting | 28 blocks |
| Careful sneaking footsteps | Silent |
| Landing or splashing | 24 blocks |
| Doors, containers, block placement/breaking | 24 blocks |
| Firing a projectile | 40 blocks |
| Projectile impact | 24 blocks |
| Swimming | 16 blocks |
| Player damage | 24 blocks |
| Eating or drinking | 12 blocks |
| Equipping or removing armor | 16 blocks |

Injuries and fall landings attract investigation. Eating and drinking are audible
during chewing/sipping, including interrupted meals, as well as on completion.
Equipment changes use actual equip/unequip events; silent hotbar changes do not
make noise. Crouching does not silence these sounds. Falling through the air does
not reveal the player before a landing or injury makes a sound.
| Explosion | 64 blocks |

Cover halves hearing range and reduces confidence. A heard event supplies a stored
location/area, never the hidden player's continuously updated coordinates.
Unowned explosions and projectile impacts can also distract him. New sounds from
Hounds themselves do not feed back into the player-hearing system.

## Escalation and uncertainty

| State shown by `/pale_hound inspect` | Behavior |
| --- | --- |
| `ROAMING` | Listen, patrol, and notice visible players. |
| `INVESTIGATING` | Creep toward a heard area without a combat lock. |
| `STALKING` | Observe/follow a visible surface player; close crowding can trigger combat. |
| `PURSUING` | Chase a confirmed target. |
| `SEARCHING` | Visit the last known location and reachable nearby corners/exits; pause and look around. |
| `RECOVERING` | Roam quietly after an unsuccessful search or disengagement. |
| `AVOIDING_HAZARD` | Withdraw from a danger recognized through experience. |

Losing sight ends live player targeting immediately. A pursuit searches the last
observed position and travel direction; a new noise can redirect that search.
Finding a player again requires real visibility. Repeated disturbances around the
same location increase suspicion and search persistence. Confirmed contact after
high suspicion or a lost chase can resume pursuit without requiring eye contact.

By default a search lasts 45 seconds. Repeated fresh evidence can extend a search
up to twice that budget. Evidence also has a 45-second maximum unrefreshed age.
An unsuccessful search leads to 30 seconds of quiet roaming; low-level disturbances
are ignored during that interval, while loud new evidence or very close visible
contact can interrupt it. Hiding behind cover and staying quiet can therefore work.

Searches use reachable ground waypoints and retain a bounded recent-location
history. Search path effort defaults to 10 times vanilla's node budget, versus the
previous 6-times general navigation setting. No chunks are forced. A failed route
tries alternatives and eventually gives up instead of granting new location knowledge.
Legacy stare/chase exits now disengage rather than make him vanish because the
player looks away. Environmental rescue cannot visibly teleport him or use a hidden
player's live position. Existing out-of-sight escape removal remains.

## Learning dangers

At first, he has no learned fear of lava, fire, webs or explosives. Actual health
damage creates experience, bounded to three levels per hazard:

- Lava damage teaches lava avoidance.
- Fire, burning and hot-floor damage teach heat avoidance.
- Explosion damage teaches caution around primed TNT.
- Damage while caught in a web associates the trap with danger; webs themselves
  do not deal vanilla damage.
- Cactus/berry injuries and freezing teach those corresponding terrain risks.

Learning increases route costs and can cause a temporary withdrawal from nearby
recognized hazards. It never makes walls or unsupported air walkable. Explosive
recognition requires sight or close audible proximity. With repeated experience,
he also becomes cautious about a visible player holding the corresponding lava
bucket, web, TNT or ignition tool. Torches remain a visibility cue rather than an
automatic fear item. Learning changes decisions, not damage immunity or health.

World learning is enabled by default, so later encounters inherit prior experience.
The save stores it in `data/pale_hound_hazard_memory.dat` in the Overworld. Each
entity also stores its own experience in NBT. Set `share_hazard_learning = false`
for individual-only learning; neither mode stores experience outside the world save.

## Configuration and inspection

In `config/pale_hound-server.toml` (or an existing world override):

```toml
[Hunting]
sensory_hunting = true
hearing_multiplier = 1.0
vision_range = 64
lit_vision_range = 96
search_seconds = 45
memory_seconds = 45
recovery_seconds = 30
path_search_effort = 10.0
learn_hazards = true
share_hazard_learning = true
```

`path_search_effort` can be raised to 32 on a server with available CPU headroom.
More effort permits deeper alternative-route searches; it does not increase sight
or reveal hidden players. Longer search times increase pressure. Longer recovery
intervals give more breathing room. Master `sensory_hunting = false` restores the
legacy behavior; `learn_hazards = false` disables the experience layer separately.

OPs and Creative players can use `/pale_hound inspect` for the nearest loaded
Hound's state, suspicion, evidence snapshots and effective learned hazards.
`/pale_hound encounter` starts an encounter without resetting natural timers;
`/pale_hound highlight 120` adds a private outline and live location display.

The existing surface rules remain: 12-block attack priority, slow backoff at 16
blocks, and a 14-block gaze reaction with a one-second rush window. A recognized
hazard can take priority over approaching its holder. Sound playback
still permits one Hound sound at a time. Install the updated JAR on client and server;
GeckoLib remains a separate dependency.
