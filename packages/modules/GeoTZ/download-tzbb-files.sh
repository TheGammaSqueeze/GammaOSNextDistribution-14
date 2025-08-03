#!/bin/bash

# Copyright 2020 The Android Open Source Project
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

# Fail fast on any error.
set -e

if [ -z ${ANDROID_BUILD_TOP} ]; then
  echo \$ANDROID_BUILD_TOP must be set.
  exit 1
fi

RELEASE_ID=${1}
if [ -z ${RELEASE_ID} ]; then
  echo "Usage:"
  echo "  ${0} <release ID>"
  echo
  echo "e.g. ${0} 2020a"
  exit 1
fi

SCRIPT_PATH=$(realpath $0)

# -f = report failures
# -L = follow redirects
CURL_CMD="curl -f -L"

TZBB_PROJECT_URL=https://github.com/evansiroky/timezone-boundary-builder/
SERVER_RELEASE_BASE_URL=${TZBB_PROJECT_URL}releases/download
SERVER_SOURCE_BASE_URL=https://raw.githubusercontent.com/evansiroky/timezone-boundary-builder/master

SOURCE_FILES=(\
  DATA_LICENSE \
)
RELEASE_FILES=(\
  input-data.zip \
  timezones.geojson.zip \
)

function download() {
  set -e
  SERVER_BASE_URL=${1}
  FILE_NAME=${2}
  LOCAL_DIR=${3}

  URL=${SERVER_BASE_URL}/${FILE_NAME}
  LOCAL_FILE_NAME=${LOCAL_DIR}/${FILE_NAME}
  echo Downloading ${FILE_NAME} from ${URL}...
  ${CURL_CMD} ${URL} --output ${LOCAL_FILE_NAME}

  DOWNLOAD_DATE=$(date)
  SHA=$(sha1sum ${LOCAL_FILE_NAME} | awk '{ print $1}')

  # Add a METADATA file URL entry for every file we download.
  cat << EOF >> ${METADATA_FILE}
  url {
    type: OTHER
    value: "${FILE_NAME} downloaded from ${URL} on ${DOWNLOAD_DATE}, SHA1=${SHA}"
  }
EOF
}

LOCAL_DATA_DIR=${ANDROID_BUILD_TOP}/packages/modules/GeoTZ/tzbb_data

mkdir -p ${LOCAL_DATA_DIR}
echo Removing existing data files...
rm -f ${LOCAL_DATA_DIR}/*

METADATA_FILE=${LOCAL_DATA_DIR}/METADATA
# Start the METADATA file
cat << EOF > ${METADATA_FILE}
// Generated file DO NOT EDIT
// Run download-tzbb-files.sh
name: "tzbb_data"
description:
    "Assorted files downloaded from ${TZBB_PROJECT_URL}"

third_party {
  url {
    type: HOMEPAGE
    value: "${TZBB_PROJECT_URL}"
  }
EOF

for FILE in ${SOURCE_FILES[@]}; do
  download ${SERVER_SOURCE_BASE_URL} ${FILE} ${LOCAL_DATA_DIR}
done

for FILE in ${RELEASE_FILES[@]}; do
  download ${SERVER_RELEASE_BASE_URL}/${RELEASE_ID} ${FILE} ${LOCAL_DATA_DIR}
done

PROTO_UPGRADE_DATE=$(date +'{ year: '%Y' month: '%-m' day: '%-d' }')

# Finish the METADATA file
cat << EOF >> ${METADATA_FILE}
  version: "${RELEASE_ID}"
  last_upgrade_date ${PROTO_UPGRADE_DATE}
  license_type: RESTRICTED
}
EOF

ln -sr ${LOCAL_DATA_DIR}/DATA_LICENSE ${LOCAL_DATA_DIR}/LICENSE

echo Look in ${LOCAL_DATA_DIR} for updated files....
