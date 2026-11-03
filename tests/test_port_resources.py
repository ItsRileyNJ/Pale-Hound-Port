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
                self.assertEqual(digest((RES / name).read_bytes()), expected)

    def test_necessary_asset_migrations_preserve_content(self):
        import copy
        name = 'assets/pale_hound/animations/pale_hound.animation.json'
        expected = copy.deepcopy(BASE['migrated_assets'][name])
        rotations = expected['animations']['animation.pale_hound.calm_idle']['bones']['upperleftleg']['rotation']
        for time, vector in rotations.items():
            rotations[time] = {'vector': [float(v.split('+')[0]) for v in vector], 'easing': 'easeOutSine'}
        self.assertEqual(json.loads((RES / name).read_text()), expected)
        name = 'assets/pale_hound/lang/en_us.json'
        expected = dict(BASE['migrated_assets'][name])
        for value in json.loads((RES / 'assets/pale_hound/sounds.json').read_text()).values():
            if 'subtitle' in value:
                expected[value['subtitle']] = value['subtitle']
        self.assertEqual(json.loads((RES / name).read_text()), expected)

    def test_config_defaults_and_category_order_unchanged(self):
        for name, expected in BASE['config_static_blocks'].items():
            with self.subTest(name=name):
                source = (ROOT / f'src/main/java/de/cadentem/pale_hound/config/{name}.java').read_text()
                block = source[source.index('    static {'):]
                normalized = re.sub(r'\s+', '', block).replace('(Object)500', '500')
                self.assertEqual(digest(normalized.encode()), expected)

    def test_original_ai_logic_unchanged(self):
        for filename, expected in BASE['ai_class_bodies'].items():
            with self.subTest(filename=filename):
                source = (ROOT / 'src/main/java/de/cadentem/pale_hound/entities/goals' / filename).read_text()
                body = source[source.index('public '):]
                body = body.replace('maxUpStep()', 'getStepHeight()')
                body = body.replace('EventHooks.canEntityGrief', 'ForgeEventFactory.getMobGriefingEvent')
                body = body.replace('HalfTransparentBlock', 'AbstractGlassBlock')
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
