# Copyright (C) 2022 The Android Open Source Project
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

import argparse
import os
import subprocess
import sys
import tempfile
import zipfile

def main():
    parser = argparse.ArgumentParser(
        description="This program takes an apks file and outputs a text file containing the " +
        "name of all the libcutils.so files it found in the apex, and their arches.")
    parser.add_argument('--deapexer-path', required=True)
    parser.add_argument('--readelf-path', required=True)
    parser.add_argument('--debugfs-path', required=True)
    parser.add_argument('--fsckerofs-path', required=True)
    parser.add_argument('apks')
    parser.add_argument('output')
    args = parser.parse_args()

    with tempfile.TemporaryDirectory() as d:
        with zipfile.ZipFile(args.apks) as zip:
            zip.extractall(d)
        result = ''
        for name in sorted(os.listdir(os.path.join(d, 'standalones'))):
            extractedDir = os.path.join(d, 'standalones', name+'_extracted')
            subprocess.run([
                args.deapexer_path,
                '--debugfs_path',
                args.debugfs_path,
                '--fsckerofs_path',
                args.fsckerofs_path,
                'extract',
                os.path.join(d, 'standalones', name),
                extractedDir,
            ], check=True)

            result += name + ':\n'
            all_files = []
            for root, _, files in os.walk(extractedDir):
                for f in files:
                    if f == 'libcutils.so':
                        all_files.append(os.path.join(root, f))
            all_files.sort()
            for f in all_files:
                readOutput = subprocess.check_output([
                    args.readelf_path,
                    '-h',
                    f,
                ], text=True)
                arch = [x.strip().removeprefix('Machine:').strip() for x in readOutput.split('\n') if x.strip().startswith('Machine:')]
                if len(arch) != 1:
                    sys.exit(f"Expected 1 arch, got {arch}")
                rel = os.path.relpath(f, extractedDir)
                result += f'  {rel}: {arch[0]}\n'

        with open(args.output, 'w') as f:
            f.write(result)

if __name__ == "__main__":
    main()
