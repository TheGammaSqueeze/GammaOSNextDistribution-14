#!/bin/bash
#
# Copyright 2023 Google Inc. All rights reserved.
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

set -euo pipefail

# This test asserts that the android_app under test does not package ndk libs
#
# Setup
# android_app: SimpleJni
# jni_libs: libsimplejni
# transitive_dep_of_jni_lib: liblog (an NDK library)
#
# Expectation
# SimpleJni.apk contains libsimplejni.so
# SimpleJni.apk does not contain libglog.so

unsigned_apk=$(find ${RUNFILES_DIR} -name *_unsigned.apk)

# check that the apk contains libsimplejni.so
if ! [[ $(unzip -l ${unsigned_apk} | grep lib/.*libsimplejni.so ) ]]; then
  echo "Could not find libsimplejni.so in SimpleJni's apk file: ${unsigned_apk}"
  exit 1
fi

# check that the apk does not liblog.so
if [[ $(unzip -l ${unsigned_apk} | grep lib/.*liblog.so ) ]]; then
  echo "Found liblog.so in SimpleJni's apk file: ${unsigned_apk}. Since this is an NDK library, it should not be included."
  exit 1
fi
