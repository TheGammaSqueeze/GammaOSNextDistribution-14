#  Copyright (C) 2020 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
"""Repacking tool for Shared Libs APEX testing."""

import argparse
import hashlib
import logging
import os
import shutil
import subprocess
import sys
import tempfile
from zipfile import ZipFile

import apex_build_info_pb2
import apex_manifest_pb2

logger = logging.getLogger(__name__)

def comma_separated_list(arg):
  return arg.split(',')


def parse_args(argv):
  parser = argparse.ArgumentParser(
      description='Repacking tool for Shared Libs APEX testing')

  parser.add_argument('--input', required=True, help='Input file')
  parser.add_argument('--output', required=True, help='Output file')
  parser.add_argument(
      '--key', required=True, help='Path to the private avb key file')
  parser.add_argument(
      '--pk8key',
      required=True,
      help='Path to the private apk key file in pk8 format')
  parser.add_argument(
      '--pubkey', required=True, help='Path to the public avb key file')
  parser.add_argument(
      '--tmpdir', required=True, help='Temporary directory to use')
  parser.add_argument(
      '--x509key',
      required=True,
      help='Path to the public apk key file in x509 format')
  parser.add_argument(
      '--mode', default='strip', choices=['strip', 'sharedlibs'])
  parser.add_argument(
      '--libs',
      default='libc++.so,libsharedlibtest.so',
      type=comma_separated_list,
      help='Libraries to strip/repack. Expects comma separated values.')
  return parser.parse_args(argv)


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
  if verbose:
    logger.info('  Running: \"%s\"', ' '.join(args))
  return subprocess.Popen(args, **kwargs)


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
        'Failed to run command \'{}\' (exit code {}):\n{}'.format(
            args, proc.returncode, output))
  return output


def get_container_files(apex_file_path, tmpdir):
  dir_name = tempfile.mkdtemp(prefix='container_files_', dir=tmpdir)
  with ZipFile(apex_file_path, 'r') as zip_obj:
    zip_obj.extractall(path=dir_name)
  files = {}
  for i in [
      'apex_manifest.json', 'apex_manifest.pb', 'apex_build_info.pb', 'assets',
      'apex_payload.img', 'apex_payload.zip'
  ]:
    file_path = os.path.join(dir_name, i)
    if os.path.exists(file_path):
      files[i] = file_path

  image_file = files.get('apex_payload.img')
  if image_file is None:
    image_file = files.get('apex_payload.zip')

  files['apex_payload'] = image_file

  return files


def extract_payload_from_img(img_file_path, tmpdir):
  dir_name = tempfile.mkdtemp(prefix='extracted_payload_', dir=tmpdir)
  cmd = [
      _get_host_tools_path('debugfs_static'), '-R',
      'rdump ./ %s' % dir_name, img_file_path
  ]
  run_and_check_output(cmd)

  # Remove payload files added by apexer and e2fs tools.
  for i in ['apex_manifest.json', 'apex_manifest.pb']:
    if os.path.exists(os.path.join(dir_name, i)):
      os.remove(os.path.join(dir_name, i))
  if os.path.isdir(os.path.join(dir_name, 'lost+found')):
    shutil.rmtree(os.path.join(dir_name, 'lost+found'))
  return dir_name


def run_apexer(container_files, payload_dir, key_path, pubkey_path, tmpdir):
  apexer_cmd = _get_host_tools_path('apexer')
  cmd = [
      apexer_cmd, '--force', '--include_build_info', '--do_not_check_keyname'
  ]
  cmd.extend([
      '--apexer_tool_path',
      os.path.dirname(apexer_cmd) + ':prebuilts/sdk/tools/linux/bin'
  ])
  cmd.extend(['--manifest', container_files['apex_manifest.pb']])
  if 'apex_manifest.json' in container_files:
    cmd.extend(['--manifest_json', container_files['apex_manifest.json']])
  cmd.extend(['--build_info', container_files['apex_build_info.pb']])
  if 'assets' in container_files:
    cmd.extend(['--assets_dir', container_files['assets']])
  cmd.extend(['--key', key_path])
  cmd.extend(['--pubkey', pubkey_path])

  # Decide on output file name
  apex_suffix = '.apex.unsigned'
  fd, fn = tempfile.mkstemp(prefix='repacked_', suffix=apex_suffix, dir=tmpdir)
  os.close(fd)
  cmd.extend([payload_dir, fn])

  run_and_check_output(cmd)
  return fn


