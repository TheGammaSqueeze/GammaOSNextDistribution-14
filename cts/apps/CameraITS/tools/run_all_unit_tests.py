# Copyright 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Unit tests for run_all_tests script."""

import itertools
import os
import unittest

import run_all_tests


class RunAllUnitTests(unittest.TestCase):
  """Unit tests to verify run_all_tests tool."""

  def _scene_folders_exist(self, scene_folders):
    """Asserts all scene_folders exist in tests directory."""
    for scene_folder in scene_folders:
      scene_path = os.path.join(os.environ['CAMERA_ITS_TOP'],
                                'tests', scene_folder)
      self.assertTrue(os.path.exists(scene_path),
                      msg=f'{scene_path} does not exist!')

  def test_sub_camera_tests(self):
    """Ensures SUB_CAMERA_TESTS matches test files in tests directory."""
    for scene_folder in run_all_tests.SUB_CAMERA_TESTS:
      for test in run_all_tests.SUB_CAMERA_TESTS[scene_folder]:
        test_path = os.path.join(os.environ['CAMERA_ITS_TOP'],
                                 'tests', scene_folder, f'{test}.py')
        self.assertTrue(os.path.exists(test_path),
                        msg=f'{test_path} does not exist!')

  def test_all_scenes(self):
    """Ensures _ALL_SCENES list matches scene folders in test directory."""
    self._scene_folders_exist(run_all_tests._ALL_SCENES)

  def test_tablet_scenes(self):
    """Ensures _TABLET_SCENES list matches scene folders in test directory."""
    self._scene_folders_exist(run_all_tests._TABLET_SCENES)

  def test_scene_req(self):
    """Ensures _SCENE_REQ scenes match scene folders in test directory."""
    self._scene_folders_exist(run_all_tests._SCENE_REQ.keys())

  def test_grouped_scenes(self):
    """Ensures _GROUPED_SCENES scenes match scene folders in test directory."""
    # flatten list of scene folder lists stored as values of a dictionary
    scene_folders = list(itertools.chain.from_iterable(
        run_all_tests._GROUPED_SCENES.values()))
    self._scene_folders_exist(scene_folders)

if __name__ == '__main__':
  unittest.main()
