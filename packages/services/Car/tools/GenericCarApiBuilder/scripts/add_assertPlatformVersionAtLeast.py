#  Copyright (C) 2023 The Android Open Source Project
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

# This script will find the instances where assertPlatformVersionAtLeast does not exist or has the wrong version and add the correct version.
# Usage: python add_assertPlatformVersionAtLeast.py /ssd/udc-dev

import os
import sys
import subprocess

platformVersion_import_statement = "import static com.android.car.internal.util.VersionUtils.assertPlatformVersionAtLeastU;\n"

rootDir = os.getenv("ANDROID_BUILD_TOP")
if rootDir is None or rootDir == "":
    # env variable is not set. Then use the arg passed as Git root
    rootDir = sys.argv[1]

java_cmd= "java -jar " + rootDir + "/packages/services/Car/tools/GenericCarApiBuilder" \
                                  "/GenericCarApiBuilder.jar " \
                                  "--platform-version-assertion-check " \
                                  "--root-dir " + rootDir

output = subprocess.check_output(java_cmd, shell=True).decode('utf-8').strip().split("\n")

import_added = []
new_lines = dict()

apis = dict()

for api in output:
    tokens = api.strip().split("|")
    line_number, filepath = int(tokens[1].strip()), tokens[2].strip()

    if filepath not in apis:
        apis[filepath] = []

    apis[filepath].append(line_number)


for filepath, line_numbers in apis.items():
    apis[filepath] = sorted(apis[filepath])

for filepath, api_line_numbers in apis.items():
    for line_number in api_line_numbers:
        # Check how much we need to offset the new_lines by
        if filepath not in new_lines:
            new_lines[filepath] = 0

        new_lines[filepath] += 1

        line_number = line_number + new_lines[filepath] - 2

        with open(filepath, "r") as f:
            lines = f.readlines()

        # Add assertPlatformVersionAtLeastU wherever it is missing.
        line = lines[line_number]
        indent_spaces = len(line) - len(line.lstrip())
        lines.insert(line_number, indent_spaces * " " + "assertPlatformVersionAtLeastU();\n")

        for i, line in enumerate(lines):
            if line.startswith("import") and filepath not in import_added:
                lines.insert(i, platformVersion_import_statement)
                import_added.append(filepath)
                new_lines[filepath] += 1
                break

        with open(filepath, "w") as file:
            for line in lines:
                file.write(line)
