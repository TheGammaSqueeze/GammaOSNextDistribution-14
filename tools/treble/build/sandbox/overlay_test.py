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

"""Test overlay."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import os
import shutil
import subprocess
import tempfile
import unittest
from . import config
from . import overlay
import re


class BindOverlayTest(unittest.TestCase):

  def setUp(self):
    self.source_dir = tempfile.mkdtemp()
    self.destination_dir = tempfile.mkdtemp()
    #
    # base_dir/
    #   base_project/
    #     .git
    # no_git_dir/
    #   no_git_subdir1/
    #     no_git_file1
    #   no_git_subdir2/
    #     no_git_file2
    # overlays/
    #   unittest1/
    #     from_dir/
    #       .git/
    #     upper_subdir/
    #       lower_subdir/
    #         from_unittest1/
    #           .git/
    #     from_file
    #   unittest2/
    #     upper_subdir/
    #       lower_subdir/
    #         from_unittest2/
    #           .git/
    #   no_git_dir2/
    #     no_git_subdir1/
    #     no_git_subdir2/
    #       .bindmount
    #
    os.mkdir(os.path.join(self.source_dir, 'base_dir'))
    os.mkdir(os.path.join(self.source_dir, 'base_dir', 'base_project'))
    os.mkdir(os.path.join(self.source_dir, 'base_dir', 'base_project', '.git'))
    os.mkdir(os.path.join(self.source_dir, 'no_git_dir'))
    os.mkdir(os.path.join(self.source_dir, 'no_git_dir', 'no_git_subdir1'))
    open(os.path.join(self.source_dir,
                      'no_git_dir', 'no_git_subdir1', 'no_git_file1'), 'a').close()
    os.mkdir(os.path.join(self.source_dir, 'no_git_dir', 'no_git_subdir2'))
    open(os.path.join(self.source_dir,
                      'no_git_dir', 'no_git_subdir2', 'no_git_file2'), 'a').close()
    os.mkdir(os.path.join(self.source_dir, 'overlays'))
    os.mkdir(os.path.join(self.source_dir,
                          'overlays', 'unittest1'))
    os.mkdir(os.path.join(self.source_dir,
                          'overlays', 'unittest1', 'from_dir'))
    os.mkdir(os.path.join(self.source_dir,
                          'overlays', 'unittest1', 'from_dir', '.git'))
    os.mkdir(os.path.join(self.source_dir,
                          'overlays', 'unittest1', 'upper_subdir'))
    os.mkdir(os.path.join(self.source_dir,
                          'overlays', 'unittest1', 'upper_subdir',
                          'lower_subdir'))
    os.mkdir(os.path.join(self.source_dir,
                          'overlays', 'unittest1', 'upper_subdir',
                          'lower_subdir', 'from_unittest1'))
    os.mkdir(os.path.join(self.source_dir,
                          'overlays', 'unittest1', 'upper_subdir',
                          'lower_subdir', 'from_unittest1', '.git'))
    os.symlink(
            os.path.join(self.source_dir, 'overlays', 'unittest1',
                'upper_subdir', 'lower_subdir'),
            os.path.join(self.source_dir, 'overlays', 'unittest1',
                'upper_subdir', 'subdir_symlink')
            )
    open(os.path.join(self.source_dir,
                      'overlays', 'unittest1', 'from_file'), 'a').close()
    os.mkdir(os.path.join(self.source_dir,
                          'overlays', 'unittest2'))
    os.mkdir(os.path.join(self.source_dir,
                          'overlays', 'unittest2', 'upper_subdir'))
    os.mkdir(os.path.join(self.source_dir,
                          'overlays', 'unittest2', 'upper_subdir',
                          'lower_subdir'))
    os.mkdir(os.path.join(self.source_dir,
                          'overlays', 'unittest2', 'upper_subdir',
                          'lower_subdir', 'from_unittest2'))
    os.mkdir(os.path.join(self.source_dir,
                          'overlays', 'unittest2', 'upper_subdir',
                          'lower_subdir', 'from_unittest2', '.git'))

    os.mkdir(os.path.join(self.source_dir, 'overlays', 'no_git_dir2'))
    os.mkdir(os.path.join(self.source_dir,
                          'overlays', 'no_git_dir2', 'no_git_subdir1'))
    os.mkdir(os.path.join(self.source_dir,
                          'overlays', 'no_git_dir2', 'no_git_subdir2'))
    open(os.path.join(self.source_dir,
                      'overlays', 'no_git_dir2', 'no_git_subdir2', '.bindmount'),
         'a').close()

  def tearDown(self):
    shutil.rmtree(self.source_dir)

  def testValidTargetOverlayBinds(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(
        '<?xml version="1.0" encoding="UTF-8" ?>'
        '<config>'
        '  <target name="unittest">'
        '    <overlay name="unittest1"/>'
        '    <build_config>'
        '      <goal name="goal_name"/>'
        '    </build_config>'
        '  </target>'
        '</config>'
        )
      test_config.flush()
      o = overlay.BindOverlay(
          cfg=config.factory(test_config.name),
          build_target='unittest',
          source_dir=self.source_dir)
    self.assertIsNotNone(o)
    bind_mounts = o.GetBindMounts()
    bind_source = os.path.join(self.source_dir, 'overlays/unittest1/from_dir')
    bind_destination = os.path.join(self.source_dir, 'from_dir')
    self.assertEqual(bind_mounts[bind_destination], overlay.BindMount(bind_source, True, False))
    self.assertIn(os.path.join(self.source_dir, 'base_dir', 'base_project'), bind_mounts)

  def testValidTargetOverlayBindsAllowedProjects(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config, \
        tempfile.NamedTemporaryFile('w+t') as test_allowed_projects:
      test_config.write(
        '<?xml version="1.0" encoding="UTF-8" ?>'
        '<config>'
        '  <target name="unittest">'
        '    <overlay name="unittest1"/>'
        '    <build_config allowed_projects_file="%s">'
        '      <goal name="goal_name"/>'
        '    </build_config>'
        '  </target>'
        '</config>' % test_allowed_projects.name
        )
      test_config.flush()
      test_allowed_projects.write(
        '<?xml version="1.0" encoding="UTF-8" ?>'
        '<manifest>'
        '  <project name="from_dir" path="overlays/unittest1/from_dir"/>'
        '</manifest>'
        )
      test_allowed_projects.flush()
      o = overlay.BindOverlay(
          cfg=config.factory(test_config.name),
          build_target='unittest',
          source_dir=self.source_dir)
    self.assertIsNotNone(o)
    bind_mounts = o.GetBindMounts()
    self.assertIn(os.path.join(self.source_dir, 'from_dir'), bind_mounts)
    self.assertNotIn(os.path.join(self.source_dir, 'base_dir', 'base_project'), bind_mounts)

  def testMultipleOverlays(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(
        '<?xml version="1.0" encoding="UTF-8" ?>'
        '<config>'
        '  <target name="unittest">'
        '    <overlay name="unittest1"/>'
        '    <overlay name="unittest2"/>'
        '    <build_config>'
        '      <goal name="goal_name"/>'
        '    </build_config>'
        '  </target>'
        '</config>'
        )
      test_config.flush()
      o = overlay.BindOverlay(
          cfg=config.factory(test_config.name),
          build_target='unittest',
          source_dir=self.source_dir)
    self.assertIsNotNone(o)
    bind_mounts = o.GetBindMounts()
    bind_source = os.path.join(self.source_dir,
      'overlays/unittest1/upper_subdir/lower_subdir/from_unittest1')
    bind_destination = os.path.join(self.source_dir, 'upper_subdir/lower_subdir/from_unittest1')
    self.assertEqual(bind_mounts[bind_destination], overlay.BindMount(bind_source, True, False))
    bind_source = os.path.join(self.source_dir,
      'overlays/unittest2/upper_subdir/lower_subdir/from_unittest2')
    bind_destination = os.path.join(self.source_dir,
      'upper_subdir/lower_subdir/from_unittest2')
    self.assertEqual(bind_mounts[bind_destination], overlay.BindMount(bind_source, True, False))

  def testMultipleOverlaysWithAllowlist(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(
        '<?xml version="1.0" encoding="UTF-8" ?>'
        '<config>'
        '  <target name="unittest">'
        '    <overlay name="unittest1"/>'
        '    <overlay name="unittest2"/>'
        '    <allow_readwrite path="overlays/unittest1/upper_subdir/lower_subdir/from_unittest1"/>'
        '    <build_config>'
        '      <goal name="goal_name"/>'
        '    </build_config>'
        '  </target>'
        '</config>'
        )
      test_config.flush()
      o = overlay.BindOverlay(
          cfg=config.factory(test_config.name),
          build_target='unittest',
          source_dir=self.source_dir)
    self.assertIsNotNone(o)
    bind_mounts = o.GetBindMounts()
    bind_source = os.path.join(self.source_dir,
      'overlays/unittest1/upper_subdir/lower_subdir/from_unittest1')
    bind_destination = os.path.join(self.source_dir, 'upper_subdir/lower_subdir/from_unittest1')
    self.assertEqual(
        bind_mounts[bind_destination],
        overlay.BindMount(source_dir=bind_source, readonly=False, allows_replacement=False))
    bind_source = os.path.join(self.source_dir,
      'overlays/unittest2/upper_subdir/lower_subdir/from_unittest2')
    bind_destination = os.path.join(self.source_dir,
      'upper_subdir/lower_subdir/from_unittest2')
    self.assertEqual(bind_mounts[bind_destination], overlay.BindMount(bind_source, True, False))

  def testAllowReadWriteNoGitDir(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(
        '<?xml version="1.0" encoding="UTF-8" ?>'
        '<config>'
        '  <target name="unittest">'
        '    <overlay name="unittest1"/>'
        '    <overlay name="unittest2"/>'
        '    <allow_readwrite path="no_git_dir/no_git_subdir1"/>'
        '    <build_config>'
        '      <goal name="goal_name"/>'
        '    </build_config>'
        '  </target>'
        '</config>'
        )
      test_config.flush()
      o = overlay.BindOverlay(
          cfg=config.factory(test_config.name),
          build_target='unittest',
          source_dir=self.source_dir)
    self.assertIsNotNone(o)
    bind_mounts = o.GetBindMounts()
    bind_source = os.path.join(self.source_dir,
      'no_git_dir/no_git_subdir1')
    bind_destination = os.path.join(self.source_dir, 'no_git_dir/no_git_subdir1')
    self.assertIn(bind_destination, bind_mounts)
    self.assertEqual(
        bind_mounts[bind_destination],
        overlay.BindMount(source_dir=bind_source, readonly=False, allows_replacement=False))
    bind_source = os.path.join(self.source_dir,
      'no_git_dir/no_git_subdir2')
    bind_destination = os.path.join(self.source_dir,
      'no_git_dir/no_git_subdir2')
    self.assertIn(bind_destination, bind_mounts)
    self.assertEqual(bind_mounts[bind_destination], overlay.BindMount(bind_source, True, False))

  def testValidOverlaidDir(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(
        '<?xml version="1.0" encoding="UTF-8" ?>'
        '<config>'
        '  <target name="unittest">'
        '    <overlay name="unittest1"/>'
        '    <build_config>'
        '      <goal name="goal_name"/>'
        '    </build_config>'
        '  </target>'
        '</config>'
        )
      test_config.flush()
      o = overlay.BindOverlay(
          cfg=config.factory(test_config.name),
          build_target='unittest',
          source_dir=self.source_dir,
          destination_dir=self.destination_dir)
    self.assertIsNotNone(o)
    bind_mounts = o.GetBindMounts()
    bind_source = os.path.join(self.source_dir, 'overlays/unittest1/from_dir')
    bind_destination = os.path.join(self.destination_dir, 'from_dir')
    self.assertEqual(bind_mounts[bind_destination], overlay.BindMount(bind_source, True, False))

  def testValidFilesystemViewDirectoryBind(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(
        '<?xml version="1.0" encoding="UTF-8" ?>'
        '<config>'
        '  <target name="unittest">'
        '    <view name="unittestview"/>'
        '    <build_config>'
        '      <goal name="goal_name"/>'
        '    </build_config>'
        '  </target>'
        '  <view name="unittestview">'
        '    <path source="overlays/unittest1/from_dir" '
        '    destination="to_dir"/>'
        '  </view>'
        '</config>'
        )
      test_config.flush()
      o = overlay.BindOverlay(
          cfg=config.factory(test_config.name),
          build_target='unittest',
          source_dir=self.source_dir)
    self.assertIsNotNone(o)
    bind_mounts = o.GetBindMounts()
    bind_source = os.path.join(self.source_dir, 'overlays/unittest1/from_dir')
    bind_destination = os.path.join(self.source_dir, 'to_dir')
    self.assertEqual(bind_mounts[bind_destination], overlay.BindMount(bind_source, True, False))

  def testValidFilesystemViewFileBind(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(
          '<?xml version="1.0" encoding="UTF-8" ?>'
          '<config>'
          '  <target name="unittest">'
          '    <view name="unittestview"/>'
          '    <build_config>'
          '      <goal name="goal_name"/>'
          '    </build_config>'
          '  </target>'
          '  <view name="unittestview">'
          '    <path source="overlays/unittest1/from_file" '
          '      destination="to_file"/>'
          '  </view>'
          '</config>'
          )
      test_config.flush()
      o = overlay.BindOverlay(
          cfg=config.factory(test_config.name),
          build_target='unittest',
          source_dir=self.source_dir)
    self.assertIsNotNone(o)
    bind_mounts = o.GetBindMounts()
    bind_source = os.path.join(self.source_dir, 'overlays/unittest1/from_file')
    bind_destination = os.path.join(self.source_dir, 'to_file')
    self.assertEqual(bind_mounts[bind_destination], overlay.BindMount(bind_source, True, False))

  def testInvalidTarget(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(
        '<?xml version="1.0" encoding="UTF-8" ?>'
        '<config>'
        '  <target name="unittest">'
        '    <overlay name="unittest1"/>'
        '    <build_config>'
        '      <goal name="goal_name"/>'
        '    </build_config>'
        '  </target>'
        '</config>'
        )
      test_config.flush()
      with self.assertRaises(KeyError):
        overlay.BindOverlay(
            cfg=config.factory(test_config.name),
            build_target='unknown',
            source_dir=self.source_dir)

  def testExplicitBindMount(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(
        '<?xml version="1.0" encoding="UTF-8" ?>'
        '<config>'
        '  <target name="target_name">'
        '    <overlay name="no_git_dir2"/>'
        '    <build_config>'
        '      <goal name="goal_name"/>'
        '    </build_config>'
        '  </target>'
        '</config>'
        )
      test_config.flush()
      o = overlay.BindOverlay(
          cfg=config.factory(test_config.name),
          build_target='target_name',
          source_dir=self.source_dir)
    self.assertIsNotNone(o)
    bind_mounts = o.GetBindMounts()

    bind_source = os.path.join(self.source_dir, 'overlays/no_git_dir2/no_git_subdir1')
    bind_destination = os.path.join(self.source_dir, 'no_git_subdir1')
    self.assertEqual(bind_mounts[bind_destination], overlay.BindMount(bind_source, True, False))

    bind_source = os.path.join(self.source_dir, 'overlays/no_git_dir2/no_git_subdir2')
    bind_destination = os.path.join(self.source_dir, 'no_git_subdir2')
    self.assertEqual(bind_mounts[bind_destination], overlay.BindMount(bind_source, True, False))

  def testReplacementPath(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write(
        '<?xml version="1.0" encoding="UTF-8" ?>'
        '<config>'
        '  <target name="unittest">'
        '    <overlay name="unittest1">'
        '     <replacement_path path="from_dir"/>'
        '    </overlay>'
        '    <build_config>'
        '      <goal name="goal_name"/>'
        '    </build_config>'
        '  </target>'
        '</config>'
        )
      test_config.flush()
      o = overlay.BindOverlay(
            cfg=config.factory(test_config.name),
            build_target='unittest',
            source_dir=self.source_dir)
    self.assertIsNotNone(o)
    bind_mounts = o.GetBindMounts()
    bind_source = os.path.join(self.source_dir, 'overlays/unittest1/from_dir')
    bind_destination = os.path.join(self.source_dir, 'from_dir')
    self.assertEqual(bind_mounts[bind_destination],
                     overlay.BindMount(bind_source, True, True))

if __name__ == '__main__':
  unittest.main()
