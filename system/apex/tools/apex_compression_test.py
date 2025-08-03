#!/usr/bin/env python
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
#
"""Unit tests for apex_compression_tool."""
import hashlib
import logging
import os
import shutil
import subprocess
import tempfile
import unittest
from zipfile import ZipFile, ZIP_STORED, ZIP_DEFLATED

import apex_manifest_pb2

logger = logging.getLogger(__name__)

TEST_APEX = 'com.android.example.apex'

# In order to debug test failures, set DEBUG_TEST to True and run the test from
# local workstation bypassing atest, e.g.:
# $ m apex_compression_tool_test && \
#   out/host/linux-x86/nativetest64/apex_compression_tool_test/\
#   apex_compression_tool_test
#
# the test will print out the command used, and the temporary files used by the
# test.
DEBUG_TEST = False


def run(args, verbose=None, **kwargs):
  """Creates and returns a subprocess.Popen object.

  Args:
    args: The command represented as a list of strings.
    verbose: Whether the commands should be shown. Default to the global
        verbosity if unspecified.
    kwargs: Any additional args to be passed to subprocess.Popen(), such as env,
        stdin, etc. stdout and stderr will default to subprocess.PIPE and
        subprocess.STDOUT respectively unless caller specifies any of them.
        universal_newlines will default to True, as most of the users in
        releasetools expect string output.

  Returns:
    A subprocess.Popen object.
  """
  if 'stdout' not in kwargs and 'stderr' not in kwargs:
    kwargs['stdout'] = subprocess.PIPE
    kwargs['stderr'] = subprocess.STDOUT
  if 'universal_newlines' not in kwargs:
    kwargs['universal_newlines'] = True
  if DEBUG_TEST:
    print('\nRunning: \n%s\n' % ' '.join(args))
  # Don't log any if caller explicitly says so.
  if verbose:
    logger.info('  Running: \'%s\'', ' '.join(args))
  return subprocess.Popen(args, **kwargs)


def run_host_command(args, verbose=None, **kwargs):
  host_build_top = os.environ.get('ANDROID_BUILD_TOP')
  if host_build_top:
    host_command_dir = os.path.join(host_build_top,
                                    'out/host/linux-x86/bin')
    args[0] = os.path.join(host_command_dir, args[0])
  return run_and_check_output(args, verbose, **kwargs)


def run_and_check_output(args, verbose=None, **kwargs):
  """Runs the given command and returns the output.

  Args:
    args: The command represented as a list of strings.
    verbose: Whether the commands should be shown. Default to the global
        verbosity if unspecified.
    kwargs: Any additional args to be passed to subprocess.Popen(), such as env,
        stdin, etc. stdout and stderr will default to subprocess.PIPE and
        subprocess.STDOUT respectively unless caller specifies any of them.

  Returns:
    The output string.

  Raises:
    ExternalError: On non-zero exit from the command.
  """

  proc = run(args, verbose=verbose, **kwargs)
  output, _ = proc.communicate()
  if output is None:
    output = ''
  # Don't log any if caller explicitly says so.
  if verbose:
    logger.info('%s', output.rstrip())
  if proc.returncode != 0:
    raise RuntimeError(
        "Failed to run command '{}' (exit code {}):\n{}".format(
            args, proc.returncode, output))
  return output


def get_current_dir():
  """Returns the current dir, relative to the script dir."""
  # The script dir is the one we want, which could be different from pwd.
  current_dir = os.path.dirname(os.path.realpath(__file__))
  return current_dir


def get_sha1sum(file_path):
  h = hashlib.sha256()

  with open(file_path, 'rb') as file:
    while True:
      # Reading is buffered, so we can read smaller chunks.
      chunk = file.read(h.block_size)
      if not chunk:
        break
      h.update(chunk)

  return h.hexdigest()


