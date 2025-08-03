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
#

"""
Creates new kernel configs for the next compatibility matrix.
"""

import argparse
import datetime
import os
import shutil
import subprocess

def check_call(*args, **kwargs):
    print(args[0])
    subprocess.check_call(*args, **kwargs)

def replace_configs_module_name(current_release, new_release, file_path):
    check_call("sed -i'' -E 's/\"kernel_config_{}_([0-9.]*)\"/\"kernel_config_{}_\\1\"/g' {}"
                .format(current_release, new_release, file_path), shell=True)

class Bump(object):
    def __init__(self, cmdline_args):
        top = os.environ["ANDROID_BUILD_TOP"]
        self.current_release = cmdline_args.current
        self.new_release = cmdline_args.next
        self.configs_dir = os.path.join(top, "kernel/configs")
        self.current_release_dir = os.path.join(self.configs_dir, self.current_release)
        self.new_release_dir = os.path.join(self.configs_dir, self.new_release)
        self.versions = [e for e in os.listdir(self.current_release_dir) if e.startswith("android-")]

    def run(self):
        shutil.copytree(self.current_release_dir, self.new_release_dir)
        for version in self.versions:
            dst = os.path.join(self.new_release_dir, version)
            for file_name in os.listdir(dst):
                abs_path = os.path.join(dst, file_name)
                if not os.path.isfile(abs_path):
                    continue
                year = datetime.datetime.now().year
                check_call("sed -i'' -E 's/Copyright \\(C\\) [0-9]{{4,}}/Copyright (C) {}/g' {}".format(year, abs_path), shell=True)
                replace_configs_module_name(self.current_release, self.new_release, abs_path)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('current', type=str, help='name of the current version (e.g. v)')
    parser.add_argument('next', type=str, help='name of the next version (e.g. w)')
    cmdline_args = parser.parse_args()

    Bump(cmdline_args).run()

if __name__ == '__main__':
    main()
