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
  if [[ "${CLASS:0:5}" == "java." || "${CLASS:0:6}" == "javax." || "${CLASS:0:4}" == "sun." \
      || "${CLASS:0:4}" == "jdk." || "${CLASS:0:8}" == "com.sun." ]];
  then
    unzip out/soong/.intermediates/libcore/openjdk-sdk-stubs-no-javadoc/android_common/exportable/openjdk-sdk-stubs-no-javadoc-stubs.srcjar \
      "${FILE}".java -d libcore/ojluni/annotations/flagged_api/
    mv libcore/ojluni/annotations/flagged_api/"$FILE".java libcore/ojluni/annotations/flagged_api/"$FILE".annotated.java
  else
    unzip out/soong/.intermediates/libcore/libart-sdk-stubs-no-javadoc/android_common/exportable/libart-sdk-stubs-no-javadoc-stubs.srcjar \
      "${FILE}".java -d libcore/luni/annotations/flagged_api/
    mv libcore/luni/annotations/flagged_api/"$FILE".java libcore/luni/annotations/flagged_api/"$FILE".annotated.java
  fi
done
