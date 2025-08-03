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

if [ -z ${ANDROID_BUILD_TOP} ]; then
  echo \$ANDROID_BUILD_TOP must be set.
  exit 1
fi

SYSTEM_TIMEZONE_DIR=$(realpath ${ANDROID_BUILD_TOP}/system/timezone)
GEOTZ_DIR=$(realpath ${ANDROID_BUILD_TOP}/packages/modules/GeoTZ)
DATA_PIPELINE_DIR=${GEOTZ_DIR}/data_pipeline
TZBB_DATA_DIR=${GEOTZ_DIR}/tzbb_data
WORKING_DIR_ROOT=${DATA_PIPELINE_DIR}/working_dir
OUTPUT_DATA_DIR=${GEOTZ_DIR}/output_data/odbl

#
# Hardcoded values that can be changed for debugging / dev to speed things up.
#

# Change this to a lower level to speed up S2 sampling.
S2_LEVEL=12

# Set to 1 to skip the initial build step if no code changes are being made.
SKIP_BUILD=0

# Set to 1 to skip to a later step. See also ALLOW_WORKING_DIR_ROOT_EXISTS.
# Set to 1 ignore if the output dir exists. Use with SKIP_TO_STEP to resume a
# failed run. Most steps discover what to do by inspecting the names of files so
# be careful not to skip to step with no output data available.
SKIP_TO_STEP=0
ALLOW_WORKING_DIR_ROOT_EXISTS=0

# Only process a subset of zone IDs from the geojson input file.
# Must be comma separated, no spaces.
# Do not use with SKIP_TO_STEP > 1 as it will have no effect.
# Be careful with this and ALLOW_WORKING_DIR_ROOT_EXISTS=1 as that can leave STEP1
# output files from previous runs, which will then be processed by STEP2.
STEP1_RESTRICT_TO_ZONES=


if [ -d ${WORKING_DIR_ROOT} ]; then
  echo Working dir ${WORKING_DIR_ROOT} exists...
  if (( ${ALLOW_WORKING_DIR_ROOT_EXISTS} == 0 )); then
    echo Halting...
    echo Remove or move ${WORKING_DIR_ROOT} and try again.
    exit 1
  fi
fi

MAX_STEP_ID=7
if (( ${SKIP_TO_STEP} > ${MAX_STEP_ID} )); then
  echo Cannot skip to step ${SKIP_TO_STEP}
  exit 1
fi

JAVA_ARGS="-J-Xmx32G"

STEP1_TARGET=geotz_geojsontz_to_tzs2polygons
STEP1_CMD="${STEP1_TARGET} ${JAVA_ARGS}"
STEP1_THREAD_COUNT=10
STEP1_WORKING_DIR=${WORKING_DIR_ROOT}/tzs2polygons

STEP2_TARGET=geotz_canonicalize_tzs2polygons
STEP2_CMD="${STEP2_TARGET} ${JAVA_ARGS}"
STEP2_TZIDS_FILE=${SYSTEM_TIMEZONE_DIR}/output_data/android/tzids.prototxt
STEP2_REPLACEMENT_THREADHOLD=2020-01-01T00:00:00.00Z
STEP2_WORKING_DIR=${WORKING_DIR_ROOT}/canonicalized_tzs2polygons

STEP3_TARGET=geotz_tzs2polygons_to_tzs2cellunions
STEP3_CMD="${STEP3_TARGET} ${JAVA_ARGS}"
STEP3_THREAD_COUNT=5
STEP3_WORKING_DIR=${WORKING_DIR_ROOT}/tzs2cellunions_l${S2_LEVEL}

STEP4_TARGET=geotz_tzs2cellunions_to_tzs2ranges
STEP4_CMD="${STEP4_TARGET} ${JAVA_ARGS}"
STEP4_THREAD_COUNT=10
STEP4_WORKING_DIR=${WORKING_DIR_ROOT}/tzs2ranges_l${S2_LEVEL}

