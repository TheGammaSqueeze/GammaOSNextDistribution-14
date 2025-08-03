#!/usr/bin/env bash

# Copyright (C) 2021 The Android Open Source Project
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

set -e

if [ $# != 3 ]; then
  echo "Usage: hash_gen.sh <api_path> <latest-version> <hash_file_path>"
  exit 1
fi

# This adds a new hash to the file if one already exists.
# We can have multiple hashes in a hash file so we can still identify previous
# iterations of this interfaces that were released.
(cd $1 && find ./ -name "*.aidl" -print0 | LC_ALL=C sort -z | xargs -0 sha1sum && echo $2) | sha1sum | cut -d " " -f 1 >> $3
