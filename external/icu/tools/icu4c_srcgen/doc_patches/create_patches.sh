#!/usr/bin/env bash

# Copyright (C) 2017 The Android Open Source Project
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

# Re-generate all doc patches
source $(dirname $BASH_SOURCE)/common.sh

read -p "Discard all uncommitted changes in libicu/ (y/n)? " CONT
if [ "$CONT" != "y" ]; then
     exit 1
fi

# Create libicu/ copy without patches
cd ${ICU_DIR}
git checkout -- ${LIBICU_DIR}
if [ -d "${PATCHES_DIR}" ]; then
  rm -r ${PATCHES_DIR} 2> /dev/null
fi
cd ${SRCGEN_DIR}
./generate_ndk.py

# Create patches
cd ${LIBICU_DIR}
GIT_EXTERNAL_DIFF=${SCRIPT_DIR}/git_diff_create_patch.sh git diff -- ${LIBICU_DIR}

# Revert the changes in libicu/
git checkout -- ${LIBICU_DIR}
