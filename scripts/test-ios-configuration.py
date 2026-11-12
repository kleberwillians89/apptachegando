"""Offline regression tests for the Xcode -> Gradle environment bridge."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class IosConfigurationTest(unittest.TestCase):
    def invoke(self, environment=None):
        with tempfile.TemporaryDirectory() as directory:
            scripts = Path(directory) / 'scripts'
            scripts.mkdir()
            shutil.copy(Path(__file__).with_name('build-ios-framework.sh'), scripts)
            (scripts / 'gradlew-local.sh').write_text(
                '#!/bin/bash\nprintf "%s\\n" "$APP_ENVIRONMENT" "$@"\n'
            )
            env = dict(os.environ)
            env.pop('APP_ENVIRONMENT', None)
            if environment is not None:
                env['APP_ENVIRONMENT'] = environment
            return subprocess.check_output(
                ['bash', str(scripts / 'build-ios-framework.sh'), '--no-daemon'],
                env=env, text=True,
            ).splitlines()

    def test_xcode_defaults_to_qa(self):
        self.assertEqual(self.invoke(), [
            'homologation', '-Papp.environment=homologation',
            ':composeApp:embedAndSignAppleFrameworkForXcode', '--no-daemon',
        ])

    def test_development_requires_explicit_selection(self):
        self.assertEqual(self.invoke('development')[:2],
                         ['development', '-Papp.environment=development'])


if __name__ == '__main__':
    unittest.main()
