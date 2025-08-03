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

readme() {
  echo '''
Install apps in an app bundle release directory to the device via adb, e.g.
./batch_install_app.sh /path/to/app_bundle /path/to/report

Note: aapt is needed to get the metadata from APKs.
'''
}

SECONDS=0
MY_NAME=$0
SCRIPT_NAME="${MY_NAME##*/}"
SCRIPT_DIR="${MY_NAME%/$SCRIPT_NAME}"
echo Running from $SCRIPT_DIR

if [[ -z $OUT_DIR ]]; then
  OUT_DIR="${HOME}/Downloads"
fi

INPUT_DIR=$1
if [[ ! -d ${INPUT_DIR} ]]; then
  echo "Error: ${INPUT_DIR} is not a directory."
  readme
  exit
fi

echo "LOG=${LOG}"
log() {
  if [[ -n ${LOG} ]]; then
    echo $1
  fi
}

# check an app/package version via adb, e.g.
# checkAppVersion package_name
checkAppVersion() {
  pkg=$1
  cmd="adb shell dumpsys package ${pkg}"
  dump=$(${cmd})
  log "$dump"
  echo "${dump}" | grep versionName
}

echo "Process all APKs in ${INPUT_DIR}"
# apkDic[apk_name]=apk_path
declare -A apkDic="$(${SCRIPT_DIR}/get_file_dir.sh ${INPUT_DIR} apk)"
echo "Found: ${#apkDic[@]} apks"

screenshotDir="/data/local/tmp/screenshots"
echo "Removig the following screenshots from the device"
adb shell ls -l ${screenshotDir}
adb shell rm -r ${screenshotDir}
adb shell mkdir -p ${screenshotDir}

# apkBadgingDic[apk_name]=aapt_badging_output_string
declare -A apkBadgingDic
# manifestDic[apk_name]=AndroidManifest_xml_content_string
declare -A manifestDic
i=1
for apk in "${!apkDic[@]}"; do
  path="${apkDic[${apk}]}"
  badging=$(aapt dump badging ${path})
  apkBadgingDic[${apk}]="\"${badging}\""
  log "${apkBadgingDic[${apk}]}"

  # Get package name from the aapt badging output string
  # ... package: name='com.google.android.gsf' versionCode...
  pkg0=${badging#package: name=\'}
  pkg=${pkg0%\' versionCode*}

  echo "$i,${pkg},${apk},${path}"
  checkAppVersion ${pkg}
  ${SCRIPT_DIR}/install_apk.sh ${path}
  checkAppVersion ${pkg}
  echo

  # Get the 1st launchable activity
  # ... launchable-activity: name='com.google.android.maps.MapsActivity'  label...
  if [[ "$badging" == *"launchable-activity: name="* ]]; then
    activity0=${badging#*launchable-activity: name=\'}
    activity=${activity0%%\'  label=*}
    echo "Launching an activity: ${activity}"
    adb shell am start -n "${pkg}/${activity}"
    sleep 5
    adb shell screencap "${screenshotDir}/${pkg}.png"
    echo "grep screen"
  fi

  i=$(($i + 1))
done

adb shell ls -l ${screenshotDir}
adb pull ${screenshotDir} ${OUT_DIR}
echo "Took ${SECONDS} seconds"
