#!/bin/bash

# Copyright (C) 2020 The Android Open Source Project
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

echo "To remount the system partition to be writable:"
echo "The AVD has to be 1. rootable & 2. started with -writable-system option"

echo "Disabling Verified Build"
adb root
adb disable-verity
adb shell avbctl disable-verification

echo "Rebooting, rooting & remouting"
adb reboot; adb wait-for-device
echo "Need to wait long enough for adb to be ready to remount"
sleep 10
adb root
adb remount