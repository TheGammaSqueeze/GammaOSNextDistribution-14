#!/usr/bin/env python3
#
# Copyright 2024, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Unit test for the Snapshot module."""

import json
import os
import pathlib
import shutil
import tempfile
import unittest

from atest.integration_tests.snapshot import Snapshot
# Disable pylint here because it conflicts with google format
# pylint: disable=wrong-import-order
from pyfakefs import fake_filesystem_unittest


class SnapshotTest(fake_filesystem_unittest.TestCase):
  """Snapshot test class unit test."""

  def setUp(self):
    self.setUpPyfakefs()
    self.temp_dir = pathlib.Path(tempfile.mkdtemp())
    super().setUp()

  def tearDown(self):
    shutil.rmtree(self.temp_dir)
    super().tearDown()

  def test_restore_dir_and_env_and_obj_from_snapshot(self):
    """Test take and restore directory, environ, and objects together."""
    workspace = self.temp_dir / 'workspace'
    restore_dst_workspace = self.temp_dir / 'workspace2'
    self.fs.create_dir(workspace)
    self.fs.create_dir(workspace.joinpath('dir1'))
    self.fs.create_file(workspace.joinpath('dir1', 'file1'), contents='test')
    env_key = 'env_name'
    env_val = 'value'
    obj_key = 'obj_name'
    obj_val = 123
    os.environ[env_key] = env_val
    snapshot = Snapshot(self.temp_dir / 'db')
    snapshot_name = 'a_snapshot_name'
    snapshot.take_snapshot(
        snapshot_name,
        workspace,
        ['*'],
        env_keys=[env_key],
        objs={obj_key: obj_val},
    )

    environ, objs = snapshot.restore_snapshot(
        snapshot_name, restore_dst_workspace.as_posix()
    )

    self.assertTrue(restore_dst_workspace.joinpath('dir1', 'file1').exists())
    self.assertEqual(environ[env_key], env_val)
    self.assertEqual(objs[obj_key], obj_val)

  def test_restore_objects_from_snapshot(self):
    """Test objects is restored from a snapshot."""
    workspace = self.temp_dir / 'workspace'
    self.fs.create_dir(workspace)
    snapshot = Snapshot(self.temp_dir / 'db')
    snapshot_name = 'a_snapshot_name'
    objs = {'a': {'b': False}}
    snapshot.take_snapshot('a_snapshot_name', workspace, ['*'], objs=objs)

    _, actual_objs = snapshot.restore_snapshot(
        snapshot_name, workspace.as_posix()
    )

    self.assertEqual(objs, actual_objs)

  def test_take_snapshot_empty_dir_snapshot_is_created(self):
    """Test taking a snapshot of an empty directory."""
    workspace = self.temp_dir / 'workspace'
    self.fs.create_dir(workspace)
    snapshot = Snapshot(self.temp_dir / 'db')
    snapshot_name = 'a_snapshot_name'

    snapshot.take_snapshot(snapshot_name, workspace, ['*'])

    self.assertTrue(
        self.temp_dir.joinpath(f'db/{snapshot_name}_metadata.json').exists()
    )

  def test_take_snapshot_with_files_files_are_recorded(self):
    """Test taking a snapshot of a directory with files."""
    workspace = self.temp_dir / 'workspace'
    self.fs.create_dir(workspace)
    self.fs.create_dir(workspace.joinpath('dir1'))
    self.fs.create_file(workspace.joinpath('dir1', 'file1'), contents='test')
    snapshot = Snapshot(self.temp_dir / 'db')
    snapshot_name = 'a_snapshot_name'

    snapshot.take_snapshot('a_snapshot_name', workspace, ['*'])

    with open(
        self.temp_dir / 'db' / f'{snapshot_name}_metadata.json',
        encoding='utf-8',
    ) as f:
      file_infos = json.load(f)
    self.assertEqual(len(file_infos), 2)
    self.assertIn('dir1/file1', file_infos)
    self.assertIn('dir1', file_infos)

  def test_take_snapshot_with_include_paths(self):
    """Test taking a snapshot with include paths."""
    workspace = self.temp_dir / 'workspace'
    self.fs.create_dir(workspace)
    self.fs.create_dir(workspace.joinpath('dir1'))
    self.fs.create_file(workspace.joinpath('dir1', 'file1'), contents='test')
    self.fs.create_dir(workspace.joinpath('dir2'))
    self.fs.create_file(workspace.joinpath('dir2', 'file2'), contents='test')
    snapshot = Snapshot(self.temp_dir / 'db')
    snapshot_name = 'a_snapshot_name'

    snapshot.take_snapshot('a_snapshot_name', workspace, include_paths=['dir2'])

    with open(
        self.temp_dir / 'db' / f'{snapshot_name}_metadata.json',
        encoding='utf-8',
    ) as f:
      file_infos = json.load(f)
    self.assertEqual(len(file_infos), 2)
    self.assertIn('dir2', file_infos)
    self.assertIn('dir2/file2', file_infos)

  def test_take_snapshot_with_exclude_paths(self):
    """Test taking a snapshot with exclude paths."""
    workspace = self.temp_dir / 'workspace'
    self.fs.create_dir(workspace)
    self.fs.create_dir(workspace.joinpath('dir1'))
    self.fs.create_file(workspace.joinpath('dir1', 'file1'), contents='test')
    self.fs.create_dir(workspace.joinpath('dir2'))
    self.fs.create_file(workspace.joinpath('dir2', 'file2'), contents='test')
    snapshot = Snapshot(self.temp_dir / 'db')
    snapshot_name = 'a_snapshot_name'

    snapshot.take_snapshot(
        'a_snapshot_name', workspace, ['*'], exclude_paths=['dir1']
    )

    with open(
        self.temp_dir / 'db' / f'{snapshot_name}_metadata.json',
        encoding='utf-8',
    ) as f:
      file_infos = json.load(f)
    self.assertEqual(len(file_infos), 2)
    self.assertIn('dir2', file_infos)
    self.assertIn('dir2/file2', file_infos)

  def test_take_snapshot_with_include_and_exclude_paths(self):
    """Test taking a snapshot with include and exclude paths."""
    workspace = self.temp_dir / 'workspace'
    self.fs.create_dir(workspace)
    self.fs.create_dir(workspace.joinpath('dir1'))
    self.fs.create_file(workspace.joinpath('dir1', 'file1'), contents='test')
    self.fs.create_dir(workspace.joinpath('dir2'))
    self.fs.create_file(workspace.joinpath('dir2', 'file2'), contents='test')
    snapshot = Snapshot(self.temp_dir / 'db')
    snapshot_name = 'a_snapshot_name'

    snapshot.take_snapshot(
        snapshot_name,
        workspace,
        include_paths=['dir1'],
        exclude_paths=['dir1/file1'],
    )

    with open(
        self.temp_dir / 'db' / f'{snapshot_name}_metadata.json',
        encoding='utf-8',
    ) as f:
      file_infos = json.load(f)
    self.assertEqual(len(file_infos), 1)
    self.assertIn('dir1', file_infos)

  def test_restore_snapshot_empty_dir(self):
    """Test restoring a snapshot of an empty directory."""
    workspace = self.temp_dir / 'workspace'
    self.fs.create_dir(workspace)
    self.fs.create_dir(workspace.joinpath('dir1'))
    snapshot = Snapshot(self.temp_dir / 'db')
    snapshot_name = 'a_snapshot_name'
    snapshot.take_snapshot(snapshot_name, workspace, ['*'])

    restore_dir = self.temp_dir / 'restore'
    snapshot.restore_snapshot(snapshot_name, restore_dir)

    self.assertTrue(restore_dir.joinpath('dir1').exists())

  def test_restore_snapshot_with_deleted_files(self):
    """Test restoring a snapshot with deleted files."""
    workspace = self.temp_dir / 'workspace'
    self.fs.create_dir(workspace)
    self.fs.create_dir(workspace.joinpath('dir1'))
    self.fs.create_file(workspace.joinpath('dir1', 'file1'), contents='test')
    snapshot = Snapshot(self.temp_dir / 'db')
    snapshot_name = 'a_snapshot_name'
    snapshot.take_snapshot(snapshot_name, workspace, ['*'])
    self.fs.remove(workspace.joinpath('dir1', 'file1'))

    snapshot.restore_snapshot(snapshot_name, workspace.as_posix())

    self.assertTrue(workspace.joinpath('dir1', 'file1').exists())

  def test_restore_snapshot_with_extra_files(self):
    """Test restoring a snapshot with extra files."""
    workspace = self.temp_dir / 'workspace'
    self.fs.create_dir(workspace)
    self.fs.create_dir(workspace.joinpath('dir1'))
    self.fs.create_file(workspace.joinpath('dir1', 'file1'), contents='test')
    snapshot = Snapshot(self.temp_dir / 'db')
    snapshot_name = 'a_snapshot_name'
    snapshot.take_snapshot('a_snapshot_name', workspace, ['*'])
    self.fs.create_file(workspace.joinpath('dir1', 'file2'), contents='test')

    snapshot.restore_snapshot(snapshot_name, workspace.as_posix())

    self.assertTrue(workspace.joinpath('dir1', 'file1').exists())
    self.assertFalse(workspace.joinpath('dir1', 'file2').exists())

  def test_restore_snapshot_with_modified_files(self):
    """Test restoring a snapshot with modified files."""
    workspace = self.temp_dir / 'workspace'
    self.fs.create_dir(workspace)
    self.fs.create_dir(workspace.joinpath('dir1'))
    file_path = workspace.joinpath('dir1', 'file1')
    self.fs.create_file(file_path, contents='test1')
    snapshot = Snapshot(self.temp_dir / 'db')
    snapshot_name = 'a_snapshot_name'
    snapshot.take_snapshot(snapshot_name, workspace, ['*'])
    file_path.write_text('test2', encoding='utf-8')
    # Increment file's modified time by 10 milliseconds
    mtime = os.path.getmtime(file_path)
    os.utime(file_path, (mtime, mtime + 0.01))

    snapshot.restore_snapshot(snapshot_name, workspace.as_posix())

    self.assertEqual(
        workspace.joinpath('dir1', 'file1').read_text('utf-8'), 'test1'
    )


if __name__ == '__main__':
  unittest.main()
