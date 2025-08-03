#!/system/bin/sh
#
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

# Exit on errors, or whenever an unset variable is substituted
set -eu

stop thermal_manager
setprop vendor.powerhal.init 0
stop power-hal-1-0
start power-hal-1-0

echo 'Locking CPU to 1125000 (56% relative to max freq)'
echo 11 > /proc/ppm/policy/ut_fix_freq_idx

expected_gpu_freq='390000'
echo "Locking GPU to ${expected_gpu_freq} (min freq)"
echo "${expected_gpu_freq}" > /proc/gpufreq/gpufreq_opp_freq

expected_cpu_freqs='1125000, 1125000, 1125000, 1125000'
cpu_freqs="$(cat /sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_cur_freq | sed -z 's/\n/, /g')"
echo "Set CPU frequencies to ${cpu_freqs}"
if [[ "${cpu_freqs}" -ne "${expected_cpu_freqs}" ]]; then
  echo "Failed to set CPUs to expected frequencies: ${expected_cpu_freqs}"
  exit 1
fi

gpu_freq="$(cat /proc/gpufreq/gpufreq_opp_freq | tail -n 1 | cut -d '=, ' -f 5)"
echo "Set GPU frequency to ${gpu_freq}"
if [[ "${gpu_freq}" -ne "${expected_gpu_freq}" ]]; then
  echo "Failed to set GPU to expected frequency: ${expected_gpu_freq}"
  exit 1
fi


