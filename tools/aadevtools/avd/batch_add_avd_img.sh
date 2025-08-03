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
Add images listed in INPUT_CSV to Android SDK dir. So you can use them by Android Studio AVD manager. e.g.
./batch_add_avd_image.sh ./resource/avd_img_list.csv
'''
}

MY_NAME=$0
SCRIPT_NAME="${MY_NAME##*/}"
SCRIPT_DIR="${MY_NAME%/$SCRIPT_NAME}"
echo Running from $SCRIPT_DIR

INPUT_CSV=$1
if [[ -z "${INPUT_CSV}" ]]; then
    INPUT_CSV="$SCRIPT_DIR/resource/avd_img_list.csv"
fi

echo "Process ${INPUT_CSV}"
header=0
avdCount=0
while IFS=',' read -r name api zip others || [ -n "${name}" ]; do
  if [[ "${name}" == "name" ]]; then
    # skip header
    header="${name},${api},${zip}"
  else
    addAvdImgCmd="VARIANT=${name} API_LEVEL=${api} OEM_AVD_ZIP=${zip} ${SCRIPT_DIR}/add_avd_img.sh"
    echo "${addAvdImgCmd}"
    eval "${addAvdImgCmd}"

    createAvdCmd="AVD_IMG_NAME=${name} API_LEVEL=${api} DEFAULT_DEVICE_XML=${SCRIPT_DIR}/resource/devices.xml ${SCRIPT_DIR}/create_avd.sh"
    echo "${createAvdCmd}"
    eval "${createAvdCmd}"

    echo
    ((avdCount+=1))
  fi
done < $INPUT_CSV

if [[ $header != "name,api,zip" ]]; then
  readme
  echo "ERROR: The header:$header is not as expected in $INPUT_CSV"
  cat $INPUT_CSV
fi

echo "Processed ${avdCount} AVDs"