#!/bin/bash

#
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

read -p "This script will be deleting the current Health Connect Database on your device and flash it with the latest
 code. Do you want to continue y/n:" input

if [ "$input" != "y" ] && [ "$input" != "Y" ]; then
  if [ "$input" = "n" ] || [ "$input" = "N" ]; then
    exit
  else
    echo "Not a valid input, exiting."
    exit
  fi
fi

# Building the HealthFitness module
cd ~ || exit
cd $ANDROID_BUILD_TOP || exit
source build/envsetup.sh
lunch bluejay
cd packages/modules/HealthFitness || exit
m com.google.android.healthfitness

if [ $? -ne 0 ]; then
  echo "Operation failed. Error occurred during healthfitness module build"
fi

# Removing previously installed database from device
adb root
adb shell rm /data/system_ce/0/healthconnect/healthconnect.db
if [ $? -ne 0 ]; then
  echo "Operation failed. Error occurred during removal of database"
fi

# Installing the database version of currently build module
cd ~ || exit
adb root
adb install $OUT/system/apex/com.google.android.healthfitness.apex
adb reboot
adb wait-for-device

# Building the CtsHealthFitnessDeviceTestCases module
cd ~ || exit
cd $ANDROID_BUILD_TOP || exit
atest CtsHealthFitnessDeviceTestCases -b
if [ $? -ne 0 ]; then
  echo "Operation failed. Error occurred during CtsHealthFitnessDeviceTestCases module build"
fi

# Storing the database version in a variable
sleep 10s
adb root
version=$(
  adb shell <<EOF
cd ~;
sqlite3 /data/system_ce/0/healthconnect/healthconnect.db
PRAGMA user_version;
.exit
EOF
) || {
  echo "Waited but the new database was not created. Run the script again and please unlock your device after it reboots."
  exit
}

#saving the build apex file in resources
source_apex_file="$OUT/system/apex/com.google.android.healthfitness.apex"
required_apex_file_name="HeathConnectVersion_$version.apex"
destination_apex_resource="$ANDROID_BUILD_TOP/packages/modules/HealthFitness/tests/cts/hostsidetests/healthconnect/host/res/HealthConnectApexFiles/$required_apex_file_name"
cp "$source_apex_file" "$destination_apex_resource"
if [ $? -eq 0 ]; then
  echo "File '$source_apex_file' have been saved to resources '$destination_apex_resource'"
else
  echo "Failed to save file '$source_apex_file'."
fi

#saving the build cts-apk file in resources
source_apk_file="$OUT/testcases/CtsHealthFitnessDeviceTestCases"
required_apk_file_name="HealthConnectCTS_$version.apk"
destination_apk_resource="$ANDROID_BUILD_TOP/packages/modules/HealthFitness/tests/cts/hostsidetests/healthconnect/host/res/HealthConnectCtsApkFiles/$required_apk_file_name"
cp -r "$source_apk_file" "$destination_apk_resource"
if [ $? -eq 0 ]; then
  echo "File '$source_apk_file' have been saved to resources '$destination_apk_resource'"
else
  echo "Failed to save file '$source_apk_file'."
fi
