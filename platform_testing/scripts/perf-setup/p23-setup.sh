#!/system/bin/sh
#
# Copyright (C) 2023 The Android Open Source Project
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

# Performance test setup for 2023 devices

echo "Disabling Tskin thermal mitigation..."
setprop persist.vendor.disable.thermal.control 1

echo "Disabling TJ thermal mitigation..."
setprop persist.vendor.disable.thermal.tj.control 1

echo "Clearing cooling device states..."
for i in /sys/devices/virtual/thermal/cooling_device*/user_vote; do echo 0 > "$i" 2>/dev/null; done
for i in /sys/devices/virtual/thermal/cooling_device*/cur_state; do echo 0 > "$i" 2>/dev/null; done

echo "Disabling powerhints..."
setprop vendor.powerhal.init 0
setprop ctl.restart vendor.power-hal-aidl

# set max freq for all cores
echo "Locking CPUs to the max freq..."
for cpu_path in /sys/devices/system/cpu/cpu*[0-9]; do
  local max_freq=`cat $cpu_path/cpufreq/cpuinfo_max_freq`
  echo $max_freq > $cpu_path/cpufreq/scaling_max_freq
  echo $max_freq > $cpu_path/cpufreq/scaling_min_freq

  local cur_freq=`cat $cpu_path/cpufreq/cpuinfo_cur_freq`
  echo "`basename $cpu_path` -> $cur_freq"
done

echo "Locking GPU to the max freq..."
echo 890000 > /sys/devices/platform/1f000000.mali/scaling_max_freq
echo 890000 > /sys/devices/platform/1f000000.mali/scaling_min_freq
cat /sys/devices/platform/1f000000.mali/cur_freq

echo "Locking Buses to the max freq..."
for path in /sys/class/devfreq/*{bci,mif,dsu,int}; do
  local max_freq=`cat $path/max_freq`
  echo $max_freq > $path/exynos_data/debug_scaling_devfreq_max
  echo $max_freq > $path/exynos_data/debug_scaling_devfreq_min

  local cur_freq=`cat $path/cur_freq`
  echo "`basename $path` -> $cur_freq"
done

