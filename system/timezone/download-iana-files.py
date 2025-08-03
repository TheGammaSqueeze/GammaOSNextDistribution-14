#!/usr/bin/python3 -B

# Copyright 2017 The Android Open Source Project
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

"""Downloads the latest IANA time zone files."""

import argparse
import ftplib
import os
import shutil
import subprocess
import sys

sys.path.append('%s/external/icu/tools' % os.environ.get('ANDROID_BUILD_TOP'))
import i18nutil
import tzdatautil

# Calculate the paths that are referred to by multiple functions.
android_build_top = i18nutil.GetAndroidRootOrDie()
iana_data_dir = os.path.realpath('%s/system/timezone/input_data/iana' % android_build_top)
iana_tools_dir = os.path.realpath('%s/system/timezone/input_tools/iana' % android_build_top)

def FtpRetrieveFile(ftp, filename):
  ftp.retrbinary('RETR %s' % filename, open(filename, 'wb').write)


def CheckSignature(data_filename, signature_filename):
  """Checks the signature of a file."""
  print('Verifying signature of %s using %s...' % (data_filename, signature_filename))
  try:
    subprocess.check_call(['gpg', '--trusted-key=ED97E90E62AA7E34', '--verify',
                          signature_filename, data_filename])
  except subprocess.CalledProcessError as err:
    print('Unable to verify signature')
    print('\n\n******')
    print('If this fails for you, you probably need to import Paul Eggert''s public key:')
    print('  gpg --receive-keys ED97E90E62AA7E34')
    print('******\n\n')
    raise


def FindRemoteTar(ftp, file_prefix, release_version):
  iana_tar_filenames = []

  for filename in ftp.nlst():
    if "/" in filename:
      print("FTP server returned bogus file name")
      sys.exit(1)

    if filename.startswith(file_prefix) and filename.endswith('.tar.gz'):
      iana_tar_filenames.append(filename)

  if release_version is not None:
    if file_prefix.endswith('20'):
      file_prefix = file_prefix[0:-2]
    expected_filename = file_prefix + release_version + '.tar.gz'
    if expected_filename not in iana_tar_filenames:
      print(f'Expected {expected_filename} not found')
      sys.exit(1)
    return expected_filename


  iana_tar_filenames.sort(reverse=True)

  if len(iana_tar_filenames) == 0:
    print('No files found')
    sys.exit(1)

  return iana_tar_filenames[0]


def DownloadAndReplaceLocalFiles(file_prefixes, ftp, local_dir,
                                 release_version):
  output_files = []

  for file_prefix in file_prefixes:
    remote_iana_tar_filename = FindRemoteTar(ftp, file_prefix, release_version)
    local_iana_tar_file = tzdatautil.GetIanaTarFile(local_dir, file_prefix)
    if local_iana_tar_file:
      local_iana_tar_filename = os.path.basename(local_iana_tar_file)
      if remote_iana_tar_filename <= local_iana_tar_filename:
        print('Latest remote file for %s is called %s and is older or the same as'
              ' current local file %s'
              % (local_dir, remote_iana_tar_filename, local_iana_tar_filename))
        continue

    print('Found new %s* file for %s: %s' % (file_prefix, local_dir, remote_iana_tar_filename))
    i18nutil.SwitchToNewTemporaryDirectory()

    print('Downloading file %s...' % remote_iana_tar_filename)
    FtpRetrieveFile(ftp, remote_iana_tar_filename)

    signature_filename = '%s.asc' % remote_iana_tar_filename
    print('Downloading signature %s...' % signature_filename)
    FtpRetrieveFile(ftp, signature_filename)

    CheckSignature(remote_iana_tar_filename, signature_filename)

    new_local_iana_tar_file = '%s/%s' % (local_dir, remote_iana_tar_filename)
    shutil.copyfile(remote_iana_tar_filename, new_local_iana_tar_file)
    new_local_signature_file = '%s/%s' % (local_dir, signature_filename)
    shutil.copyfile(signature_filename, new_local_signature_file)

    output_files.append(new_local_iana_tar_file)
    output_files.append(new_local_signature_file)

    # Delete the existing local IANA tar file, if there is one.
    if local_iana_tar_file:
      os.remove(local_iana_tar_file)

    local_signature_file = '%s.asc' % local_iana_tar_file
    if os.path.exists(local_signature_file):
      os.remove(local_signature_file)

  return output_files

# Run from any directory after having run source/envsetup.sh / lunch
# See http://www.iana.org/time-zones/ for more about the source of this data.
def main():
  parser = argparse.ArgumentParser(description=('Update IANA files from upstream'))
  parser.add_argument('--tools', help='Download tools files', action='store_true')
  parser.add_argument('--data', help='Download data files', action='store_true')
  parser.add_argument('-r', '--release', default=None,
                      dest='release_version',
                      help='Pick a specific release revision instead of '
                      'latest. For example --release 2023a ')
  args = parser.parse_args()
  if not args.tools and not args.data:
    parser.error("Nothing to do")
    sys.exit(1)

  print('Looking for new IANA files...')

  ftp = ftplib.FTP('ftp.iana.org')
  ftp.login()
  ftp.cwd('tz/releases')

  output_files = []
  if args.tools:
    # The tools and data files are kept separate to make the updates independent.
    # This means we duplicate the tzdata20xx file (once in the tools dir, once
    # in the data dir) but the contents of the data tar appear to be needed for
    # the zic build.
    new_files = DownloadAndReplaceLocalFiles(['tzdata20', 'tzcode20'], ftp,
                                             iana_tools_dir,
                                             args.release_version)
    output_files += new_files
  if args.data:
    new_files = DownloadAndReplaceLocalFiles(['tzdata20'], ftp, iana_data_dir,
                                             args.release_version)
    output_files += new_files

  if len(output_files) == 0:
    print('No files updated')
  else:
    print('New files:')
    for output_file in output_files:
      print('  %s' % output_file)


if __name__ == '__main__':
  main()
