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

echo "Testing set_avds_force_cold_boot.sh"
echo "set all AVDs to Cold Boot"
$SCRIPT_DIR/set_avds_force_cold_boot.sh

echo "set all AVDs to Quick/Fast Boot"
FORCE_COLD_BOOT="no" $SCRIPT_DIR/set_avds_force_cold_boot.sh

echo "set all automotive AVDs to Quick/Fast Boot"
FORCE_COLD_BOOT="yes" AVD_ROOT_DIR="$HOME/.android/avd" TAG_ID="android-automotive" $SCRIPT_DIR/set_avds_force_cold_boot.sh
