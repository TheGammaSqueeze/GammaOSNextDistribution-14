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
  check permissions xml from a apk bundle release aginast those on a device
  ./batch_check_permission.sh ~/Downloads/apk_bundle_dir ~/Downloads/override.csv
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
if [[ -z "${INPUT_DIR}" ]]; then
  readme
  exit
fi

RENAME_CSV=$2
# Read rename csv to create xmlRenameDic
declare -A xmlRenameDic
if [[ -f ${RENAME_CSV} ]]; then
  while IFS=',' read -r name newName others || [ -n "${name}" ]; do
    if [[ "${name}" == "name" ]]; then
      # skip header
      header="${name},${newName}"
    else
      xmlRenameDic["${name}"]="${newName}"
    fi
  done < $RENAME_CSV
fi

echo "LOG=${LOG}"
log() {
  if [[ -n ${LOG} ]]; then
    echo $1
  fi
}

echo "Listing xmls in ${INPUT_DIR}"
declare -A relXmlDic

declare -A relXmlDic="$(${SCRIPT_DIR}/get_file_dir.sh ${INPUT_DIR} xml)"
echo "Found: ${#relXmlDic[@]} xmls"

echo "Listing xmls in the device"
declare -A deviceXmlDic
deviceXmlList=$(adb shell "find / -name *.xml" 2>/dev/null)
for xml in ${deviceXmlList}; do
  file=${xml##*/}
  fPath=${xml%/*}
  fParentPathPostfix=${fPath:(-11)}
  if [[ "permissions" == ${fParentPathPostfix} ]]; then
    deviceXmlDic[${file}]=${xml}
    log "${file} ${fPath} ${fParentPathPostfix}"
  fi
done
echo "Found: ${#deviceXmlDic[@]} xmls"

echo "Comparing xmls from ${INPUT_DIR} to those on the device."
i=1
for xml in "${!relXmlDic[@]}"; do
  # relFile="...google/etc/permissions/privapp-permissions-car.xml"
  relFile=${relXmlDic[$xml]}
  # fPath="...google/etc/permissions"
  fPath=${relFile%/*}
  # fParentPathPostfix="permissions"
  fParentPathPostfix=${fPath:(-11)}
  log "${xml} ${fPath} ${fParentPathPostfix}"

  # Only care about permissions
  if [[ "permissions" == ${fParentPathPostfix} ]]; then
    echo "$i Comparing permission file: $xml"

    deviceFile=${deviceXmlDic[$xml]}
    if [[ -z ${deviceFile} ]]; then
      # Maybe it's renamed
      newXml=${xmlRenameDic[$xml]}
      log "Rename $xml to $newXml"
      deviceFile=${deviceXmlDic[$newXml]}
      if [[ -z ${deviceFile} ]]; then
        echo "Error: no ${xml} on the device."
        echo
        i=$(($i + 1))
        continue
      fi
    fi

    # Pull the xml from device & diff
    adb pull "${deviceFile}" "${OUT_DIR}/${xml}"
    diff "${relXmlDic[$xml]}" "${OUT_DIR}/${xml}"
    i=$(($i + 1))
    echo
  fi
done
echo "Took ${SECONDS} seconds"
