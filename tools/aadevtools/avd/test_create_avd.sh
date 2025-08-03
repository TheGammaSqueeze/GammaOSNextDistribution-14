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

echo "Create sdk_gcar_30.avd"
AVD_IMG_NAME="sdk_gcar" \
  ABI="x86_64" \
  API_LEVEL=30 \
  DEFAULT_DEVICE_XML="${SCRIPT_DIR}/resource/devices.xml" \
  "${SCRIPT_DIR}/create_avd.sh"

echo
echo "Remove sdk_gcar_30.avd"
rm -r /Users/samlin/.android/avd/sdk_gcar_30.avd || rm /Users/samlin/.android/avd/sdk_gcar_30.ini

echo
echo "Create sdk_gcar_30.avd again"
AVD_IMG_NAME="sdk_gcar" \
  ABI="x86_64" \
  API_LEVEL=30 \
  DEFAULT_DEVICE_XML="${SCRIPT_DIR}/resource/devices.xml" \
  "${SCRIPT_DIR}/create_avd.sh"
