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


MY_NAME=$0
SCRIPT_NAME=${MY_NAME##*/}
SCRIPT_DIR=${MY_NAME%/$SCRIPT_NAME}

echo "Testing patch_all_avds.sh"

echo "Set all AVDs with RAM=4096 HEAP=576 DATA_DISK=6000"
$SCRIPT_DIR/patch_all_avds.sh

echo "Set all AVDs back to AE default RAM=1536 HEAP=256 DATA_DISK=2048"
RAM=1536 HEAP=256 DATA_DISK=2048 $SCRIPT_DIR/patch_all_avds.sh

echo "Set all Aotomotive AVDs with RAM=4096 HEAP=576 DATA_DISK=6000"
RAM=4096 HEAP=576 DATA_DISK=6000 TAG_ID="android-automotive" $SCRIPT_DIR/patch_all_avds.sh