STEP5_TARGET=geotz_mergetzs2ranges
STEP5_CMD="${STEP5_TARGET} ${JAVA_ARGS}"
STEP5_THREAD_COUNT=5
STEP5_WORKING_DIR=${WORKING_DIR_ROOT}/mergedtzs2ranges_l${S2_LEVEL}
STEP5_OUTPUT_FILE=${STEP5_WORKING_DIR}/mergedtzs2ranges${S2_LEVEL}.prototxt

STEP6_TARGET=geotz_createtzs2fileinput
STEP6_CMD="${STEP6_TARGET} ${JAVA_ARGS}"
STEP6_OUTPUT_FILE=${WORKING_DIR_ROOT}/tzs2fileinput/tzs2fileinput${S2_LEVEL}.prototxt

STEP7_TARGET=geotz_createtzs2file
STEP7_CMD="${STEP7_TARGET} ${JAVA_ARGS}"
STEP7_OUTPUT_FILE=${OUTPUT_DATA_DIR}/tzs2.dat

BUILD_TARGETS=(\
  ${STEP1_TARGET} \
  ${STEP2_TARGET} \
  ${STEP3_TARGET} \
  ${STEP4_TARGET} \
  ${STEP5_TARGET} \
  ${STEP6_TARGET} \
  ${STEP7_TARGET} \
)

echo ${0} starting at $(date --iso-8601=seconds)

mkdir -p ${WORKING_DIR_ROOT}

# Build all step commands
if (( ${SKIP_BUILD} == 1 )); then
  echo Skipping build step...
else
  BUILD_CMD="${ANDROID_BUILD_TOP}/build/soong/soong_ui.bash --make-mode -j30"
  LOG_FILE=${WORKING_DIR_ROOT}/build.log
  echo Building step commands. Logging to ${LOG_FILE} ...
  {
    ${BUILD_CMD} ${BUILD_TARGETS[@]}
  } &> ${LOG_FILE}
fi

# Step 0: Preparation, unpack the geojson file into the working dir.
ZIPPED_BOUNDARY_FILE=${TZBB_DATA_DIR}/timezones.geojson.zip
echo Starting step 0
if (( ${SKIP_TO_STEP} <= 0 )); then
  mkdir ${WORKING_DIR_ROOT}/dist
  echo Unpacking ${ZIPPED_BOUNDARY_FILE} to ${WORKING_DIR_ROOT}/dist/...
  unzip -o ${TZBB_DATA_DIR}/timezones.geojson.zip -d ${WORKING_DIR_ROOT}/dist/

  # Ensure there's a LICENSE next to the boundary file. It will be copied
  # alongside data by subsequent steps.
  echo Copying LICENSE file to ${WORKING_DIR_ROOT}/dist/
  cp ${TZBB_DATA_DIR}/LICENSE ${WORKING_DIR_ROOT}/dist/
else
  echo Skipping...
fi
echo Completed step 0

UNZIPPED_BOUNDARY_FILE=${WORKING_DIR_ROOT}/dist/combined.json
if [ ! -f ${UNZIPPED_BOUNDARY_FILE} ]; then
  echo "${UNZIPPED_BOUNDARY_FILE} not found"
  exit 1
fi

# Step 1
echo Starting step 1
if (( ${SKIP_TO_STEP} <= 1 )); then
  mkdir -p ${STEP1_WORKING_DIR}
  LOG_FILE=${WORKING_DIR_ROOT}/step1.log
  echo Logging to ${LOG_FILE} ...
  {
    ${STEP1_CMD} \
        --geo-json ${UNZIPPED_BOUNDARY_FILE} \
        --num-threads ${STEP1_THREAD_COUNT} \
        --output ${STEP1_WORKING_DIR} \
        --tz-ids "${STEP1_RESTRICT_TO_ZONES}"
  } &> ${LOG_FILE}
else
  echo Skipping...
fi
echo Completed step 1

