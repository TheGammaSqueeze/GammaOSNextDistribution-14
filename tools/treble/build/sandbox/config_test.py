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
"""Test config."""

import tempfile
import unittest
from . import config

_TEST_CONFIG_XML = """<config>
  <target name="android_target_1">
    <build_config>
      <goal name="droid"/>
      <flag name="dist"/>
    </build_config>
  </target>
  <target name="android_target_2" tags="cool,hot">
    <config name="fmc_framework_images" value="image1,image2"/>
    <config name="fmc_misc_info_keys" value="misc_info_keys.txt"/>
    <goal name="common_goal"/>
    <build_config tags="warm">
      <goal name="droid"/>
      <flag name="dist"/>
      <goal name="goal_for_android_target_2"/>
    </build_config>
    <build_config name="build_target_2" tags="dry">
      <config name="fmc_framework_images" value="bt1,bt2"/>
      <config name="fmc_misc_info_keys" value="misc_info_keys_2.txt"/>
      <goal name="droid"/>
      <flag name="VAR=a"/>
    </build_config>
  </target>
  <target name="android_target_3" tags="">
    <build_config>
      <goal name="droid"/>
    </build_config>
  </target>
  <target name="some_target" android_target="android_target_4">
    <goal name="droid"/>
    <build_config>
    </build_config>
  </target>
  <target name="no_goals_target">
    <build_config/>
  </target>
</config>
"""

_TEST_CONTEXTS_CONFIG_XML = """<config>
  <target name="test_target">
    <build_config>

      <!-- no contexts attribute: always selected -->
      <goal name="droid"/>

      <!-- empty contexts attribute: always selected -->
      <goal name="always" contexts=""/>

      <!-- selected if ci context requested -->
      <flag name="dist" contexts="ci"/>

      <!-- selected if x context requested -->
      <flag name="VAR=value" contexts="x"/>

      <!-- selected if ci or x context requested -->
      <goal name="extra_goal" contexts="ci,x"/>

    </build_config>
  </target>
</config>
"""


