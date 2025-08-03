#!/bin/bash
#
# Copyright (C) 2023 The Android Open Source Project
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

set -e

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

BOOT_A_NAME="boot_a"
BOOT_A_SIZE_KB="8"
BOOT_A_PART_FILE="${SCRIPT_DIR}/${BOOT_A_NAME}.bin"

BOOT_B_NAME="boot_b"
BOOT_B_SIZE_KB="12"
BOOT_B_PART_FILE="${SCRIPT_DIR}/${BOOT_B_NAME}.bin"

dd if=/dev/urandom bs=1024 count=${BOOT_A_SIZE_KB} > ${BOOT_A_PART_FILE}
dd if=/dev/urandom bs=1024 count=${BOOT_B_SIZE_KB} > ${BOOT_B_PART_FILE}

python3 ${SCRIPT_DIR}/../../tools/gen_gpt_disk.py ${SCRIPT_DIR}/gpt_test.bin 64K \
    --partition "${BOOT_A_NAME},${BOOT_A_SIZE_KB}k,${BOOT_A_PART_FILE}" \
    --partition "${BOOT_B_NAME},${BOOT_B_SIZE_KB}k,${BOOT_B_PART_FILE}"
