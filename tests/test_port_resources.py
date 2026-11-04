"""Source/resource parity checks. These do not replace compilation or gameplay tests."""
import hashlib
import json
from pathlib import Path
import re
import tomllib
import unittest

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
BASE = json.loads((ROOT / 'tests/original_baseline.json').read_text())


def digest(data):
    return hashlib.sha256(data).hexdigest()


class PortResources(unittest.TestCase):
    def test_original_assets_unchanged(self):
        for name, expected in BASE['assets'].items():
            if name in BASE['migrated_assets']:
                continue
            with self.subTest(name=name):
                if name == 'assets/pale_hound/sounds.json':
                    original = (ROOT / 'tests/original_sounds.json').read_bytes()
                    self.assertEqual(digest(original), expected)
                    actual = json.loads((RES / name).read_text())
                    for key in ['ambient_crying', 'stalking_underground', 'sees_player', 'sees_player_surface']:
                        actual.pop(key)
                    self.assertEqual(actual, json.loads(original))
                else:
                    self.assertEqual(digest((RES / name).read_bytes()), expected)

    def test_necessary_asset_migrations_preserve_content(self):
        import copy
        name = 'assets/pale_hound/animations/pale_hound.animation.json'
        expected = copy.deepcopy(BASE['migrated_assets'][name])
        rotations = expected['animations']['animation.pale_hound.calm_idle']['bones']['upperleftleg']['rotation']
        for time, vector in rotations.items():
            rotations[time] = {'vector': [float(v.split('+')[0]) for v in vector], 'easing': 'easeOutSine'}
        actual = json.loads((RES / name).read_text())
        expected['animations'].pop('animation.pale_hound.crawl')
        actual['animations'].pop('animation.pale_hound.crawl')
        actual['animations'].pop('animation.pale_hound.crawl_idle')
        actual['animations'].pop('animation.pale_hound.crawl_low')
        actual['animations'].pop('animation.pale_hound.crawl_low_idle')
        for name in ['crawl_backward', 'crawl_tunnel_backward', 'crawl_low_backward']:
            actual['animations'].pop('animation.pale_hound.' + name)
        actual['animations'].pop('animation.pale_hound.crawl_tunnel')
        actual['animations'].pop('animation.pale_hound.crawl_tunnel_idle')
        self.assertEqual(actual, expected)
        name = 'assets/pale_hound/lang/en_us.json'
        expected = dict(BASE['migrated_assets'][name])
        for value in json.loads((RES / 'assets/pale_hound/sounds.json').read_text()).values():
            if 'subtitle' in value:
                if not value['subtitle'].startswith('subtitles.pale_hound.'):
                    expected[value['subtitle']] = value['subtitle']
        expected.update({
            'difficulty.pale_hound.story': 'Story',
            'pale_hound.story.description': 'Easy combat. Only hostile mobs in the Story allowlist can spawn (default: Pale Hound).',
            'pale_hound.story.hardcore': 'Story difficulty is unavailable in Hardcore worlds.',
            'gamerule.paleHoundStoryMode': 'Story difficulty',
            'gamerule.paleHoundStoryMode.description': 'Use Easy combat and allow only configured hostile mobs (default: Pale Hound). Removes other loaded hostiles. Not available in Hardcore.',
            'subtitles.pale_hound.ambient_crying': 'Distant crying',
            'subtitles.pale_hound.stalking_underground': 'Something stalks nearby',
            'subtitles.pale_hound.sees_player': 'The Hound notices you',
            'subtitles.pale_hound.sees_player_surface': 'The Hound watches from outside',
        })
        self.assertEqual(json.loads((RES / name).read_text()), expected)

    def test_grounded_crawl_loops_and_idle_pose(self):
        animations = json.loads((RES / 'assets/pale_hound/animations/pale_hound.animation.json').read_text())['animations']
        for moving, stationary in [('crawl', 'crawl_idle'), ('crawl_tunnel', 'crawl_tunnel_idle'), ('crawl_low', 'crawl_low_idle')]:
            crawl = animations['animation.pale_hound.' + moving]
            idle = animations['animation.pale_hound.' + stationary]
            self.assertTrue(crawl['loop'])
            for bone in crawl['bones'].values():
                for channel in bone.values():
                    if isinstance(channel, dict):
                        self.assertEqual(channel['0.0000'], channel['1.6000'])
                        values = list(channel.values())
                        for left, right in zip(values, values[1:]):
                            self.assertLess(max(abs(a - b) for a, b in zip(left, right)), 60)
            for bone in idle['bones'].values():
                for channel in bone.values():
                    self.assertIsInstance(channel, list)  # No swimming/stroking while standing still.
        for normal, low in [('crawl_tunnel', 'crawl_low'), ('crawl_tunnel_idle', 'crawl_low_idle')]:
            normal_bones = animations['animation.pale_hound.' + normal]['bones']
            low_bones = dict(animations['animation.pale_hound.' + low]['bones'])
            base = low_bones.pop('base')
            self.assertEqual(low_bones, normal_bones)
            # Compression about the base pivot must keep the feet on the floor.
            self.assertAlmostEqual(19.5 * (1 - base['scale'][1]) + base['position'][1], 0)

    def test_reverse_crawls_plant_limbs_in_reverse_order(self):
        animations = json.loads((RES / 'assets/pale_hound/animations/pale_hound.animation.json').read_text())['animations']
        for pose in ['crawl', 'crawl_tunnel', 'crawl_low']:
            normal = animations['animation.pale_hound.' + pose]
            reverse = animations['animation.pale_hound.' + pose + '_backward']
            self.assertTrue(reverse['loop'])
            self.assertEqual(reverse['animation_length'], normal['animation_length'])
            for bone, channels in normal['bones'].items():
                for name, value in channels.items():
                    actual = reverse['bones'][bone][name]
                    if isinstance(value, dict):
                        for time, frame in value.items():
                            self.assertEqual(actual[f"{1.6 - float(time):.4f}"], frame)
                    else:
                        self.assertEqual(actual, value)  # Preserve head/neck attachment and clearance.

    def test_user_audio_encoding_and_event_pools(self):
        clips = json.loads((ROOT / 'docs/USER_AUDIO.json').read_text())['clips']
        self.assertEqual(len(clips), 13)
        sounds = json.loads((RES / 'assets/pale_hound/sounds.json').read_text())
        for event, count in [('ambient_crying', 5), ('sees_player', 3), ('sees_player_surface', 2), ('stalking_underground', 3)]:
            self.assertEqual(len(sounds[event]['sounds']), count)
            self.assertTrue(all(entry['stream'] for entry in sounds[event]['sounds']))
        for clip in clips:
            data = (RES / ('assets/pale_hound/sounds/' + clip['asset'] + '.ogg')).read_bytes()
            self.assertTrue(data.startswith(b'OggS'))
            header = data.index(b'\x01vorbis')
            self.assertEqual(data[header + 11], 2)  # Preserve supplied stereo playback.
            self.assertEqual(int.from_bytes(data[header + 12:header + 16], 'little'), 48000)
            self.assertIn('pale_hound:' + clip['asset'], [entry['name'] for entry in sounds[clip['event']]['sounds']])

    def test_config_defaults_and_category_order_unchanged(self):
        for name, expected in BASE['config_static_blocks'].items():
            with self.subTest(name=name):
                source = (ROOT / f'src/main/java/de/cadentem/pale_hound/config/{name}.java').read_text()
                block = source[source.index('    static {'):]
                # The added spawn pacing knob is intentional; retain the original baseline for every old option.
                if name == 'ServerConfig':
                    addition = 'SPAWN_TIMER_MULTIPLIER = BUILDER.comment("Scale spawn waits and cooldowns; 0.8 means 20% shorter waits, 1.0 restores original timing. Does not affect noise timers.").defineInRange("spawn_timer_multiplier", 0.8, 0.1, 5.0);'
                    self.assertIn(addition, block)
                    block = block.replace(addition, '')
                    for addition in [
                        'STORY_HOSTILE_ALLOWLIST = BUILDER.comment("Hostile entity IDs allowed in Story difficulty. Empty blocks all hostiles. Other difficulties are unaffected.").defineListAllowEmpty("story_hostile_allowlist", List.of("pale_hound:pale_hound"), value -> value instanceof String id && id.contains(":") && net.minecraft.resources.ResourceLocation.tryParse(id) != null);',
                        'SURFACE_STALK_DURATION_MULTIPLIER = BUILDER.comment("Additional multiplier for surface stalking duration; 2.0 gives about 3 to 4.5 minutes with default presence settings.").defineInRange("surface_stalk_duration_multiplier", 2.0, 0.1, 10.0);',
                        'SURFACE_SPAWN_TIMER_MULTIPLIER = BUILDER.comment("Additional surface-only spawn wait multiplier; 2.0 doubles surface waits without changing cave or noise timers.").defineInRange("surface_spawn_timer_multiplier", 2.0, 0.1, 10.0);',
                        'PRESENCE_TIME_MULTIPLIER = BUILDER.comment("Scale normal lifetime, chase duration and stalking duration; escapes last until out of sight.").defineInRange("presence_time_multiplier", 3.0, 0.1, 10.0);',
                        'ROAM_RADIUS = BUILDER.comment("Horizontal radius for idle roaming destination searches.").defineInRange("roam_radius", 24, 8, 64);',
                        'ROAM_INTERVAL = BUILDER.comment("Ticks between idle roaming attempts on average.").defineInRange("roam_interval", 40, 10, 1200);',
                        'FOREST_SPAWN_CHANCE_MULTIPLIER = BUILDER.comment("Scale the ready-to-spawn chance in forested biomes; 1.25 is a 25% bonus. Does not change timers.").defineInRange("forest_spawn_chance_multiplier", 1.25, 0.0, 5.0);',
                    ]:
                        self.assertIn(addition, block)
                        block = block.replace(addition, '')
                    # The new default blacklist deliberately replaces the original surface list.
                    legacy = re.search(r'LEGACY_SURFACE_BIOME_BLACKLIST = (List\.of\(.*?\));', source).group(1)
                    block = block.replace('DEFAULT_SURFACE_BIOME_BLACKLIST', legacy)
                normalized = re.sub(r'\s+', '', block).replace('(Object)500', '500')
                self.assertEqual(digest(normalized.encode()), expected)

    def test_original_ai_logic_unchanged(self):
        for filename, expected in BASE['ai_class_bodies'].items():
            if filename == 'PaleHoundFleeGoal.java':
                continue  # Intentional retreat rewrite is covered by physical runtime tests.
            with self.subTest(filename=filename):
                source = (ROOT / 'src/main/java/de/cadentem/pale_hound/entities/goals' / filename).read_text()
                body = source[source.index('public '):]
                body = re.sub(r'this\.(?:paleHound|caveDweller)\.isValidEncounterTarget\(', 'Utils.isValidTarget(', body)
                body = body.replace('!this.paleHound.targetIsFacingMe && !this.paleHound.isSurfaceStalkAttack()', '!this.paleHound.targetIsFacingMe')
                body = body.replace('maxUpStep()', 'getStepHeight()')
                body = body.replace('Utils.presenceTicks(', 'Utils.secondsToTicks(')
                body = body.replace('this.paleHound.setCrawling(false);', 'this.paleHound.getEntityData().set(PaleHoundEntity.CRAWLING_ACCESSOR, false);')
                body = body.replace('EventHooks.canEntityGrief', 'ForgeEventFactory.getMobGriefingEvent')
                body = body.replace('HalfTransparentBlock', 'AbstractGlassBlock')
                body = re.sub(r'if \(this\.paleHound\.isSurfaceStalking\(\)(?: \|\| this\.paleHound\.currentRoll != Roll\.CHASE \|\| this\.paleHound\.isFleeing)?\) \{\s*return false;\s*\}', '', body)
                self.assertEqual(digest(re.sub(r'\s+', '', body).encode()), expected)

    def test_loot_migrated_without_changing_pools(self):
        actual = json.loads((RES / 'data/pale_hound/loot_table/entities/pale_hound.json').read_text())
        expected = dict(BASE['original_loot'], type='minecraft:entity')
        self.assertEqual(actual, expected)
        self.assertFalse((RES / 'data/pale_hound/loot_tables').exists())

    def test_sound_and_animation_references_exist(self):
        assets = RES / 'assets/pale_hound'
        sounds = json.loads((assets / 'sounds.json').read_text())
        for entry in sounds.values():
            for sound in entry['sounds']:
                name = sound if isinstance(sound, str) else sound['name']
                namespace, path = name.split(':') if ':' in name else ('minecraft', name)
                if namespace == 'pale_hound':
                    self.assertTrue((assets / f'sounds/{path}.ogg').is_file(), name)
        source = (ROOT / 'src/main/java/de/cadentem/pale_hound/entities/PaleHoundEntity.java').read_text()
        animations = json.loads((assets / 'animations/pale_hound.animation.json').read_text())['animations']
        for name in re.findall(r'\.then\("([^"]+)"', source):
            self.assertIn(name, animations)
        registered = (ROOT / 'src/main/java/de/cadentem/pale_hound/registry/ModSounds.java').read_text()
        self.assertEqual(set(re.findall(r'registerSoundEvent\("([^"]+)"\)', registered)), set(sounds))

    def test_target_and_supplied_dependency(self):
        metadata = tomllib.loads((RES / 'META-INF/neoforge.mods.toml').read_text())
        dependencies = {d['modId']: d for d in metadata['dependencies']['pale_hound']}
        self.assertEqual(dependencies['minecraft']['versionRange'], '[1.21.1]')
        self.assertEqual(dependencies['geckolib']['versionRange'], '[4.9.3,5)')
        self.assertIn("version = '21.1.262'", (ROOT / 'build.gradle').read_text())
        self.assertEqual(digest((ROOT / 'libs/geckolib-neoforge-1.21.1-4.9.3.jar').read_bytes()), BASE['geckolib_sha256'])
        for file in (ROOT / 'src/main/java').rglob('*.java'):
            self.assertNotIn('net.minecraftforge', file.read_text(), str(file))
            self.assertIsNone(re.search(r'\b[mf]_\d+_\b', file.read_text()), str(file))


if __name__ == '__main__':
    unittest.main()