class ConfigTest(unittest.TestCase):
  """unittest for Config."""

  def testConfigFilenameNone(self):
    cfg = config.factory(None)
    self.assertIsNone(cfg)

  def testAvailableBuildTargets(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(_TEST_CONFIG_XML)
      test_config.flush()
      cfg = config.factory(test_config.name)
      self.assertListEqual(
          cfg.get_available_build_targets(),
          # Sorted; not in document order.
          [
              'android_target_1',
              'android_target_2',
              'android_target_3',
              'build_target_2',
              'no_goals_target',
              'some_target',
          ])

  def testBuildTargetTags(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(_TEST_CONFIG_XML)
      test_config.flush()
      cfg = config.factory(test_config.name)

      self.assertSetEqual(cfg.get_tags('android_target_1'), set())
      self.assertSetEqual(
          cfg.get_tags('android_target_2'), set(['cool', 'hot', 'warm']))
      self.assertSetEqual(
          cfg.get_tags('build_target_2'), set(['cool', 'hot', 'dry']))
      self.assertSetEqual(cfg.get_tags('android_target_3'), set())

      self.assertFalse(cfg.has_tag('android_target_1', 'cool'))
      self.assertFalse(cfg.has_tag('android_target_1', 'hot'))
      self.assertFalse(cfg.has_tag('android_target_1', 'meh'))

      self.assertTrue(cfg.has_tag('android_target_2', 'cool'))
      self.assertTrue(cfg.has_tag('android_target_2', 'hot'))
      self.assertFalse(cfg.has_tag('android_target_2', 'meh'))

      self.assertTrue(cfg.has_tag('build_target_2', 'cool'))
      self.assertTrue(cfg.has_tag('build_target_2', 'hot'))
      self.assertFalse(cfg.has_tag('build_target_2', 'meh'))

      self.assertFalse(cfg.has_tag('android_target_3', 'cool'))
      self.assertFalse(cfg.has_tag('android_target_3', 'hot'))
      self.assertFalse(cfg.has_tag('android_target_3', 'meh'))

  def testBuildTargetToAndroidTarget(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(_TEST_CONFIG_XML)
      test_config.flush()
      cfg = config.factory(test_config.name)

      # Test that build_target android_target_1 -> android_target_1.
      self.assertEqual(
          cfg.get_build_config_android_target('android_target_1'),
          'android_target_1')

      # Test that build_target android_target_2 -> android_target_2.
      self.assertEqual(
          cfg.get_build_config_android_target('android_target_2'),
          'android_target_2')

      # Test that build_target build_target_2 -> android_target_2.
      self.assertEqual(
          cfg.get_build_config_android_target('build_target_2'),
          'android_target_2')

      # Test overriding android_target property
      self.assertEqual(
          cfg.get_build_config_android_target('some_target'),
          'android_target_4')

  def testBuildTargetToBuildGoalsAndFlags(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(_TEST_CONFIG_XML)
      test_config.flush()
      cfg = config.factory(test_config.name)

      self.assertEqual(cfg.get_build_goals('android_target_1'), ['droid'])
      self.assertEqual(cfg.get_build_flags('android_target_1'), ['dist'])

      self.assertEqual(
          cfg.get_build_goals('android_target_2'),
          ['common_goal', 'droid', 'goal_for_android_target_2'])
      self.assertEqual(cfg.get_build_flags('android_target_2'), ['dist'])

      self.assertEqual(
          cfg.get_build_goals('build_target_2'), ['common_goal', 'droid'])
      self.assertEqual(cfg.get_build_flags('build_target_2'), ['VAR=a'])

      # Test empty goals
      self.assertEqual(cfg.get_build_goals('no_goals_target'), [])

  def testBuildTargetToBuildGoalsAndFlagsWithContexts(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(_TEST_CONTEXTS_CONFIG_XML)
      test_config.flush()
      cfg = config.factory(test_config.name)

      # Test that when contexts is the default (empty), we select only the
      # "always" goals.

      build_goals = cfg.get_build_goals('test_target')
      self.assertEqual(build_goals, ['droid', 'always'])

      # Test that when contexts is explicitly empty, we select only the
      # "always" goals.

      build_goals = cfg.get_build_goals('test_target', set())
      self.assertEqual(build_goals, ['droid', 'always'])

      # Similarly, test that when contexts is doesn't match any goal_contexts,
      # we select only the "always" goals.

      build_goals = cfg.get_build_goals('test_target', set('no_matchy'))
      self.assertEqual(build_goals, ['droid', 'always'])

      # Test that when contexts is set(['x']), we select the "always" goals and
      # the x goals.

      build_goals = cfg.get_build_goals('test_target', set(['x']))
      build_flags = cfg.get_build_flags('test_target', set(['x']))

      self.assertEqual(build_goals, ['droid', 'always', 'extra_goal'])
      self.assertEqual(build_flags, ['VAR=value'])

      # Test that when requested_contexts is set(['ci', 'x']), we select the
      # "always" goals, the ci goals, and the x goals.

      build_goals = cfg.get_build_goals('test_target', set(['ci', 'x']))
      build_flags = cfg.get_build_flags('test_target', set(['ci', 'x']))

      self.assertEqual(build_goals, ['droid', 'always', 'extra_goal'])
      self.assertEqual(build_flags, ['dist', 'VAR=value'])

  def testAllowReadWriteAll(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write("""<?xml version="1.0" encoding="UTF-8" ?>
        <config>
          <target name="target_allowed" allow_readwrite_all="true">
            <build_config>
              <goal name="droid"/>
            </build_config>
            <allow_readwrite_all/>
          </target>
          <target name="target_not_allowed">
            <build_config>
              <goal name="droid"/>
            </build_config>
          </target>
          <target name="target_also_not_allowed" allow_readwrite_all="false">
            <build_config>
              <goal name="droid"/>
            </build_config>
          </target>
        </config>""")
      test_config.flush()
      cfg = config.factory(test_config.name)

      self.assertTrue(cfg.get_allow_readwrite_all('target_allowed'))

      self.assertFalse(cfg.get_allow_readwrite_all('target_not_allowed'))

      self.assertFalse(cfg.get_allow_readwrite_all('target_also_not_allowed'))

  def testAllowedProjectsFile(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(
          '<?xml version="1.0" encoding="UTF-8" ?>'
          '<config>'
          '  <target name="target_name">'
          '    <build_config allowed_projects_file="path/to/default/build/config/allowed_projects.xml">'
          '      <goal name="build_goal"/>'
          '    </build_config>'
          '    <build_config name="has_allowed_projects_file" allowed_projects_file="path/to/named/build/config/allowed_projects.xml">'
          '      <goal name="build_goal"/>'
          '    </build_config>'
          '    <build_config name="no_allowed_projects_file">'
          '      <goal name="build_goal"/>'
          '    </build_config>'
          '  </target>'
          '</config>')
      test_config.flush()
      cfg = config.factory(test_config.name)

      self.assertEqual(
          cfg.get_allowed_projects_file('target_name'),
          'path/to/default/build/config/allowed_projects.xml')
      self.assertEqual(
          cfg.get_allowed_projects_file('has_allowed_projects_file'),
          'path/to/named/build/config/allowed_projects.xml')
      self.assertIsNone(
          cfg.get_allowed_projects_file('no_allowed_projects_file'))

  def testMergeConfig(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(_TEST_CONFIG_XML)
      test_config.flush()
      cfg = config.factory(test_config.name)

      bc_at2 = cfg.get_build_config('android_target_2')
      self.assertDictEqual(
          bc_at2.configurations, {
              'fmc_framework_images': 'image1,image2',
              'fmc_misc_info_keys': 'misc_info_keys.txt'
          })

      bc_bt2 = cfg.get_build_config('build_target_2')
      self.assertDictEqual(
          bc_bt2.configurations, {
              'fmc_framework_images': 'bt1,bt2',
              'fmc_misc_info_keys': 'misc_info_keys_2.txt'
          })


if __name__ == '__main__':
  unittest.main()
