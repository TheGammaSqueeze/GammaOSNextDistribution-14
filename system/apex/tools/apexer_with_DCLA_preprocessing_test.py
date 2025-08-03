#!/usr/bin/env python3
#
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

"""Unit tests for apexer_with_DCLA_preprocessing."""
import hashlib
import logging
import os
import shutil
import stat
import subprocess
import tempfile
from typing import List
import unittest
import zipfile

TEST_PRIVATE_KEY = os.path.join('testdata', 'com.android.example.apex.pem')
TEST_APEX = 'com.android.example.apex'

# In order to debug test failures, set DEBUG_TEST to True and run the test from
# local workstation bypassing atest, e.g.:
# $ m apexer_with_DCLA_preprocessing_test && \
#   out/host/linux-x86/nativetest64/apexer_with_DCLA_preprocessing_test/\
#   apexer_with_DCLA_preprocessing_test
#
# the test will print out the command used, and the temporary files used by the
# test.
DEBUG_TEST = False

def get_current_dir():
  """returns the current dir, relative to the script dir."""
  # The script dir is the one we want, which could be different from pwd.
  current_dir = os.path.dirname(os.path.realpath(__file__))
  return current_dir

# TODO: consolidate these common test utilities into a common python_library_host
# to be shared across tests under system/apex
def run_command(cmd: List[str]) -> None:
  """Run a command."""
  try:
    if DEBUG_TEST:
      cmd_str = ' '.join(cmd)
      print(f'\nRunning: \n{cmd_str}\n')
    res = subprocess.run(
        cmd,
        check=True,
        stdout=subprocess.PIPE,
        universal_newlines=True,
        stderr=subprocess.PIPE)
  except subprocess.CalledProcessError as err:
    print(err.stderr)
    print(err.output)
    raise err

def get_apexer_with_DCLA_preprocessing() -> str:
  tool_binary = os.path.join(get_current_dir(), 'apexer_with_DCLA_preprocessing')
  if not os.path.isfile(tool_binary):
    raise FileNotFoundError(f'cannot find tooling apexer with DCLA preprocessing')
  else:
    os.chmod(tool_binary, stat.S_IRUSR | stat.S_IXUSR);
    return tool_binary

def get_host_tool(tool_name: str) -> str:
  """get host tools."""
  tool_binary = os.path.join(get_current_dir(), 'bin', tool_name)
  if not os.path.isfile(tool_binary):
    host_build_top = os.environ.get('ANDROID_BUILD_TOP')
    if host_build_top:
      host_command_dir = os.path.join(host_build_top, 'out/host/linux-x86/bin')
      tool_binary = os.path.join(host_command_dir, tool_name)
      if not os.path.isfile(tool_binary):
        host_command_dir = os.path.join(host_build_top, 'prebuilts/sdk/current/public')
        tool_binary = os.path.join(host_command_dir, tool_name)
    else:
      tool_binary = shutil.which(tool_name)

  if not tool_binary or not os.path.isfile(tool_binary):
    raise FileNotFoundError(f'cannot find tooling {tool_name}')
  else:
    return tool_binary

def get_digest(file_path: str) -> str:
  """Get sha512 digest of a file """
  digester = hashlib.sha512()
  with open(file_path, 'rb') as f:
    bytes_to_digest = f.read()
    digester.update(bytes_to_digest)
    return digester.hexdigest()

