#!/bin/bash
# Copyright (C) 2018 The Android Open Source Project
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

if [[ -z "${ANDROID_BUILD_TOP}" ]]; then
    echo "Missing environment variables. Did you run build/envsetup.sh and lunch?" >&2
    exit 1
fi

PROJECT_DIR=external/bouncycastle

PACKAGE_TRANSFORMATIONS="\
    org.bouncycastle:com.android.internal.org.bouncycastle \
"

MODULE_DIRS="\
    bcprov \
    bcpkix \
"
DEFAULT_CONSTRUCTORS_FILE=${BOUNCY_CASTLE_DIR}/srcgen/default-constructors.txt

REPACKAGED_DIR="${ANDROID_BUILD_TOP}/${PROJECT_DIR}/repackaged_platform"

SOURCE_DIRS="\
    src/main/java \
"

SRCGEN_DIR=${PROJECT_DIR}/srcgen_platform

# Repackage the project's source.
source ${ANDROID_BUILD_TOP}/tools/currysrc/scripts/repackage-common.sh

# Remove some unused source files:
rm -fr ${REPACKAGED_DIR}/bcprov/src/main/java/com/android/org/bouncycastle/asn1/ocsp/
rm -fr ${REPACKAGED_DIR}/bcpkix/src/main/java/com/android/org/bouncycastle/cert/ocsp/
rm -fr ${REPACKAGED_DIR}/bcpkix/src/main/java/com/android/org/bouncycastle/cmc/
rm -fr ${REPACKAGED_DIR}/bcpkix/src/main/java/com/android/org/bouncycastle/openssl/
rm -fr ${REPACKAGED_DIR}/bcpkix/src/main/java/com/android/org/bouncycastle/pkix/
