#!/usr/bin/env sh

# Copyright 2022 The Android Open Source Project
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

# Script to test the SysUI-Studio gradle build.
# The script assumes `m` has already run
# TODO: Be more specific about which modules are dependencies

print_error() {
  local RED='\033[0;31m'
  local NC='\033[0m'
  printf "${RED}$1${NC}\n"
}

print_good() {
  local GREEN='\033[0;32m'
  local NC='\033[0m'
  printf "${GREEN}$1${NC}\n"
}

# The default list of Gradle tasks to run if no args are passed
DEFAULT_TASKS=(
  :SystemUI:assemble
  :SystemUILib:assembleAndroidTest
  :ComposeGallery:assemble
  :ComposeGallery:assembleAndroidTest
  :NexusLauncher:assembleGoogleWithQuickstepDebug
  :NexusLauncher:assembleGoogleWithQuickstepDebugAndroidTest
  :WallpaperPickerGoogle:assembleGoogleDebug
  :PlatformScenarioTests:assembleDebug
)

GRADLE_TASKS="$@"
if [[ -z "$GRADLE_TASKS" ]]; then
  GRADLE_TASKS="${DEFAULT_TASKS[@]}"
fi

SCRIPT_DIR="$(cd $(dirname $0) && pwd)"
ANDROID_BUILD_TOP="$(cd "${SCRIPT_DIR}/../../../../../"; pwd)"
STUDIO_DEV_DIR="${ANDROID_BUILD_TOP}/vendor/unbundled_google/packages/SystemUIGoogle/studio-dev"

# The temporary artifacts directory.
GRADLE_BUILD_DIR="${ANDROID_BUILD_TOP}/out/gradle"
mkdir -p "${GRADLE_BUILD_DIR}"

export ANDROID_HOME="${GRADLE_BUILD_DIR}/MockSdk"

# Sets the path to the user preferences directory for tools that are part of the Android SDK.
export ANDROID_USER_HOME="${GRADLE_BUILD_DIR}/.android"

export JAVA_HOME="${ANDROID_BUILD_TOP}/prebuilts/jdk/jdk17/linux-x86"
export PATH="${JAVA_HOME}/bin:${ANDROID_BUILD_TOP}/out/host/linux-x86/bin:$PATH"

"${STUDIO_DEV_DIR}"/studiow --no-download --update-sdk soong || exit $?

export GRADLE_USER_HOME="${GRADLE_BUILD_DIR}/gradle-user-home"

export STUDIO_LAUNCHED_WITH_WRAPPER=true

cd "${STUDIO_DEV_DIR}/SysUIGradleProject"
./gradlew \
    --refresh-dependencies \
    --project-cache-dir="${GRADLE_BUILD_DIR}"/gradle-project-cache \
    $GRADLE_TASKS

return_code=$?

if [ "${return_code}" -eq 0 ]; then
  print_good 'Success'
else
  print_error 'failed to build using gradlew'
fi
exit "${return_code}"
