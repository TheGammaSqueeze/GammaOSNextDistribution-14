# Copyright (C) 2020 The Android Open Source Project
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
"""Test manifest split."""

import json
import os
import re
import subprocess
import tempfile
import unittest
import unittest.mock
import xml.etree.ElementTree as ET

import manifest_split


class ManifestSplitTest(unittest.TestCase):

  def test_read_config(self):
    with tempfile.NamedTemporaryFile('w+t') as test_config:
      test_config.write("""
        <config>
          <add_project name="add1" />
          <add_project name="add2" />
          <remove_project name="remove1" />
          <remove_project name="remove2" />
          <path_mapping pattern="p1.*" sub="$0" />
        </config>""")
      test_config.flush()
      config = manifest_split.ManifestSplitConfig.from_config_files(
          [test_config.name])
      self.assertEqual(config.remove_projects, {
          'remove1': test_config.name,
          'remove2': test_config.name
      })
      self.assertEqual(config.add_projects, {
          'add1': test_config.name,
          'add2': test_config.name
      })
      self.assertEqual(config.path_mappings, [
          manifest_split.PathMappingConfig(re.compile('p1.*'), '$0'),
      ])

  def test_get_repo_projects_from_manifest(self):
    manifest_contents = """
      <manifest>
        <project name="platform/project1" path="system/project1" />
        <project name="platform/project2" path="system/project2" />
        <project name="platform/project3" path="system/project3" />
      </manifest>"""
    manifest = ET.ElementTree(ET.fromstring(manifest_contents))
    projects = manifest_split.get_repo_projects(
        None, manifest, path_mappings=[])
    self.assertDictEqual(
        {
            'system/project1': 'platform/project1',
            'system/project2': 'platform/project2',
            'system/project3': 'platform/project3',
        }, projects)


  def test_get_repo_projects(self):
    with tempfile.NamedTemporaryFile('w+t') as repo_list_file:
      repo_list_file.write("""
        system/project1 : platform/project1
        system/project2 : platform/project2""")
      repo_list_file.flush()
      repo_projects = manifest_split.get_repo_projects(
          repo_list_file.name, None, path_mappings=[])
      self.assertEqual(
          repo_projects, {
              'system/project1': 'platform/project1',
              'system/project2': 'platform/project2',
          })

  def test_get_repo_projects_with_mappings(self):
    with tempfile.NamedTemporaryFile('w+t') as repo_list_file:
      repo_list_file.write("""
        overlay/system/project1 : platform/project1
        system/project2 : platform/project2
        hide/this/one : platform/project3""")
      repo_list_file.flush()
      path_mappings = [
          manifest_split.PathMappingConfig(re.compile('^overlay/(.*)'), '\\1'),
          manifest_split.PathMappingConfig(re.compile('^hide/this/one.*'), ''),
      ]

      repo_projects = manifest_split.get_repo_projects(repo_list_file.name,
                                                       None,
                                                       path_mappings)
      self.assertEqual(
          repo_projects, {
              'system/project1': 'platform/project1',
              'system/project2': 'platform/project2',
          })

  def test_get_module_info(self):
    with tempfile.NamedTemporaryFile('w+t') as module_info_file:
      module_info_file.write("""{
        "target1a": { "class": ["EXECUTABLES"], "path": ["system/project1"], "dependencies": ["target2"] },
        "target1b": { "class": ["EXECUTABLES"], "path": ["system/project1"], "dependencies": ["target3", "target42"] },
        "target2": { "class": ["SHARED_LIBRARIES"], "path": ["out/project2"], "dependencies": [] },
        "target3": { "class": ["SHARED_LIBRARIES"], "path": ["vendor/google/project3"], "dependencies": ["x", "y", "z"] },
        "target4a": { "class": ["APPS"], "path": ["system/project4"], "dependencies": ["out/target/common/obj/JAVA_LIBRARIES/target4b_intermediates/classes-header.jar"] },
        "target4b": { "class": ["JAVA_LIBRARIES"],  "path": ["system/project4"], "dependencies": [] }
      }""")
      module_info_file.flush()
      repo_projects = {
          'system/project1': 'platform/project1',
          'system/project4': 'platform/project4',
          'vendor/google/project3': 'vendor/project3',
      }
      ignore_paths = set(['out/'])
      module_info = manifest_split.ModuleInfo(module_info_file.name,
                                              repo_projects, ignore_paths)
      self.assertEqual(
          module_info.project_modules, {
              'platform/project1': set(['target1a', 'target1b']),
              'platform/project4': set(['target4a', 'target4b']),
              'vendor/project3': set(['target3']),
          })
      self.assertEqual(
          module_info.module_project, {
              'target1a': 'platform/project1',
              'target1b': 'platform/project1',
              'target3': 'vendor/project3',
              'target4a': 'platform/project4',
              'target4b': 'platform/project4',
          })
      self.assertEqual(
          module_info.module_class, {
              'target1a': 'EXECUTABLES',
              'target1b': 'EXECUTABLES',
              'target2': 'SHARED_LIBRARIES',
              'target3': 'SHARED_LIBRARIES',
              'target4a': 'APPS',
              'target4b': 'JAVA_LIBRARIES',
          })
      self.assertEqual(
          module_info.module_deps, {
              'target1a': ['target2'],
              'target1b': ['target3', 'target42'],
              'target2': [],
              'target3': ['x', 'y', 'z'],
              'target4a': ['target4b'],
              'target4b': [],
          })

  def test_get_module_info_raises_on_unknown_module_path(self):
    with tempfile.NamedTemporaryFile('w+t') as module_info_file:
      module_info_file.write("""{
        "target1": { "class": ["EXECUTABLES"], "path": ["system/unknown/project1"], "dependencies": [] }
      }""")
      module_info_file.flush()
      repo_projects = {}
      ignore_paths = set()
      with self.assertRaisesRegex(ValueError,
                                  'Unknown module path for module target1'):
        manifest_split.ModuleInfo(module_info_file.name, repo_projects,
                                  ignore_paths)

  @unittest.mock.patch.object(subprocess, 'check_output', autospec=True)
  def test_get_ninja_inputs(self, mock_check_output):
    mock_check_output.return_value = b"""
    path/to/input1
    path/to/input2
    path/to/TEST_MAPPING
    path/to/MODULE_LICENSE_GPL
    """

    inputs = manifest_split.get_ninja_inputs('unused', 'unused', ['droid'])
    self.assertEqual(inputs, {'path/to/input1', 'path/to/input2'})

  @unittest.mock.patch.object(subprocess, 'check_output', autospec=True)
  def test_get_ninja_inputs_includes_test_mapping(self, mock_check_output):
    mock_check_output.return_value = b"""
    path/to/input1
    path/to/input2
    path/to/TEST_MAPPING
    """

    inputs = manifest_split.get_ninja_inputs('unused', 'unused',
                                             ['droid', 'test_mapping'])
    self.assertEqual(
        inputs, {'path/to/input1', 'path/to/input2', 'path/to/TEST_MAPPING'})

  @unittest.mock.patch.object(subprocess, 'check_output', autospec=True)
  def test_get_kati_makefiles(self, mock_check_output):
    with tempfile.TemporaryDirectory() as temp_dir:
      os.chdir(temp_dir)

      makefiles = [
          'device/oem1/product1.mk',
          'device/oem2/product2.mk',
          'device/google/google_product.mk',
          'overlays/oem_overlay/device/oem3/product3.mk',
          'packages/apps/Camera/Android.mk',
      ]
      for makefile in makefiles:
        os.makedirs(os.path.dirname(makefile))
        os.mknod(makefile)

      symlink_src = os.path.join(temp_dir, 'vendor/oem4/symlink_src.mk')
      os.makedirs(os.path.dirname(symlink_src))
      os.mknod(symlink_src)
      symlink_dest = 'device/oem4/symlink_dest.mk'
      os.makedirs(os.path.dirname(symlink_dest))
      os.symlink(symlink_src, symlink_dest)
      # Only append the symlink destination, not where the symlink points to.
      # (The Kati stamp file does not resolve symlink sources.)
      makefiles.append(symlink_dest)

      # Mock the output of ckati --dump_stamp_tool:
      mock_check_output.return_value = '\n'.join(makefiles).encode()

      kati_makefiles = manifest_split.get_kati_makefiles(
          'stamp-file', ['overlays/oem_overlay/'])
      self.assertEqual(
          kati_makefiles,
          set([
              # Regular product makefiles
              'device/oem1/product1.mk',
              'device/oem2/product2.mk',
              # Product makefile remapped from an overlay
              'device/oem3/product3.mk',
              # Product makefile symlink and its source
              'device/oem4/symlink_dest.mk',
              'vendor/oem4/symlink_src.mk',
          ]))

  def test_scan_repo_projects(self):
    repo_projects = {
        'system/project1': 'platform/project1',
        'system/project2': 'platform/project2',
    }
    self.assertEqual(
        manifest_split.scan_repo_projects(repo_projects,
                                          'system/project1/path/to/file.h'),
        'system/project1')
    self.assertEqual(
        manifest_split.scan_repo_projects(
            repo_projects, 'system/project2/path/to/another_file.cc'),
        'system/project2')
    self.assertIsNone(
        manifest_split.scan_repo_projects(
            repo_projects, 'system/project3/path/to/unknown_file.h'))

  def test_get_input_projects(self):
    repo_projects = {
        'system/project1': 'platform/project1',
        'system/project2': 'platform/project2',
        'system/project4': 'platform/project4',
    }
    inputs = [
        'system/project1/path/to/file.h',
        'out/path/to/out/file.h',
        'system/project2/path/to/another_file.cc',
        'system/project3/path/to/unknown_file.h',
        '/tmp/absolute/path/file.java',
    ]
    self.assertEqual(
        manifest_split.get_input_projects(repo_projects, inputs), {
            'platform/project1': ['system/project1/path/to/file.h'],
            'platform/project2': ['system/project2/path/to/another_file.cc'],
        })

  def test_update_manifest(self):
    manifest_contents = """
      <manifest>
        <project name="platform/project1" path="system/project1" />
        <project name="platform/project2" path="system/project2" />
        <project name="platform/project3" path="system/project3" />
      </manifest>"""
    input_projects = set(['platform/project1', 'platform/project3'])
    remove_projects = set(['platform/project3'])
    manifest = manifest_split.update_manifest(
        ET.ElementTree(ET.fromstring(manifest_contents)), input_projects,
        remove_projects)

    projects = manifest.getroot().findall('project')
    self.assertEqual(len(projects), 1)
    self.assertEqual(
        ET.tostring(projects[0]).strip().decode(),
        '<project name="platform/project1" path="system/project1" />')

  @unittest.mock.patch.object(subprocess, 'check_output', autospec=True)
  def test_create_split_manifest(self, mock_check_output):
    with tempfile.NamedTemporaryFile('w+t') as repo_list_file, \
      tempfile.NamedTemporaryFile('w+t') as manifest_file, \
      tempfile.NamedTemporaryFile('w+t') as module_info_file, \
      tempfile.NamedTemporaryFile('w+t') as config_file, \
      tempfile.NamedTemporaryFile('w+t') as split_manifest_file, \
      tempfile.TemporaryDirectory() as temp_dir:

      os.chdir(temp_dir)

      repo_list_file.write("""
        system/project1 : platform/project1
        system/project2 : platform/project2
        system/project3 : platform/project3
        system/project4 : platform/project4
        system/project5 : platform/project5
        system/project6 : platform/project6
        system/project7 : platform/project7
        system/project8 : platform/project8
        system/project9 : platform/project9
        vendor/project1 : vendor/project1""")
      repo_list_file.flush()

      manifest_file.write("""
        <manifest>
          <project name="platform/project1" path="system/project1" />
          <project name="platform/project2" path="system/project2" />
          <project name="platform/project3" path="system/project3" />
          <project name="platform/project4" path="system/project4" />
          <project name="platform/project5" path="system/project5" />
          <project name="platform/project6" path="system/project6" />
          <project name="platform/project7" path="system/project7" />
          <project name="platform/project8" path="system/project8" />
          <project name="platform/project9" path="system/project9" />
          <project name="vendor/project1" path="vendor/project1" />
        </manifest>""")
      manifest_file.flush()

      module_info_file.write("""{
        "droid": { "class": ["EXECUTABLES"], "path": ["system/project1"], "dependencies": [] },
        "target_a": { "class": ["EXECUTABLES"], "path": ["out/project2"], "dependencies": ["unknown_module_a"] },
        "target_b": { "class": ["EXECUTABLES"], "path": ["system/project3"], "dependencies": ["target_f", "unknown_module_b"] },
        "target_c": { "class": ["EXECUTABLES"], "path": ["system/project4"], "dependencies": [] },
        "target_d": { "class": ["EXECUTABLES"], "path": ["system/project5"], "dependencies": [] },
        "target_e": { "class": ["EXECUTABLES"], "path": ["system/project6"], "dependencies": [] },
        "target_f": { "class": ["HEADER_LIBRARIES"], "path": ["system/project7"], "dependencies": [] },
        "target_g": { "class": ["SHARED_LIBRARIES"], "path": ["system/project8"], "dependencies": ["target_h"] },
        "target_h": { "class": ["HEADER_LIBRARIES"], "path": ["system/project9"], "dependencies": [] }
      }""")
      module_info_file.flush()

      # droid needs inputs from project1 and project3
      ninja_inputs_droid = b"""
      system/project1/file1
      system/project1/file2
      system/project3/file1
      """

      # target_b (indirectly included due to being in project3) needs inputs
      # from project3 and project4
      ninja_inputs_target_b = b"""
      system/project3/file2
      system/project4/file1
      """

      # target_c (indirectly included due to being in project4) needs inputs
      # from only project4
      ninja_inputs_target_c = b"""
      system/project4/file2
      system/project4/file3
      """

      product_makefile = 'vendor/project1/product.mk'
      os.makedirs(os.path.dirname(product_makefile))
      os.mknod(product_makefile)
      kati_stamp_dump = product_makefile.encode()

      mock_check_output.side_effect = [
          ninja_inputs_droid,
          kati_stamp_dump,
          ninja_inputs_target_b,
          ninja_inputs_target_c,
      ]

      # The config file says to manually include project6
      config_file.write("""
        <config>
          <add_project name="platform/project6" />
        </config>""")
      config_file.flush()

      debug_file = os.path.join(temp_dir, 'debug.json')

      manifest_split.create_split_manifest(
          ['droid'], manifest_file.name, split_manifest_file.name,
          [config_file.name], repo_list_file.name, 'build-target.ninja',
          'ninja', module_info_file.name, 'unused kati stamp',
          ['unused overlay'], [], debug_file)
      split_manifest = ET.parse(split_manifest_file.name)
      split_manifest_projects = [
          child.attrib['name']
          for child in split_manifest.getroot().findall('project')
      ]
      self.assertEqual(
          split_manifest_projects,
          [
              # From droid
              'platform/project1',
              # From droid
              'platform/project3',
              # From target_b (module within project3, indirect dependency)
              'platform/project4',
              # Manual inclusion from config file
              'platform/project6',
              # From target_b (depends on target_f header library)
              'platform/project7',
              # Inclusion from the Kati makefile stamp
              'vendor/project1',
          ])

      with open(debug_file) as debug_fp:
        debug_data = json.load(debug_fp)

        # Dependency for droid, but no other adjacent modules
        self.assertTrue(debug_data['platform/project1']['direct_input'])
        self.assertFalse(debug_data['platform/project1']['adjacent_input'])
        self.assertFalse(debug_data['platform/project1']['deps_input'])

        # Dependency for droid and an adjacent module
        self.assertTrue(debug_data['platform/project3']['direct_input'])
        self.assertTrue(debug_data['platform/project3']['adjacent_input'])
        self.assertFalse(debug_data['platform/project3']['deps_input'])

        # Dependency only for an adjacent module
        self.assertFalse(debug_data['platform/project4']['direct_input'])
        self.assertTrue(debug_data['platform/project4']['adjacent_input'])
        self.assertFalse(debug_data['platform/project4']['deps_input'])

        # Included via header library
        self.assertFalse(debug_data['platform/project7']['direct_input'])
        self.assertFalse(debug_data['platform/project7']['adjacent_input'])
        self.assertTrue(debug_data['platform/project7']['deps_input'])

        # Included due to the config file
        self.assertEqual(
            debug_data['platform/project6']['manual_add_config'],
            config_file.name)

        # Included due to the Kati makefile stamp
        self.assertEqual(debug_data['vendor/project1']['kati_makefiles'][0],
                         product_makefile)

  @unittest.mock.patch.object(manifest_split, 'get_ninja_inputs', autospec=True)
  @unittest.mock.patch.object(manifest_split, 'get_kati_makefiles', autospec=True)
  @unittest.mock.patch.object(manifest_split.ModuleInfo, '__init__', autospec=True)
  def test_create_split_manifest_skip_kati_module_info(self, mock_init,
                                                       mock_get_kati_makefiles,
                                                       mock_get_ninja_inputs):
    with tempfile.NamedTemporaryFile('w+t') as repo_list_file, \
            tempfile.NamedTemporaryFile('w+t') as manifest_file, \
            tempfile.NamedTemporaryFile('w+t') as module_info_file, \
            tempfile.NamedTemporaryFile('w+t') as config_file, \
            tempfile.NamedTemporaryFile('w+t') as split_manifest_file, \
            tempfile.TemporaryDirectory() as temp_dir:

      os.chdir(temp_dir)

      manifest_file.write("""
        <manifest>
        </manifest>""")
      manifest_file.flush()

      manifest_split.create_split_manifest(
          targets=['droid'],
          manifest_file=manifest_file.name,
          split_manifest_file=split_manifest_file.name,
          config_files=[],
          repo_list_file=repo_list_file.name,
          ninja_build_file='build-target.ninja',
          ninja_binary='ninja',
          kati_stamp_file=None,
          module_info_file=None,
          overlays=[],
          installed_prebuilts=[],
          debug_file=None)

    mock_get_ninja_inputs.assert_called_with(
        'ninja', 'build-target.ninja', ['droid'])
    mock_get_kati_makefiles.assert_not_called()
    mock_init.assert_not_called()

  @unittest.mock.patch.object(subprocess, 'check_output', autospec=True)
  def test_create_split_manifest_installed_prebuilt(self, mock_check_output):

    # The purpose of this test is to verify that create_split_manifests treats
    # installed prebuilts as projects, even though the installed prebuilts are
    # not in the manifest. This use case occurs when installed prebuilts
    # contribute modules to the build, but the installed prebuilts themselves
    # aren't sourced from the manifest.

    with tempfile.NamedTemporaryFile('w+t') as repo_list_file, \
      tempfile.NamedTemporaryFile('w+t') as manifest_file, \
      tempfile.NamedTemporaryFile('w+t') as module_info_file, \
      tempfile.NamedTemporaryFile('w+t') as split_manifest_file, \
      tempfile.TemporaryDirectory() as temp_dir:

      os.chdir(temp_dir)

      repo_list_file.write("""
        system/project1 : platform/project1
        vendor/project1 : vendor/project1""")
      repo_list_file.flush()

      # Here we have small manifest that does not include "prebuilt/project3"
      # or "prebuilt/project4".

      manifest_file.write("""
        <manifest>
          <project name="platform/project1" path="system/project1" />
          <project name="vendor/project1" path="vendor/project1" />
        </manifest>""")
      manifest_file.flush()

      # Here's the module_info.json file. It contains modules whose paths are
      # "prebuilt/project3" and "prebult/project4", which are not found in the
      # manifest. Normally create_split_manifest doesn't tolerate a path that
      # doesn't correspond to a manifest project. However, this test verifies
      # that you can use these modules if you tell create_split_manifest about
      # the installed prebuilts via a parameter.

      module_info_file.write("""{
        "droid": { "class": ["EXECUTABLES"], "path": ["system/project1"], "dependencies": [] },
        "target_a": { "class": ["EXECUTABLES"], "path": ["system/project1"], "dependencies": ["target_b", "target_c"] },
        "target_b": { "class": ["SHARED_LIBRARIES"], "path": ["prebuilt/project3"], "dependencies": [] },
        "target_c": { "class": ["SHARED_LIBRARIES"], "path": ["prebuilt/project4"], "dependencies": [] }
      }""")
      module_info_file.flush()

      # droid needs inputs from project1
      ninja_inputs_droid = b"""
      system/project1/file1
      """

      # target_a needs inputs from prebuilt/project3 and prebuilt/project4
      ninja_inputs_target_a = b"""
      prebuilt/project3/file2
      prebuilt/project4/file3
      """

      # target_b needs inputs from prebuilt/project3
      ninja_inputs_target_b = b"""
      prebuilt/project3/file4
      """

      # target_c needs inputs from prebuilt/project4
      ninja_inputs_target_c = b"""
      prebuilt/project4/file5
      """

      product_makefile = 'vendor/project1/product.mk'
      os.makedirs(os.path.dirname(product_makefile))
      os.mknod(product_makefile)
      kati_stamp_dump = product_makefile.encode()

      mock_check_output.side_effect = [
          ninja_inputs_droid,
          kati_stamp_dump,
          ninja_inputs_target_a,
          ninja_inputs_target_b,
          ninja_inputs_target_c,
      ]

      debug_file = os.path.join(temp_dir, 'debug.json')

      manifest_split.create_split_manifest(
          targets=['droid'],
          manifest_file=manifest_file.name,
          split_manifest_file=split_manifest_file.name,
          config_files=[],
          repo_list_file=repo_list_file.name,
          ninja_build_file='build-target.ninja',
          ninja_binary='ninja',
          module_info_file=module_info_file.name,
          kati_stamp_file='unused kati stamp',
          overlays=['unused overlay'],

          # This is a key part of the test. Passing these two "projects" as
          # prebuilts allows create_split_manifest to recognize them as
          # projects even though they are not in the manifest.

          installed_prebuilts=['prebuilt/project3', 'prebuilt/project4'],

          debug_file = debug_file)

      split_manifest = ET.parse(split_manifest_file.name)

      split_manifest_projects = [
          child.attrib['name']
          for child in split_manifest.getroot().findall('project')
      ]

      # Note that the installed prebuilts do not appear in the final split
      # manfiest output because they were not in the manifest to begin with.

      self.assertEqual(
          split_manifest_projects,
          [
              # From droid
              'platform/project1',
              # Inclusion from the Kati makefile stamp
              'vendor/project1',
          ])

      with open(debug_file) as debug_fp:
        debug_data = json.load(debug_fp)

        # Dependency for droid, but no other adjacent modules
        self.assertTrue(debug_data['platform/project1']['direct_input'])
        self.assertFalse(debug_data['platform/project1']['adjacent_input'])
        self.assertFalse(debug_data['platform/project1']['deps_input'])

        # Included due to the Kati makefile stamp
        self.assertEqual(debug_data['vendor/project1']['kati_makefiles'][0],
                         product_makefile)


if __name__ == '__main__':
  unittest.main()
