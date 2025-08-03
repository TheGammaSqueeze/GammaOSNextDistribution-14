#!/usr/bin/env python3
# Copyright 2022, The Android Open Source Project
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

"""Script of Atest Profiler."""

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

# This is mostly a copy of Soong's stub_template_host.txt, but with changes to
# run soong python executables via a profiler tool. This is just a hack, soong
# should really have the ability to build profiled binaries directly.


def main():
  """Main method that runs python profilers."""
  parser = argparse.ArgumentParser(
      description='Runs a soong-built python binary under a profiler.'
  )
  parser.add_argument(
      'profiler',
      choices=['pyinstrument', 'cProfile'],
      help='The profiler to use',
  )
  parser.add_argument('profile_file', help='The output file of the profiler')
  parser.add_argument(
      'executable',
      help='The soong-built python binary (with embedded_launcher: false)',
  )
  args, args_for_executable = parser.parse_known_args()

  if not os.path.isfile(args.executable):
    sys.exit(f'{args.executable}: File not found')
  os.makedirs(os.path.dirname(args.profile_file), exist_ok=True)

  runfiles_path = tempfile.mkdtemp(prefix='Soong.python_')
  try:
    _zf = zipfile.ZipFile(args.executable)
    _zf.extractall(runfiles_path)
    _zf.close()

    sys.exit(
        subprocess.call(
            [
                'python3',
                '-m',
                args.profiler,
                '-o',
                args.profile_file,
                os.path.join(
                    runfiles_path, '__soong_entrypoint_redirector__.py'
                ),
            ]
            + args_for_executable,
            close_fds=False,
        )
    )

  finally:
    shutil.rmtree(runfiles_path, ignore_errors=True)


if __name__ == '__main__':
  main()
