#!/system/bin/sh
#
# Copyright (C) 2022 The Android Open Source Project
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

# Performance test setup for 2022 devices

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

echo "Locking LITTLE CPUs to the max freq..."
echo 1803000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq
echo 1803000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq
cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq

echo "Locking MID CPUs to the max freq..."
echo 2348000 > /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq
echo 2348000 > /sys/devices/system/cpu/cpu4/cpufreq/scaling_min_freq
cat /sys/devices/system/cpu/cpu4/cpufreq/scaling_cur_freq

echo "Locking BIG CPUs to the max freq..."
echo 2850000 > /sys/devices/system/cpu/cpu6/cpufreq/scaling_max_freq
echo 2850000 > /sys/devices/system/cpu/cpu6/cpufreq/scaling_min_freq
cat /sys/devices/system/cpu/cpu6/cpufreq/scaling_cur_freq

echo "Locking GPU to the max freq..."
echo 848000 > /sys/devices/platform/28000000.mali/scaling_max_freq
echo 848000 > /sys/devices/platform/28000000.mali/scaling_min_freq
cat /sys/devices/platform/28000000.mali/cur_freq

echo "Locking MIF to the max freq..."
echo 3172000 > /sys/devices/platform/17000010.devfreq_mif/devfreq/17000010.devfreq_mif/exynos_data/debug_scaling_devfreq_max
echo 3172000 > /sys/devices/platform/17000010.devfreq_mif/devfreq/17000010.devfreq_mif/exynos_data/debug_scaling_devfreq_min
cat /sys/devices/platform/17000010.devfreq_mif/devfreq/17000010.devfreq_mif/cur_freq

echo "Locking INT to the max freq..."
echo 533000 > /sys/devices/platform/17000020.devfreq_int/devfreq/17000020.devfreq_int/exynos_data/debug_scaling_devfreq_max
echo 533000 > /sys/devices/platform/17000020.devfreq_int/devfreq/17000020.devfreq_int/exynos_data/debug_scaling_devfreq_min
cat /sys/devices/platform/17000020.devfreq_int/devfreq/17000020.devfreq_int/cur_freq