# Step 2
echo Starting step 2
if (( ${SKIP_TO_STEP} <= 2 )); then
  mkdir -p ${STEP2_WORKING_DIR}
  LOG_FILE=${WORKING_DIR_ROOT}/step2.log
  echo Logging to ${LOG_FILE} ...
  {
    ${STEP2_CMD} \
      --input ${STEP1_WORKING_DIR} \
      --tz-ids ${STEP2_TZIDS_FILE} \
      --replacement-threshold ${STEP2_REPLACEMENT_THREADHOLD} \
      --output ${STEP2_WORKING_DIR}
  } &> ${LOG_FILE}
else
  echo Skipping...
fi
echo Completed step 2

# Step 3
echo Starting step 3
if (( ${SKIP_TO_STEP} <= 3 )); then
  mkdir -p ${STEP3_WORKING_DIR}
  LOG_FILE=${WORKING_DIR_ROOT}/step3.log
  echo Logging to ${LOG_FILE} ...
  {
    ${STEP3_CMD} \
      --input ${STEP2_WORKING_DIR} \
      --num-threads ${STEP3_THREAD_COUNT} \
      --output ${STEP3_WORKING_DIR} \
      --max-s2-level ${S2_LEVEL}
  } &> ${LOG_FILE}
else
  echo Skipping...
fi
echo Completed step 3

# Step 4
echo Starting step 4
if (( ${SKIP_TO_STEP} <= 4 )); then
  mkdir -p ${STEP4_WORKING_DIR}
  LOG_FILE=${WORKING_DIR_ROOT}/step4.log
  echo Logging to ${LOG_FILE} ...
  {
    ${STEP4_CMD} \
      --input ${STEP3_WORKING_DIR} \
      --num-threads ${STEP4_THREAD_COUNT} \
      --output ${STEP4_WORKING_DIR} \
      --s2-level ${S2_LEVEL}
  } &> ${LOG_FILE}
else
  echo Skipping...
fi
echo Completed step 4

# Step 5
echo Starting step 5
if (( ${SKIP_TO_STEP} <= 5 )); then
  mkdir -p ${STEP5_WORKING_DIR}
  LOG_FILE=${WORKING_DIR_ROOT}/step5.log
  echo Logging to ${LOG_FILE} ...
  {
    ${STEP5_CMD} \
      --input ${STEP4_WORKING_DIR} \
      --num-threads ${STEP5_THREAD_COUNT} \
      --working-dir ${STEP5_WORKING_DIR} \
      --output-file ${STEP5_OUTPUT_FILE}
  } &> ${LOG_FILE}
else
  echo Skipping...
fi
echo Completed step 5

# Step 6
echo Starting step 6
if (( ${SKIP_TO_STEP} <= 6 )); then
  LOG_FILE=${WORKING_DIR_ROOT}/step6.log
  echo Logging to ${LOG_FILE} ...
  {
    ${STEP6_CMD} \
      --input-file ${STEP5_OUTPUT_FILE} \
      --output-file ${STEP6_OUTPUT_FILE}
  } 2>&1 > ${LOG_FILE}
else
  echo Skipping...
fi
echo Completed step 6

# Step 7
echo Starting step 7
if (( ${SKIP_TO_STEP} <= 7 )); then
  LOG_FILE=${WORKING_DIR_ROOT}/step7.log
  echo Logging to ${LOG_FILE} ...
  mkdir -p $(dirname ${STEP7_OUTPUT_FILE})
  {
    ${STEP7_CMD} \
      --input-file ${STEP6_OUTPUT_FILE} \
      --s2-level ${S2_LEVEL} \
      --output-file ${STEP7_OUTPUT_FILE}
  } 2>&1 > ${LOG_FILE}
else
  echo Skipping...
fi
echo Completed step 7

echo Output files can be found in ${OUTPUT_DATA_DIR}

echo ${0} completed at $(date --iso-8601=seconds)