class ApexerWithDCLAPreprocessingTest(unittest.TestCase):

  def setUp(self):
    self._to_cleanup = []
    tools_zip_file = os.path.join(get_current_dir(), 'apexer_test_host_tools.zip')
    self.unzip_host_tools(tools_zip_file)

  def tearDown(self):
    if not DEBUG_TEST:
      for i in self._to_cleanup:
        if os.path.isdir(i):
          shutil.rmtree(i, ignore_errors=True)
        else:
          os.remove(i)
      del self._to_cleanup[:]
    else:
      print('Cleanup: ' + str(self._to_cleanup))

  def create_temp_dir(self) -> str:
    tmp_dir = tempfile.mkdtemp()
    self._to_cleanup.append(tmp_dir)
    return tmp_dir

  def expand_apex(self, apex_file: str) -> None:
    """expand an apex file include apex_payload."""
    apex_dir = self.create_temp_dir()
    with zipfile.ZipFile(apex_file, 'r') as apex_zip:
      apex_zip.extractall(apex_dir)
    payload_img = os.path.join(apex_dir, 'apex_payload.img')
    extract_dir = os.path.join(apex_dir, 'payload_extract')
    os.mkdir(extract_dir)
    debugfs = get_host_tool('debugfs_static')
    run_command([debugfs, payload_img, '-R', f'rdump / {extract_dir}'])

    # remove /etc and /lost+found and /payload_extract/apex_manifest.pb
    lost_and_found = os.path.join(extract_dir, 'lost+found')
    etc_dir = os.path.join(extract_dir, 'etc')
    os.remove(os.path.join(extract_dir, 'apex_manifest.pb'))
    if os.path.isdir(lost_and_found):
      shutil.rmtree(lost_and_found)
    if os.path.isdir(etc_dir):
      shutil.rmtree(etc_dir)

    return apex_dir

  def unzip_host_tools(self, host_tools_file_path: str) -> None:
    dir_name = get_current_dir()
    if os.path.isfile(host_tools_file_path):
      with zipfile.ZipFile(host_tools_file_path, 'r') as zip_obj:
        zip_obj.extractall(dir_name)

    for i in ["apexer", "deapexer", "avbtool", "mke2fs", "sefcontext_compile", "e2fsdroid",
      "resize2fs", "soong_zip", "aapt2", "merge_zips", "zipalign", "debugfs_static",
      "signapk.jar", "android.jar"]:
      file_path = os.path.join(dir_name, "bin", i)
      if os.path.exists(file_path):
        os.chmod(file_path, stat.S_IRUSR | stat.S_IXUSR);

  def test_DCLA_preprocessing(self):
    """test DCLA preprocessing done properly."""
    apex_file = os.path.join(get_current_dir(), TEST_APEX + '.apex')
    apex_dir = self.expand_apex(apex_file)

    # create apex canned_fs_config file, TEST_APEX does not come with one
    canned_fs_config_file = os.path.join(apex_dir, 'canned_fs_config')
    with open(canned_fs_config_file, 'w') as f:
      # add /lib/foo.so file
      lib_dir = os.path.join(apex_dir, 'payload_extract', 'lib')
      os.makedirs(lib_dir)
      foo_file = os.path.join(lib_dir, 'foo.so')
      with open(foo_file, 'w') as lib_file:
        lib_file.write('This is a placeholder lib file.')
      foo_digest = get_digest(foo_file)

      # add /lib dir and /lib/foo.so in canned_fs_config
      f.write(f'/lib 0 2000 0755\n')
      f.write(f'/lib/foo.so 1000 1000 0644\n')

      # add /lib/bar.so file
      lib_dir = os.path.join(apex_dir, 'payload_extract', 'lib64')
      os.makedirs(lib_dir)
      bar_file = os.path.join(lib_dir, 'bar.so')
      with open(bar_file, 'w') as lib_file:
        lib_file.write('This is another placeholder lib file.')
      bar_digest = get_digest(bar_file)

      # add /lib dir and /lib/foo.so in canned_fs_config
      f.write(f'/lib64 0 2000 0755\n')
      f.write(f'/lib64/bar.so 1000 1000 0644\n')

      f.write(f'/ 0 2000 0755\n')
      f.write(f'/apex_manifest.pb 1000 1000 0644\n')

    # call apexer_with_DCLA_preprocessing
    manifest_file = os.path.join(apex_dir, 'apex_manifest.pb')
    build_info_file = os.path.join(apex_dir, 'apex_build_info.pb')
    key_file = os.path.join(get_current_dir(), TEST_PRIVATE_KEY)
    apexer= get_host_tool('apexer')
    apexer_wrapper = get_apexer_with_DCLA_preprocessing()
    android_jar = get_host_tool('android.jar')
    apex_out = os.path.join(apex_dir, 'DCLA_preprocessed_output.apex')
    run_command([apexer_wrapper,
                 '--apexer', apexer,
                 '--canned_fs_config', canned_fs_config_file,
                 os.path.join(apex_dir, 'payload_extract'),
                 apex_out,
                 '--',
                 '--android_jar_path', android_jar,
                 '--apexer_tool_path', os.path.dirname(apexer),
                 '--key', key_file,
                 '--manifest', manifest_file,
                 '--build_info', build_info_file,
                 '--payload_fs_type', 'ext4',
                 '--payload_type', 'image',
                 '--force'
                 ])

    # check the existence of updated canned_fs_config
    updated_canned_fs_config = os.path.join(apex_dir, 'updated_canned_fs_config')
    self.assertTrue(
        os.path.isfile(updated_canned_fs_config),
        'missing updated canned_fs_config file named updated_canned_fs_config')

    # check the resulting apex, it should have /lib/foo.so/<hash>/foo.so and
    # /lib64/bar.so/<hash>/bar.so
    result_apex_dir = self.expand_apex(apex_out)
    replaced_foo = os.path.join(
        result_apex_dir, f'payload_extract/lib/foo.so/{foo_digest}/foo.so')
    replaced_bar = os.path.join(
        result_apex_dir, f'payload_extract/lib64/bar.so/{bar_digest}/bar.so')
    self.assertTrue(
        os.path.isfile(replaced_foo),
        f'expecting /lib/foo.so/{foo_digest}/foo.so')
    self.assertTrue(
        os.path.isfile(replaced_bar),
        f'expecting /lib64/bar.so/{bar_digest}/bar.so')

if __name__ == '__main__':
  unittest.main(verbosity=2)
