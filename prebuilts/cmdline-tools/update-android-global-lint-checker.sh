#!/bin/bash -e

# Copyright 2022 Google Inc. All rights reserved.
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

if [ -z $1 ]; then
    echo "usage: $0 <build number>"
    exit 1
fi

readonly BUILD_NUMBER=$1
readonly TARGET=aosp_arm64-trunk_food-userdebug

cd "$(dirname $0)"

if ! git diff HEAD --quiet; then
    echo "must be run with a clean prebuilts/build-tools project"
    exit 1
fi

/google/data/ro/projects/android/fetch_artifact \
  --bid ${BUILD_NUMBER} \
  --target $TARGET \
  AndroidGlobalLintChecker.jar

git add AndroidGlobalLintChecker.jar
git commit -m "Update AndroidGlobalLintChecker to ab/${BUILD_NUMBER}

https://ci.android.com/builds/submitted/${BUILD_NUMBER}/$TARGET/latest

Test: treehugger"
