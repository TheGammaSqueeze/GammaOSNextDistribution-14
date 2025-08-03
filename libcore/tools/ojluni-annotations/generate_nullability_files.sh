#!/bin/bash

# Copyright (C) 2023 The Android Open Source Project
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

# Exit this shell script when a command fails.
set -e

cd "${ANDROID_BUILD_TOP}"
build/soong/soong_ui.bash --make-mode openjdk-sdk-stubs-no-javadoc libart-sdk-stubs-no-javadoc
for CLASS in "$@";
do
  FILE=${CLASS//./\/}
  unzip out/soong/.intermediates/libcore/openjdk-sdk-stubs-no-javadoc/android_common/exportable/openjdk-sdk-stubs-no-javadoc-stubs.srcjar \
    "${FILE}".java -d libcore/ojluni/annotations/sdk/nullability/
  mv libcore/ojluni/annotations/sdk/nullability/"$FILE".java libcore/ojluni/annotations/sdk/nullability/"$FILE".annotated.java
done
