#!/bin/bash

# Copyright 2020, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Tails android system logs releven for crash tests

show_usage_and_exit() {
  echo "Tail logcat for NNAPI Crash Test logs, driver error logs and failures. Options:"
  echo " -e | --show_driver_errors : to enable error log for drivers"
  echo " -d | --driver_log_tag TAG : to use in conjunction with -e specify the LOG tags used by the target driver."
  echo "                             To specify multiple tags just quote them and separate them with a space char."
  echo "                             If this option is omitted, a list of known tags is used."
  echo " -o | --save_output_to FILE : to save logcat output to the given file"
  echo " -s DEVICE_SERIAL_NUMBER : to specify the serial number of the device to connect to"
  echo " -h : show this message and exit"
  exit 1
}

OPTS="$(getopt -o heo:s:d: -l show_driver_errors,save_output_to:driver_log_tag: -- "$@")"
eval set -- "$OPTS"

SHOW_DRIVERS_ERRORS=false
OUTPUT_FILE=""
DEVICE_ID_OPT=""
DRIVER_LOG_TAGS=()
while [ $# -gt 0 ] ; do
  case "$1" in
    -h)
      shift
      show_usage_and_exit
      ;;
    -e|--show_driver_errors)
      SHOW_DRIVERS_ERRORS=true
      shift
      ;;
    -o|--save_output_to)
      OUTPUT_FILE="$2"
      shift 2
      ;;
    -s)
      DEVICE_ID_OPT="-s $2"
      shift 2
      ;;
    -d|--driver_log_tag)
      read -a DRIVER_LOG_TAGS <<< "$2"
      shift 2
      ;;
    -- )
      shift
      break
      ;;
    * )
      echo "Invalid argument: $1"
      show_usage_and_exit
      ;;
  esac
done

CRASH_TEST_LOG_TAGS+=("NN_BENCHMARK" "NN_TESTBASE")
while IFS='' read -r tag; do
  CRASH_TEST_LOG_TAGS+=("$tag");
done <<< $(find src/com/android/nn/crashtest -name '*.java' -exec grep "TAG =" {} \; \
  | grep -Po '(?<=")(.+)(?=")')

LOG_TAG_FILTER=""

# Info level from crash tests source
for tag in "${CRASH_TEST_LOG_TAGS[@]}"; do
  LOG_TAG_FILTER="${LOG_TAG_FILTER} ${tag}:I"
done

if [[ "$SHOW_DRIVERS_ERRORS" = true ]]; then
  if [ ${#DRIVER_LOG_TAGS[@]} -eq 0 ]; then
    # Qualcomm (drivers for NNAPI1.2)
    # Google
    # SLSI
    DRIVER_LOG_TAGS=(\
      "android.hardware.neuralnetworks@1.2-service"\
      "Darwinn"\
      "Eden"\
    )
  fi

  # Error level for listed driver processes
  for tag in "${DRIVER_LOG_TAGS[@]}"; do
    LOG_TAG_FILTER="${LOG_TAG_FILTER} ${tag}:E"
  done
elif [ ${#DRIVER_LOG_TAGS[@]} -ne 0 ]; then
  show_usage_and_exit
fi

# Fatal message for everything else to show crash dumps
LOG_TAG_FILTER="${LOG_TAG_FILTER} QCSL:V tflite:E *:F"

export ANDROID_LOG_TAGS="${LOG_TAG_FILTER}"

if [[ -z "$OUTPUT_FILE" ]]; then
  adb ${DEVICE_ID_OPT:+"$DEVICE_ID_OPT"} logcat -v color
else
  adb ${DEVICE_ID_OPT:+"$DEVICE_ID_OPT"} logcat -v color | tee "$OUTPUT_FILE"
fi