def _get_java_toolchain():
  java_toolchain = 'java'
  if os.path.isfile('prebuilts/jdk/jdk17/linux-x86/bin/java'):
    java_toolchain = 'prebuilts/jdk/jdk17/linux-x86/bin/java'

  java_dep_lib = (
      os.path.join(os.path.dirname(_get_host_tools_path()), 'lib64') + ':' +
      os.path.join(os.path.dirname(_get_host_tools_path()), 'lib'))

  return [java_toolchain, java_dep_lib]


def _get_host_tools_path(tool_name=None):
  # This script is located at e.g.
  # out/host/linux-x86/bin/shared_libs_repack/shared_libs_repack.py.
  # Find the host tools dir by going up two directories.
  dirname = os.path.dirname(os.path.dirname(os.path.realpath(__file__)))
  if tool_name:
    return os.path.join(dirname, tool_name)
  return dirname


def sign_apk_container(unsigned_apex, x509key_path, pk8key_path, tmpdir):
  fd, fn = tempfile.mkstemp(prefix='repacked_', suffix='.apex', dir=tmpdir)
  os.close(fd)
  java_toolchain, java_dep_lib = _get_java_toolchain()

  cmd = [
      java_toolchain, '-Djava.library.path=' + java_dep_lib, '-jar',
      os.path.join(
          os.path.dirname(_get_host_tools_path()), 'framework', 'signapk.jar'),
      '-a', '4096', '--align-file-size', x509key_path, pk8key_path, unsigned_apex, fn
  ]
  run_and_check_output(cmd)
  return fn


def compute_sha512(file_path):
  block_size = 65536
  hashbuf = hashlib.sha512()
  with open(file_path, 'rb') as f:
    fb = f.read(block_size)
    while len(fb) > 0:
      hashbuf.update(fb)
      fb = f.read(block_size)
  return hashbuf.hexdigest()


def parse_fs_config(fs_config):
  configs = fs_config.splitlines()
  # Result is set of configurations.
  # Each configuration is set of items as [file path, uid, gid, mode].
  # All items are stored as string.
  result = []
  for config in configs:
    result.append(config.split())
  return result


def config_to_str(configs):
  result = ''
  for config in configs:
    result += ' '.join(config) + '\n'
  return result


def _extract_lib_or_lib64(payload_dir, lib_full_path):
  # Figure out if this is lib or lib64:
  # Strip out the payload_dir and split by /
  libpath = lib_full_path[len(payload_dir):].lstrip('/').split('/')
  return libpath[0]


