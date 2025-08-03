#!/bin/bash
#
# Copyright (C) 2023 Google LLC
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

WATCHDOG_TASK="watchdog_cycles"
WATCHDOG_CYCLES_TASK_HOST="${OUT}/system/bin/${WATCHDOG_TASK}"
WATCHDOG_CYCLES_TASK_ANDROID="/data/local/tmp/${WATCHDOG_TASK}"

if [ -z "${RESULTS_DIR}" ] ; then
  echo "Error: you need to specify CARWATCHDOG_CUSTOM_DUMP"
  exit 1
fi

# Check if the watchdog cycle file is present in Android
adb shell ls "${WATCHDOG_CYCLES_TASK_ANDROID}"
if [ $? -ne 0 ]; then
  if [ -f "${WATCHDOG_CYCLES_TASK_HOST}" ]; then
    adb push "${WATCHDOG_CYCLES_TASK_HOST}" "${WATCHDOG_CYCLES_TASK_ANDROID}"
    adb shell chmod +x "${WATCHDOG_CYCLES_TASK_ANDROID}"
  else
    echo "Did not find ${WATCHDOG_CYCLES_TASK_HOST} in host.
    Try running 'm ${WATCHDOG_TASK}' to generate the file."
    exit 1
  fi
fi

SYSFS_CPUFREQ="/sys/devices/system/cpu/cpufreq"
GOVERNOR='userspace'

# Available frequencies for the CPU freq policies in Seahawk
# policy0:
# - 300000 403200 499200 576000 672000 768000 844800 940800 1036800 1113600
#   1209600 1305600 1382400 1478400 1555200 1632000 1708800 1785600
# policy4:
# - 710400 825600 940800 1056000 1171200 1286400 1401600 1497600 1612800 1708800
#   1804800 1920000 2016000 2131200
# policy7:
# - 825600 940800 1056000 1171200 1286400 1401600 1497600 1612800 1708800
#   1804800 1920000 2016000 2131200 2227200 2323200 2419200

declare -A POLICY_SPEED
POLICY_SPEED[policy0]=403200
POLICY_SPEED[policy4]=710400
POLICY_SPEED[policy7]=2419200

adb root

# Stop the framework
adb shell stop

# Set the CPU frequency to the same value across all CPU freq policies
for POLICY in $(adb shell ls /sys/devices/system/cpu/cpufreq) ; do
  POLICY_FILE="${SYSFS_CPUFREQ}/${POLICY}/scaling_governor"
  SPEED_FILE="${SYSFS_CPUFREQ}/${POLICY}/scaling_setspeed"
  SCALING_SPEED=${POLICY_SPEED[${POLICY}]}
  adb shell "echo ${GOVERNOR} > ${POLICY_FILE}"
  adb shell "echo ${SCALING_SPEED} > ${SYSFS_CPUFREQ}/${POLICY}/scaling_min_freq"
  adb shell "echo ${SCALING_SPEED} > ${SYSFS_CPUFREQ}/${POLICY}/scaling_max_freq"
  adb shell "echo ${SCALING_SPEED} > ${SPEED_FILE}"
  SET_GOVERNOR=$(adb shell cat "${POLICY_FILE}")
  CUR_FREQ=$(adb shell cat "${SYSFS_CPUFREQ}/${POLICY}/cpuinfo_cur_freq")
  if [ "${SET_GOVERNOR}" != "${GOVERNOR}" ] ; then
    echo "Unable to set the governor to '${GOVERNOR}' in ${POLICY_FILE}."
    echo "Exiting experiment"
    exit 1
  fi
  if [ "${CUR_FREQ}" != "${SCALING_SPEED}" ] ; then
    echo "Unable to set '${SCALING_SPEED}' in ${SPEED_FILE}."
    echo "Exiting experiment"
    exit 1
  fi
done

# Set the desired cpuset to only use cpu7
CPUSET="top-app"
CPUSET_DIR="/dev/cpuset/${CPUSET}"
CORE_NUMS="7" # Possible values: 0-3, 4-6, 7
CORE_POLICY="policy7" # The policy which contains the cores above

PREV_CORES=$(adb shell cat "${CPUSET_DIR}/cpus")

adb shell "echo ${CORE_NUMS} > ${CPUSET_DIR}/cpus"

# Add the shell process to run on the cpuset
# Does it also need to be pushed to $CPUSET_DIR/tasks?
adb shell "echo $$ > ${CPUSET_DIR}/cgroup.procs"


# Set the outputs
CARWATCHDOG_CUSTOM_DUMP="${RESULTS_DIR}/cw_freq_${POLICY_SPEED[${CORE_POLICY}]}.txt"
CARWATCHDOG_CUSTOM_PB="${RESULTS_DIR}/cw_freq_${POLICY_SPEED[${CORE_POLICY}]}.pb"

# Start a carwatchdog custom collection filtering only for 'root' package
adb shell dumpsys android.automotive.watchdog.ICarWatchdog/default --start_perf \
           --max_duration 600 --interval 1 --filter_packages root
if [ $? -ne 0 ]; then
  echo "Unable to start carwatchdog custom collection."
  exit 1
fi

sleep 2

# Process will be running on cores=$CORE_NUMS, hence the CPU frequency used is
# the will be the one set by the CPU freq policy containing the cores.
time adb shell "${WATCHDOG_CYCLES_TASK_ANDROID}"

sleep 2

adb shell dumpsys android.automotive.watchdog.ICarWatchdog/default \
           --stop_perf > "${CARWATCHDOG_CUSTOM_DUMP}"
if [ $? -ne 0 ]; then
  echo "Unable to stop carwatchdog custom collection."
fi

# Generate the proto for the carwatchdog stats
perf_stats_parser -f "${CARWATCHDOG_CUSTOM_DUMP}" -o "${CARWATCHDOG_CUSTOM_PB}"

# Set the cpuset back to the original values
adb shell "echo ${PREV_CORES} > ${CPUSET_DIR}/cpus"

# Start the framework again
adb shell start