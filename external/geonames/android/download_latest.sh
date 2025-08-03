#!/usr/bin/env bash

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

set -e
SCRIPT_PATH=$(realpath $0)

CURL_CMD=curl
SERVER_BASE_URL=https://download.geonames.org/export/dump/
FILES=( \
  cities1000.zip \
  cities15000.zip \
  cities500.zip \
  cities5000.zip \
  readme.txt \
  )

LOCAL_ANDROID_DIR=$(dirname ${SCRIPT_PATH})
LOCAL_ROOT_DIR=$(dirname ${LOCAL_ANDROID_DIR})
LOCAL_DOWNLOAD_DIR=${LOCAL_ROOT_DIR}/export/dump
LOCAL_VERSION_INFO_DIR=${LOCAL_ANDROID_DIR}/version_info
METADATA_FILE=${LOCAL_ROOT_DIR}/METADATA

echo Removing old downloaded files and metadata...
rm ${LOCAL_DOWNLOAD_DIR}/*
rm ${LOCAL_VERSION_INFO_DIR}/*

# Start the METADATA file
cat << EOF > ${METADATA_FILE}
# Generated file DO NOT EDIT
# Run android/download_latest.sh
name: "geonames"
description:
    "Assorted files downloaded from https://www.geonames.org/"

third_party {
  url {
    type: HOMEPAGE
    value: "https://www.geonames.org/"
  }
EOF

DOWNLOAD_DATE=$(date --iso-8601)
for FILE in ${FILES[@]}; do
  URL=${SERVER_BASE_URL}${FILE}
  HEADERS_FILE=${LOCAL_VERSION_INFO_DIR}/${FILE}.headers
  RELATIVE_HEADERS_FILE=$(realpath --relative-to ${LOCAL_ROOT_DIR} ${HEADERS_FILE})

  echo Downloading ${FILE}...
  ${CURL_CMD} ${URL} \
      --output ${LOCAL_DOWNLOAD_DIR}/${FILE} \
      --dump-header ${HEADERS_FILE}

  SHA=$(sha1sum ${LOCAL_DOWNLOAD_DIR}/${FILE} | awk '{ print $1}')

  # Add a METADATA file URL entry for every file we download.
  cat << EOF >> ${METADATA_FILE}
  url {
    type: OTHER
    value: "Downloaded from ${URL} on ${DOWNLOAD_DATE}, SHA1=${SHA}, see ${RELATIVE_HEADERS_FILE}"
  }
EOF

done

FILE_LAST_MODIFIED_HEADER=$(grep -i 'Last-Modified' ${LOCAL_VERSION_INFO_DIR}/${FILES[0]}.headers)
LAST_MODIFIED_DATE_TIME=$(echo ${FILE_LAST_MODIFIED_HEADER} | sed 's/Last-Modified: //')
PROTO_LAST_MODIFIED_DATE=$(date --date="${LAST_MODIFIED_DATE_TIME}" +'{ year: '%Y' month: '%-m' day: '%-d' }')

# Finish the METADATA file
cat << EOF >> ${METADATA_FILE}
  version: "As of ${DOWNLOAD_DATE}"
  last_upgrade_date ${PROTO_LAST_MODIFIED_DATE}
  license_type: NOTICE
}
EOF
