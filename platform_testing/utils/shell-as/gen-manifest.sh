#!/bin/sh

# Copyright (C) 2023 The Android Open Source Project
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

# Generates an AndroidManifest.xml file from a template by replacing the line
# containing the substring, 'PERMISSIONS', with a list of permissions defined in
# another text file.

set -e

if [ "$#" != 3 ];
then
  echo "usage: gen-manifest.sh AndroidManifest.xml.template" \
    "permissions.txt AndroidManifest.xml"
  exit 1
fi

readonly template="$1"
readonly permissions="$2"
readonly output="$3"

echo "template = $1"

# Print the XML template file before the line containing PERMISSIONS.
sed -e '/PERMISSIONS/,$d' "$template" > "$output"

# Print the permissions formatted as XML.
sed -r 's!(.*)!  <uses-permission android:name="\1"/>!g' "$permissions" >> "$output"

# Print the XML template file after the line containing PERMISSIONS.
sed -e '1,/PERMISSIONS/d' "$template" >> "$output"