class ApexCompressionTest(unittest.TestCase):
  def setUp(self):
    self._to_cleanup = []

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

  def _run_apex_compression_tool(self, args):
    cmd = ['apex_compression_tool']
    host_build_top = os.environ.get('ANDROID_BUILD_TOP')
    if host_build_top:
      os.environ['APEX_COMPRESSION_TOOL_PATH'] = (
          os.path.join(host_build_top, 'out/host/linux-x86/bin')
          + ':' + os.path.join(host_build_top, 'prebuilts/sdk/tools/linux/bin'))
    else:
      os.environ['APEX_COMPRESSION_TOOL_PATH'] = os.path.dirname(
        shutil.which('apex_compression_tool'))
    cmd.extend(args)
    run_host_command(cmd, True)

  def _get_container_files(self, apex_file_path):
    dir_name = tempfile.mkdtemp(
        prefix=self._testMethodName + '_container_files_')
    self._to_cleanup.append(dir_name)
    with ZipFile(apex_file_path, 'r') as zip_obj:
      zip_obj.extractall(path=dir_name)
    files = {}
    for i in ['apex_manifest.json', 'apex_manifest.pb', 'apex_pubkey',
              'apex_build_info.pb', 'apex_payload.img', 'apex_payload.zip',
              'AndroidManifest.xml', 'original_apex']:
      file_path = os.path.join(dir_name, i)
      if os.path.exists(file_path):
        files[i] = file_path

    image_file = files.get('apex_payload.img', None)
    if image_file is None:
      image_file = files.get('apex_payload.zip', None)
    else:
      files['apex_payload'] = image_file
      # Also retrieve the root digest of the image
      avbtool_cmd = ['avbtool',
        'print_partition_digests', '--image', files['apex_payload']]
      # avbtool_cmd output has format "<name>: <value>"
      files['digest'] = run_host_command(
        avbtool_cmd, True).split(': ')[1].strip()

    return files

  def _get_manifest_string(self, manifest_path):
    cmd = ['conv_apex_manifest']
    cmd.extend([
        'print',
        manifest_path
    ])
    return run_host_command(cmd, 'True')

  # Mutates the manifest located at |manifest_path|
  def _unset_original_apex_digest(self, manifest_path):
    # Open the protobuf
    with open(manifest_path, 'rb') as f:
      pb = apex_manifest_pb2.ApexManifest()
      pb.ParseFromString(f.read())
    pb.ClearField('capexMetadata')
    with open(manifest_path, 'wb') as f:
      f.write(pb.SerializeToString())

  def _compress_apex(self, uncompressed_apex_fp):
    """Returns file path to compressed APEX"""
    fd, compressed_apex_fp = tempfile.mkstemp(
        prefix=self._testMethodName + '_compressed_',
        suffix='.capex')
    os.close(fd)
    self._to_cleanup.append(compressed_apex_fp)
    self._run_apex_compression_tool([
        'compress',
        '--input', uncompressed_apex_fp,
        '--output', compressed_apex_fp
    ])
    return compressed_apex_fp

  def _decompress_apex(self, compressed_apex_fp):
    """Returns file path to decompressed APEX"""
    decompressed_apex_fp = tempfile. \
      NamedTemporaryFile(prefix=self._testMethodName + '_decompressed_',
                         suffix='.apex').name
    # Use deapexer to decompress
    cmd = ['deapexer']
    cmd.extend([
        'decompress',
        '--input', compressed_apex_fp,
        '--output', decompressed_apex_fp
    ])
    run_host_command(cmd, True)

    self.assertTrue(os.path.exists(decompressed_apex_fp),
                    'Decompressed APEX does not exist')
    self._to_cleanup.append(decompressed_apex_fp)
    return decompressed_apex_fp

  def _get_type(self, apex_file_path):
    cmd = ['deapexer', 'info', '--print-type', apex_file_path]
    return run_host_command(cmd, True).strip()

  def test_compression(self):
    uncompressed_apex_fp = os.path.join(get_current_dir(), TEST_APEX + '.apex')
    # TODO(samiul): try compressing a compressed APEX
    compressed_apex_fp = self._compress_apex(uncompressed_apex_fp)

    # Verify output file has been created and is smaller than input file
    uncompressed_file_size = os.path.getsize(uncompressed_apex_fp)
    compressed_file_size = os.path.getsize(compressed_apex_fp)
    self.assertGreater(compressed_file_size, 0, 'Compressed APEX is empty')
    self.assertLess(compressed_file_size, uncompressed_file_size,
                    'Compressed APEX is not smaller than uncompressed APEX')

    # Verify type of the apex is 'COMPRESSED'
    self.assertEqual(self._get_type(compressed_apex_fp), 'COMPRESSED')

    # Verify the contents of the compressed apex files
    content_in_compressed_apex = self._get_container_files(compressed_apex_fp)
    self.assertIsNotNone(content_in_compressed_apex['original_apex'])
    content_in_uncompressed_apex = self._get_container_files(
        uncompressed_apex_fp)
    self.assertIsNotNone(content_in_uncompressed_apex['apex_payload'])
    self.assertIsNotNone(content_in_uncompressed_apex['digest'])

    # Verify that CAPEX manifest contains digest of original_apex
    manifest_string = self._get_manifest_string(
        content_in_compressed_apex['apex_manifest.pb'])
    self.assertIn('originalApexDigest: "'
         + content_in_uncompressed_apex['digest'] + '"', manifest_string)

    for i in ['apex_manifest.json', 'apex_manifest.pb', 'apex_pubkey',
              'apex_build_info.pb', 'AndroidManifest.xml']:
      if i in content_in_uncompressed_apex:
        if i == 'apex_manifest.pb':
          # Get rid of originalApexDigest field, which should be the
          # only difference
          self._unset_original_apex_digest(content_in_compressed_apex[i])
        self.assertEqual(get_sha1sum(content_in_compressed_apex[i]),
                         get_sha1sum(content_in_uncompressed_apex[i]))

  def test_decompression(self):
    # setup: create compressed APEX
    uncompressed_apex_fp = os.path.join(get_current_dir(), TEST_APEX + '.apex')
    compressed_apex_fp = self._compress_apex(uncompressed_apex_fp)

    # Decompress it
    decompressed_apex_fp = self._decompress_apex(compressed_apex_fp)

    # Verify type of the apex is 'UNCOMPRESSED'
    self.assertEqual(self._get_type(decompressed_apex_fp), 'UNCOMPRESSED')

    # Verify decompressed APEX is same as uncompressed APEX
    self.assertEqual(get_sha1sum(uncompressed_apex_fp),
                     get_sha1sum(decompressed_apex_fp),
                     'Decompressed APEX is not same as uncompressed APEX')

    # Try decompressing uncompressed APEX. It should not work.
    with self.assertRaises(RuntimeError) as error:
      self._decompress_apex(uncompressed_apex_fp)

    self.assertIn(uncompressed_apex_fp
                  + ' is not a compressed APEX', str(error.exception))

  def test_only_original_apex_is_compressed(self):
    uncompressed_apex_fp = os.path.join(get_current_dir(), TEST_APEX + '.apex')
    compressed_apex_fp = self._compress_apex(uncompressed_apex_fp)

    with ZipFile(compressed_apex_fp, 'r') as zip_obj:
      self.assertEqual(zip_obj.getinfo('original_apex').compress_type,
                       ZIP_DEFLATED)
      content_in_uncompressed_apex = self._get_container_files(
          uncompressed_apex_fp)
      for i in ['apex_manifest.json', 'apex_manifest.pb', 'apex_pubkey',
                'apex_build_info.pb', 'AndroidManifest.xml']:
        if i in content_in_uncompressed_apex:
          self.assertEqual(zip_obj.getinfo(i).compress_type, ZIP_STORED)

if __name__ == '__main__':
  unittest.main(verbosity=2)
