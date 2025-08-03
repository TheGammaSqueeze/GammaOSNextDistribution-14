# Copyright 2020 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Test build_android_sandboxed."""

import os
import tempfile
import unittest
from . import build_android_sandboxed


class BuildAndroidSandboxedTest(unittest.TestCase):

  def testBasic(self):
    build_android_sandboxed.nsjail.__file__ = '/'
    os.chdir('/')
    commands = build_android_sandboxed.build(
        'target_name',
        'release_target',
        'userdebug',
        nsjail_bin='/bin/true',
        chroot='/chroot',
        dist_dir='/dist_dir',
        build_id='0',
        max_cpus=1,
        build_goals=['droid', 'dist'])

    self.assertEqual(
        commands,
        [
            '/bin/true',
            '--env', 'USER=nobody',
            '--config', '/nsjail.cfg',
            '--env', 'BUILD_NUMBER=0',
            '--max_cpus=1',
            '--env', 'DIST_DIR=/dist',
            '--bindmount', '/:/src',
            '--bindmount', '/dist_dir:/dist',
            '--',
            '/src/tools/treble/build/sandbox/build_android_target.sh',
            'target_name-release_target-userdebug',
            '/src',
            'make', '-j', 'droid', 'dist',
        ]
    )

  def testBuildCommand(self):
    build_android_sandboxed.nsjail.__file__ = '/'
    os.chdir('/')
    commands = build_android_sandboxed.build(
        'target_name',
        'release_target',
        'userdebug',
        nsjail_bin='/bin/true',
        command_wrapper='/command/wrapper',
        chroot='/chroot',
        dist_dir='/dist_dir',
        build_id='0',
        max_cpus=1,
        build_goals=['droid', 'dist'])

    self.assertEqual(
        commands,
        [
            '/bin/true',
            '--env', 'USER=nobody',
            '--config', '/nsjail.cfg',
            '--env', 'BUILD_NUMBER=0',
            '--max_cpus=1',
            '--env', 'DIST_DIR=/dist',
            '--bindmount', '/:/src',
            '--bindmount', '/dist_dir:/dist',
            '--',
            '/command/wrapper',
            'target_name-release_target-userdebug',
            '/src',
            'make', '-j', 'droid', 'dist',
        ]
    )

  def testUser(self):
    build_android_sandboxed.nsjail.__file__ = '/'
    os.chdir('/')
    commands = build_android_sandboxed.build(
        'target_name',
        'release_target',
        'user',
        nsjail_bin='/bin/true',
        chroot='/chroot',
        dist_dir='/dist_dir',
        build_id='0',
        max_cpus=1,
        build_goals=['droid', 'dist'])

    self.assertEqual(
        commands,
        [
            '/bin/true',
            '--env', 'USER=nobody',
            '--config', '/nsjail.cfg',
            '--env', 'BUILD_NUMBER=0',
            '--max_cpus=1',
            '--env', 'DIST_DIR=/dist',
            '--bindmount', '/:/src',
            '--bindmount', '/dist_dir:/dist',
            '--',
            '/src/tools/treble/build/sandbox/build_android_target.sh',
            'target_name-release_target-user',
            '/src',
            'make', '-j', 'droid', 'dist',
        ]
    )

  def testExtraBuildGoals(self):
    build_android_sandboxed.nsjail.__file__ = '/'
    os.chdir('/')
    commands = build_android_sandboxed.build(
        'target_name',
        'release_target',
        'userdebug',
        nsjail_bin='/bin/true',
        chroot='/chroot',
        dist_dir='/dist_dir',
        build_id='0',
        max_cpus=1,
        build_goals=['droid', 'dist', 'extra_build_target'])

    self.assertEqual(
        commands,
        [
            '/bin/true',
            '--env', 'USER=nobody',
            '--config', '/nsjail.cfg',
            '--env', 'BUILD_NUMBER=0',
            '--max_cpus=1',
            '--env', 'DIST_DIR=/dist',
            '--bindmount', '/:/src',
            '--bindmount', '/dist_dir:/dist',
            '--',
            '/src/tools/treble/build/sandbox/build_android_target.sh',
            'target_name-release_target-userdebug',
            '/src',
            'make', '-j', 'droid', 'dist',
            'extra_build_target'
        ]
    )

  def testSkipBuildTag(self):
    TEST_CONFIG_XML = """<config>
      <target name="target_skip" tags="skip">
        <build_config>
          <goal name="droid"/>
        </build_config>
      </target>
    </config>
    """
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(TEST_CONFIG_XML)
      test_config.flush()
      build_android_sandboxed.nsjail.__file__ = '/'
      os.chdir('/')
      skip_commands = build_android_sandboxed.build(
        'target_skip',
        'release_target',
        'userdebug',
        nsjail_bin='/bin/true',
        chroot='/chroot',
        dist_dir='/dist_dir',
        build_id='0',
        max_cpus=1,
        build_goals=[],
        config_file=test_config.name)
      self.assertFalse(skip_commands)

  def testEnv(self):
    build_android_sandboxed.nsjail.__file__ = '/'
    os.chdir('/')
    commands = build_android_sandboxed.build(
        'target_name',
        'release_target',
        'userdebug',
        nsjail_bin='/bin/true',
        chroot='/chroot',
        dist_dir='/dist_dir',
        build_id='0',
        max_cpus=1,
        build_goals=['droid', 'dist'],
        env=['first_env_var=first_value', 'second_env_var=second_value'])

    self.assertEqual(
        commands,
        [
            '/bin/true',
            '--env', 'USER=nobody',
            '--config', '/nsjail.cfg',
            '--env', 'BUILD_NUMBER=0',
            '--max_cpus=1',
            '--env', 'DIST_DIR=/dist',
            '--bindmount', '/:/src',
            '--bindmount', '/dist_dir:/dist',
            '--env', 'first_env_var=first_value',
            '--env', 'second_env_var=second_value',
            '--',
            '/src/tools/treble/build/sandbox/build_android_target.sh',
            'target_name-release_target-userdebug',
            '/src',
            'make', '-j', 'droid', 'dist',
        ]
    )

if __name__ == '__main__':
  unittest.main()
