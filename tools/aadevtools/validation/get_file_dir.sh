#!/bin/bash

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

# Get a dictionary of all the file_extension files in a directory, e.g.
# declare -a aDic="$(.\get_file_dir.sh directory file_extension)"
declare -A aDic

dir=$1
ext=$2
if [[ -d ${dir} ]]; then
  cd ${dir} && fList=$(find "${dir}" -name "*.${ext}")

  echo '( \'
  for f in ${fList}; do
    fileName=${f##*/}
    echo "[${fileName}]=\"$f\" \\"
  done
  echo ')'
fi
