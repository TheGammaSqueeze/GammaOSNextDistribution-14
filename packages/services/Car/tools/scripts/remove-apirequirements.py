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

import os

rootDir = os.getenv("ANDROID_BUILD_TOP")

# Method signatures to be removed.
# Currently, only java.lang.Object methods do not need @ApiRequirements or AddedInOrBefore annotations.
methods = {
    "public boolean equals",
    "public int hashCode",
    "public String toString",
    "protected void finalize",
    "protected Object clone"
}

# Checks if the annotated method is a JDK method that should have its annotation removed.
# There could be multiple annotations, so an arbitrary look-ahead of 6 lines was selected.
def is_exempt(lines, index):
    i = index
    while i < len(lines) and i < index + 6:
        for method in methods:
            if method in lines[i]:
                return True
        i += 1
    return False

def delete_text_from_files(directory):
    if not os.path.isdir(directory):
        return

    for file_name in os.listdir(directory):
        file_path = os.path.join(directory, file_name)

        if os.path.isfile(file_path):
            remove_api_requirements(file_path)
            delete_text_from_files(file_path)

        if os.path.isdir(file_path):
            delete_text_from_files(file_path)

def remove_api_requirements(file_path):
    with open(file_path, 'r+') as f:
        lines = f.readlines()
        f.seek(0)

        for index, line in enumerate(lines):
            # List of annotation string matches.
            if any((annotation in line) for annotation in
                   ['@ApiRequirements(', 'minCarVersion', 'minPlatformVersion',
                    '@AddedInOrBefore(majorVersion']):
                if is_exempt(lines, index):
                    continue
            f.write(line)

        f.truncate()

    f.close()

delete_text_from_files(rootDir + "/packages/services/Car/car-lib")