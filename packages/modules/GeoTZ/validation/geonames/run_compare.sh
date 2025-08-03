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

# Fail fast on any error.
set -e

# Ordered in reverse so duplicate known issues are reported against the smaller
# city file.
CITY_SIZES=(15000 5000 1000 500)
EXTERNAL_GEONAMES_DIR=${ANDROID_BUILD_TOP}/external/geonames
SYSTEM_TIMEZONE_DIR=${ANDROID_BUILD_TOP}/system/timezone
GEOTZ_DIR=${ANDROID_BUILD_TOP}/packages/modules/GeoTZ
KNOWN_DIFFS_DIR=${GEOTZ_DIR}/validation/geonames/known_diffs
TMP_DIR=$(mktemp -d -t geonames-XXXXXXXXXX)
COMPARISON_CMD=geotz_geonames_comparison

if (( $# < 1 )); then
  echo "Usage: ${0} [500|1000|5000|15000]"
  echo
  echo "  The number determines the geonames file to use (500 means <= 500 population)"
  echo "  500 is generally fine to start: This includes 1,000, 5,000 and 15,000 population too."
  echo "  If you find differences, run with 15,000 and fix issues, then 5,000, then 1,000, etc."
  exit 1
fi

CITY_SIZE=$1
GEONAMES_CITIES_ZIP_FILE=${EXTERNAL_GEONAMES_DIR}/export/dump/cities${CITY_SIZE}.zip

# Find the Java tool before we begin.
if [[ -z $(which ${COMPARISON_CMD}) ]]; then
  echo "${COMPARISON_CMD} not found. Not built?"
  exit 1
fi

INPUT_DIR=${TMP_DIR}/input
mkdir ${INPUT_DIR}

OUTPUT_DIR=${TMP_DIR}/output
mkdir ${OUTPUT_DIR}

unzip ${GEONAMES_CITIES_ZIP_FILE} -d ${INPUT_DIR}
GEONAMES_CITIES_TXT_FILE=${INPUT_DIR}/cities${CITY_SIZE}.txt

KNOWN_DIFF_FILES=()
for SIZE in ${CITY_SIZES[@]}; do
  if (( "${SIZE}" >= ${CITY_SIZE} )); then
    KNOWN_DIFF_FILE=${KNOWN_DIFFS_DIR}/known_diffs${SIZE}.prototxt
    if [ -f ${KNOWN_DIFF_FILE} ]; then
      KNOWN_DIFF_FILES+=(${KNOWN_DIFF_FILE})
    fi
  fi
done
KNOWN_DIFFS_ARG=$(IFS=,; echo "${KNOWN_DIFF_FILES[*]}")

${COMPARISON_CMD} \
  ${GEOTZ_DIR}/output_data/odbl/tzs2.dat \
  ${SYSTEM_TIMEZONE_DIR}/output_data/android/tzids.prototxt \
  2020-01-01T00:00:00Z \
  ${GEONAMES_CITIES_TXT_FILE} \
  ${OUTPUT_DIR} \
  "${KNOWN_DIFFS_ARG}"

GEONAMES_SHA=$(git --git-dir=${EXTERNAL_GEONAMES_DIR}/.git rev-parse --short HEAD)
echo ${EXTERNAL_GEONAMES_DIR} git SHA is: ${GEONAMES_SHA}
