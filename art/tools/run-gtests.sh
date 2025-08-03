#! /bin/bash
#
# Copyright (C) 2019 The Android Open Source Project
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

if [[ $1 = -h ]]; then
  cat <<EOF
Usage: $0 [<gtest>...] [--] [<gtest-option>...]

Script to run gtests located in the ART (Testing) APEX.

If called with arguments, only those tests are run, as specified by their
absolute paths (starting with /apex). All gtests are run otherwise.

Options after \`--\` are passed verbatim to each gtest binary.
EOF
  exit
fi

if [[ -z "$ART_TEST_CHROOT" ]]; then
  echo 'ART_TEST_CHROOT environment variable is empty; please set it before running this script.'
  exit 1
fi

adb="${ADB:-adb}"

android_i18n_root=/apex/com.android.i18n
android_art_root=/apex/com.android.art
android_tzdata_root=/apex/com.android.tzdata

if [[ $1 = -j* ]]; then
  # TODO(b/129930445): Implement support for parallel execution.
  shift
fi

tests=()

while [[ $# -gt 0 ]]; do
  if [[ "$1" == "--" ]]; then
    shift
    break
  fi
  tests+=("$1")
  shift
done

options="$@"

run_in_chroot() {
  if [ -n "$ART_TEST_ON_VM" ]; then
    $ART_SSH_CMD $ART_CHROOT_CMD env ANDROID_ROOT=/system $@
  else
    "$adb" shell chroot "$ART_TEST_CHROOT" $@
  fi
}

if [[ ${#tests[@]} -eq 0 ]]; then
  # Search for executables under the `bin/art` directory of the ART APEX.
  readarray -t tests <<<$(run_in_chroot \
    find "$android_art_root/bin/art" -type f -perm /ugo+x | sort)
fi

maybe_get_fake_dex2oatbootclasspath() {
  if [ -n "$ART_TEST_ON_VM" ]; then
    return
  fi
  dex2oatbootclasspath=$("$adb" shell echo \$DEX2OATBOOTCLASSPATH)
  if [ -n "$dex2oatbootclasspath" ]; then
    # The device has a real DEX2OATBOOTCLASSPATH.
    # This is the usual case.
    return
  fi
  bootclasspath=$("$adb" shell echo \$BOOTCLASSPATH)
  # Construct a fake DEX2OATBOOTCLASSPATH from the elements in BOOTCLASSPATH except the last one.
  # BOOTCLASSPATH cannot be used by the runtime in chroot anyway, so it doesn't hurt to construct a
  # fake DEX2OATBOOTCLASSPATH just to make the runtime happy.
  # This is only needed on old Android platforms such as Android P.
  echo "DEX2OATBOOTCLASSPATH=${bootclasspath%:*}"
}

failing_tests=()

for t in ${tests[@]}; do
  echo "$t"
  run_in_chroot \
    env ANDROID_ART_ROOT="$android_art_root" \
        ANDROID_I18N_ROOT="$android_i18n_root" \
        ANDROID_TZDATA_ROOT="$android_tzdata_root" \
        $(maybe_get_fake_dex2oatbootclasspath) \
        $t $options \
    || failing_tests+=("$t")
done

if [[ -n "$failing_tests" ]]; then
  for t in "${failing_tests[@]}"; do
    echo "Failed test: $t"
  done
  exit 1
fi
