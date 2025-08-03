#!/usr/bin/env python3

# Copyright (C) 2023 The Android Open Source Project
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

import glob
import json
import os
import shutil
import subprocess
import sys

HEADER_TARGETS = [
    '//libchrome:install_basever',
    '//libchrome:install_buildflag_header',
    '//libchrome:install_header',
]


def gn_desc(target):
    """ Run gn desc on given target and return json output."""
    return json.loads(subprocess.check_output(['gn', 'desc', '--format=json', 'out/Release', target]))


def install_headers(target_dir):
    """ Install headers into target directory. """
    for target in HEADER_TARGETS:
        desc = gn_desc(target)
        install_config = desc[target]['metadata']['_install_config'][0]

        # Make sure install path doesn't have absolute path
        install_path = install_config['install_path'].lstrip('/')
        try:
            relative_to = install_config['tree_relative_to']
        except:
            relative_to = os.path.join(os.getcwd(), 'libchrome')
        sources = install_config['sources']

        # Generate rsync commands for each source mapping. Cp would require
        # running makedir which we don't want to do.
        for source in sources:
            files = glob.glob(source, recursive=True)
            for file in files:
                target_file = os.path.join(target_dir, install_path, os.path.relpath(file, relative_to))
                # Create dirs before copying
                os.makedirs(os.path.dirname(target_file), exist_ok=True)
                shutil.copyfile(file, target_file)


def main():
    if len(sys.argv) != 2:
        raise Exception('Expecting 2 params, got {}'.format(sys.argv))
        return

    install_headers(sys.argv[1])


if __name__ == '__main__':
    main()
