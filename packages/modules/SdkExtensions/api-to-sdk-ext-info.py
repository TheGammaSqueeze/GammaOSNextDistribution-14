#!/usr/bin/env python
#
# Copyright (C) 2022 The Android Open Source Project
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
"""Command line tool to convert API files to sdk-extensions-info.

Example use:

  $ ./api-to-sdk-ext-info.py $(gettop)/prebuilts/sdk/extensions/1/public/api/framework-sdkextensions.txt >/tmp/1.txt
  $ ./api-to-sdk-ext-info.py $(gettop)/prebuilts/sdk/extensions/2/public/api/framework-sdkextensions.txt >/tmp/2.txt
  $ diff /tmp/{1,2}.txt
  0a1,2
  > android.os.ext.SdkExtensions.getAllExtensionVersions
  > android.os.ext.SdkExtensions.getExtensionVersion
"""

import re
import sys

re_package = re.compile(r"^package (.*?) \{(.*?)^\}", re.M + re.S)
re_class = re.compile(r"^  .*? (?:class|interface) (\S+) .*?\{(.*?)^  \}", re.M + re.S)
re_method = re.compile("^    (?:ctor|method).* (\S+)\([^)]*\);", re.M)
re_field = re.compile(r"^    field.* (\S+)(?: =.*);", re.M)


def parse_class(package_name, class_name, contents):
    def print_members(regex, contents):
        # sdk-extensions-info ignores method signatures: overloaded methods are
        # collapsed into the same item; use a set to get this behaviour for free
        members = set()
        for symbol_name in regex.findall(contents):
            members.add(f"{package_name}.{class_name}.{symbol_name}")
        if len(members) > 0:
            print("\n".join(sorted(members)))

    print_members(re_field, contents)
    print_members(re_method, contents)


def parse_package(package_name, contents):
    for match in re_class.findall(contents):
        parse_class(package_name, match[0], match[1])


def main():
    with open(sys.argv[1]) as f:
        contents = f.read()

    if contents.splitlines()[0] != "// Signature format: 2.0":
        raise "unexpected file format"
    for match in re_package.findall(contents):
        parse_package(match[0], match[1])


if __name__ == "__main__":
    main()