def main(argv):
  args = parse_args(argv)
  apex_file_path = args.input

  container_files = get_container_files(apex_file_path, args.tmpdir)
  payload_dir = extract_payload_from_img(container_files['apex_payload.img'],
                                         args.tmpdir)
  libs = args.libs
  assert len(libs)> 0

  lib_paths = [os.path.join(payload_dir, lib_dir, lib)
               for lib_dir in ['lib', 'lib64']
               for lib in libs
               if os.path.exists(os.path.join(payload_dir, lib_dir, lib))]

  assert len(lib_paths) > 0

  lib_paths_hashes = [(lib, compute_sha512(lib)) for lib in lib_paths]

  if args.mode == 'strip':
    # Stripping mode. Add a reference to the version of libc++.so to the
    # requireSharedApexLibs entry in the manifest, and remove lib64/libc++.so
    # from the payload.
    pb = apex_manifest_pb2.ApexManifest()
    with open(container_files['apex_manifest.pb'], 'rb') as f:
      pb.ParseFromString(f.read())
      for lib_path_hash in lib_paths_hashes:
        basename = os.path.basename(lib_path_hash[0])
        libpath = _extract_lib_or_lib64(payload_dir, lib_path_hash[0])
        assert libpath in ('lib', 'lib64')
        pb.requireSharedApexLibs.append(os.path.join(libpath, basename) + ':'
                                        + lib_path_hash[1])
        # Replace existing library with symlink
        symlink_dst = os.path.join('/', 'apex', 'sharedlibs',
                                   libpath, basename, lib_path_hash[1],
                                   basename)
        os.remove(lib_path_hash[0])
        os.system('ln -s {0} {1}'.format(symlink_dst, lib_path_hash[0]))
      #
      # Example of resulting manifest:
      # ---
      # name: "com.android.apex.test.foo"
      # version: 1
      # requireNativeLibs: "libc.so"
      # requireNativeLibs: "libdl.so"
      # requireNativeLibs: "libm.so"
      # requireSharedApexLibs: "lib/libc++.so:23c5dd..."
      # requireSharedApexLibs: "lib/libsharedlibtest.so:870f38..."
      # requireSharedApexLibs: "lib64/libc++.so:72a584..."
      # requireSharedApexLibs: "lib64/libsharedlibtest.so:109015..."
      # --
      # To print uncomment the following:
      # from google.protobuf import text_format
      # print(text_format.MessageToString(pb))
    with open(container_files['apex_manifest.pb'], 'wb') as f:
      f.write(pb.SerializeToString())

  if args.mode == 'sharedlibs':
    # Sharedlibs mode. Mark in the APEX manifest that this package contains
    # shared libraries.
    pb = apex_manifest_pb2.ApexManifest()
    with open(container_files['apex_manifest.pb'], 'rb') as f:
      pb.ParseFromString(f.read())
      del pb.requireNativeLibs[:]
      pb.provideSharedApexLibs = True
    with open(container_files['apex_manifest.pb'], 'wb') as f:
      f.write(pb.SerializeToString())

    pb = apex_build_info_pb2.ApexBuildInfo()
    with open(container_files['apex_build_info.pb'], 'rb') as f:
      pb.ParseFromString(f.read())

    canned_fs_config = parse_fs_config(pb.canned_fs_config.decode('utf-8'))

    # Remove the bin directory from payload dir and from the canned_fs_config.
    shutil.rmtree(os.path.join(payload_dir, 'bin'))
    canned_fs_config = [config for config in canned_fs_config
                        if not config[0].startswith('/bin')]

    # Remove from the canned_fs_config the entries we are about to relocate in
    # different dirs.
    source_lib_paths = [os.path.join('/', libpath, lib)
                        for libpath in ['lib', 'lib64']
                        for lib in libs]
    # We backup the fs config lines for the libraries we are going to relocate,
    # so we can set the same permissions later.
    canned_fs_config_original_lib = {config[0] : config
                                     for config in canned_fs_config
                                     if config[0] in source_lib_paths}

    canned_fs_config = [config for config in canned_fs_config
                        if config[0] not in source_lib_paths]

    # We move any targeted library in lib64/ or lib/ to a directory named
    # /lib64/libNAME.so/${SHA512_OF_LIBCPP}/ or
    # /lib/libNAME.so/${SHA512_OF_LIBCPP}/
    #
    for lib_path_hash in lib_paths_hashes:
      basename = os.path.basename(lib_path_hash[0])
      libpath = _extract_lib_or_lib64(payload_dir, lib_path_hash[0])
      tmp_lib = os.path.join(payload_dir, libpath, basename + '.bak')
      shutil.move(lib_path_hash[0], tmp_lib)
      destdir = os.path.join(payload_dir, libpath, basename, lib_path_hash[1])
      os.makedirs(destdir)
      shutil.move(tmp_lib, os.path.join(destdir, basename))

      canned_fs_config.append(
          ['/' + libpath + '/' + basename, '0', '2000', '0755'])
      canned_fs_config.append(
          ['/' + libpath + '/' + basename + '/' + lib_path_hash[1],
           '0', '2000', '0755'])

      if os.path.join('/', libpath, basename) in canned_fs_config_original_lib:
        config = canned_fs_config_original_lib[os.path.join(
                                                   '/',
                                                   libpath,
                                                   basename)]
        canned_fs_config.append([os.path.join('/', libpath, basename,
                                              lib_path_hash[1], basename),
                                config[1], config[2], config[3]])
      else:
        canned_fs_config.append([os.path.join('/', libpath, basename,
                                              lib_path_hash[1], basename),
                                '1000', '1000', '0644'])

    pb.canned_fs_config = config_to_str(canned_fs_config).encode('utf-8')
    with open(container_files['apex_build_info.pb'], 'wb') as f:
      f.write(pb.SerializeToString())

  try:
    for lib in lib_paths:
      os.rmdir(os.path.dirname(lib))
  except OSError:
    # Directory not empty, that's OK.
    pass

  repack_apex_file_path = run_apexer(container_files, payload_dir, args.key,
                                     args.pubkey, args.tmpdir)

  resigned_apex_file_path = sign_apk_container(repack_apex_file_path,
                                               args.x509key, args.pk8key,
                                               args.tmpdir)

  shutil.copyfile(resigned_apex_file_path, args.output)


if __name__ == '__main__':
  main(sys.argv[1:])
