#!/usr/bin/python3
#
# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

import fnmatch
import glob
import os
import shutil
import subprocess
import tempfile
import zipfile


def unzip_otatools(otatools_zip_path, output_dir, patterns=None):
  """Unzip otatools to a directory and set the permissions for execution.

  Args:
    otatools_zip_path: The path to otatools zip archive.
    output_dir: The root directory of the unzip output.
    patterns: If provided, only extract files matching any of these patterns
              from the otatools zip archive; otherwise, extract all files.
  """
  with zipfile.ZipFile(otatools_zip_path, 'r') as zf:
    if patterns is None:
      zf.extractall(path=output_dir)
    else:
      for file in zf.namelist():
        if any(fnmatch.fnmatch(file, p) for p in patterns):
          zf.extract(file, output_dir)

  for f in glob.glob(os.path.join(output_dir, 'bin', '*')):
    os.chmod(f, 0o777)


def _parse_copy_file_pair(copy_file_pair):
  """Convert a string to a source path and a destination path.

  Args:
    copy_file_pair: A string in the format of <src glob pattern>:<dst path>.

  Returns:
    The source path and the destination path.

  Raises:
    ValueError if the input string is in a wrong format.
  """
  split_pair = copy_file_pair.split(':', 1)
  if len(split_pair) != 2:
    raise ValueError(f'{copy_file_pair} is not a <src>:<dst> pair.')
  src_list = glob.glob(split_pair[0])
  if len(src_list) != 1:
    raise ValueError(f'{copy_file_pair} has more than one matched src files: '
                     f'{" ".join(src_list)}.')
  return src_list[0], split_pair[1]


def copy_files(copy_files_list, output_dir):
  """Copy files to the output directory.

  Args:
    copy_files_list: A list of copy file pairs, where a pair defines the src
                     glob pattern and the dst path.
    output_dir: The root directory of the copy dst.

  Raises:
    FileExistsError if the dst file already exists.
  """
  for pair in copy_files_list:
    src, dst = _parse_copy_file_pair(pair)
    # this line does not change dst if dst is absolute.
    dst = os.path.join(output_dir, dst)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    print(f'Copying {src} to {dst}')
    if os.path.exists(dst):
      raise FileExistsError(dst)
    shutil.copyfile(src, dst)


def _extract_cil_files(target_files_zip, output_dir):
  """Extract sepolicy cil files from a target files zip archive.

  Args:
    target_files_zip: A path to the target files zip archive.
    output_dir: The directory of extracted cil files.
  """
  with zipfile.ZipFile(target_files_zip, 'r') as zf:
    cil_files = [name for name in zf.namelist() if name.endswith('.cil')]
    for f in cil_files:
      zf.extract(f, output_dir)


def _get_sepolicy_plat_version(target_files_zip):
  """Get the platform sepolicy version from a vendor target files zip archive.

  Args:
    target_files_zip: A path to the target files zip archive.

  Returns:
    A string that represents the platform sepolicy version.
  """
  with zipfile.ZipFile(target_files_zip, 'r') as zf:
    try:
      with zf.open('VENDOR/etc/selinux/plat_sepolicy_vers.txt') as ver_file:
        return ver_file.readline().decode('utf-8').strip('\n')
    except Exception as error:
      print(f'cannot get platform sepolicy version from {target_files_zip}')
      raise


def merge_chd_sepolicy(framework_target_files_zip, vendor_target_files_zip,
                       otatools_dir, output_dir):
  """Merge the sepolicy files for CHD.

  This function takes both the system and vendor sepolicy files from
  framework_target_files_zip, and merges them with the vendor sepolicy from
  vendor_target_files_zip to generate `chd_merged_sepolicy`.

  In certain instances, a device may possess components that do not put their
  sepolicy rules within the same partition as the components themselves. This
  results in a problem that CHD is missing necessary vendor sepolicy rules
  after the replacement of the device's vendor image with Cuttlefish. As a
  short term solution to resolve this issue, the vendor sepolicy files from
  framework_target_files_zip are additionally merged.

  Args:
    framework_target_files_zip: A path to the framework target files zip
                                archive.
    vendor_target_files_zip: A path to the vendor target files zip archive.
    otatools_dir: The otatools directory.
    output_dir: The output directory for generating a merged sepolicy file.

  Returns:
    The path to the CHD merged sepolicy file.

  Raises:
    FileNotFoundError if any mandatory sepolicy file is missing.
  """
  with (
      tempfile.TemporaryDirectory(prefix='framework_',
                                  dir=output_dir) as framework_dir,
      tempfile.TemporaryDirectory(prefix='vendor_',
                                  dir=output_dir) as vendor_dir,
  ):
    merged_policy = os.path.join(output_dir, 'chd_merged_sepolicy')
    _extract_cil_files(framework_target_files_zip, framework_dir)
    _extract_cil_files(vendor_target_files_zip, vendor_dir)
    plat_ver = _get_sepolicy_plat_version(vendor_target_files_zip)
    print(f'Merging sepolicy files from {framework_target_files_zip} and '
          f'{vendor_target_files_zip}: platform version {plat_ver}.')

    # (partition, path, required)
    system_policy_files = (
        ('system', 'etc/selinux/plat_sepolicy.cil', True),
        ('system', f'etc/selinux/mapping/{plat_ver}.cil', True),
        ('system', f'etc/selinux/mapping/{plat_ver}.compat.cil', False),
        ('system_ext', 'etc/selinux/system_ext_sepolicy.cil', False),
        ('system_ext', f'etc/selinux/mapping/{plat_ver}.cil', False),
        ('system_ext', f'etc/selinux/mapping/{plat_ver}.compat.cil', False),
        ('product', 'etc/selinux/product_sepolicy.cil', False),
        ('product', f'etc/selinux/mapping/{plat_ver}.cil', False),
    )
    vendor_policy_files = (
        ('vendor', 'etc/selinux/vendor_sepolicy.cil', True),
        ('vendor', 'etc/selinux/plat_pub_versioned.cil', True),
        ('odm', 'etc/selinux/odm_sepolicy.cil', False),
    )

    # merge system and vendor policy files from framework_dir with vendor
    # policy files from vendor_dir.
    merge_cmd = [
        os.path.join(otatools_dir, 'bin', 'secilc'),
        '-m', '-M', 'true', '-G', '-N',
        '-o', merged_policy,
        '-f', '/dev/null'
    ]
    policy_dirs_and_files = (
        # For the normal case, we should merge the system policies from
        # framework_dir with the vendor policies from vendor_dir.
        (framework_dir, system_policy_files),
        (vendor_dir, vendor_policy_files),

        # Additionally merging the vendor policies from framework_dir in order
        # to fix the policy misplaced issue.
        # TODO (b/315474132): remove this when all the policies from
        #                     framework_dir are moved to the right partition.
        (framework_dir, vendor_policy_files),
    )
    for policy_dir, policy_files in policy_dirs_and_files:
      for partition, path, required in policy_files:
        policy_file = os.path.join(policy_dir, partition.upper(), path)
        if os.path.exists(policy_file):
          merge_cmd.append(policy_file)
        elif required:
          raise FileNotFoundError(f'{policy_file} does not exist')

    subprocess.run(merge_cmd, check=True)
    return merged_policy
