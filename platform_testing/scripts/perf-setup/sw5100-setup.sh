#!/system/bin/sh
#
# Copyright (C) 2020 The Android Open Source Project
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

# performance test setup for sw5100 devices
function disable_thermal()
{
    thermal_path='/sys/devices/virtual/thermal/'

    nz=$(ls -al $thermal_path | grep thermal_zone | wc -l)
    i=0
    while [ $i -lt $nz ]; do
        tz_path=$thermal_path'thermal_zone'$i'/'
        mode_path=$tz_path'mode'

        if [ -f $mode_path ]; then
            echo disabled > $tz_path'mode'
        fi
        i=$(($i + 1));
    done
}

disable_thermal
#setprop vendor.powerhal.init 0
#setprop ctl.restart vendor.power-hal-aidl

cpubase=/sys/devices/system/cpu
gov=cpufreq/scaling_governor

cpu=0
top=4
# 614400 864000 1363200 1708800
cpufreq=1708800
# Set the cores around 1.7G
while [ $((cpu < $top)) -eq 1 ]; do
  echo 1 > $cpubase/cpu${cpu}/online
  echo userspace > $cpubase/cpu${cpu}/$gov
  echo $cpufreq > /sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_max_freq
  echo $cpufreq > /sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_setspeed
  S=`cat $cpubase/cpu${cpu}/cpufreq/scaling_cur_freq`
  echo "set cpu $cpu to $S kHz"
  cpu=$(($cpu + 1))
done

echo "disable GPU bus split"
echo 0 > /sys/class/kgsl/kgsl-3d0/bus_split
echo "enable GPU force clock on"
echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on
echo "set GPU idle timer to 10000"
echo 10000 > /sys/class/kgsl/kgsl-3d0/idle_timer

# 1010 900 700 470 310 mhz
echo gpubw_mon > /sys/class/devfreq/kgsl-busmon/governor
echo 1010000000 > /sys/class/devfreq/kgsl-busmon/min_freq
echo -n "set GPU bus frequency to max at "
cat /sys/class/devfreq/kgsl-busmon/target_freq

# 1010 900 700 470 310 mhz
echo msm-adreno-tz > /sys/class/kgsl/kgsl-3d0/devfreq/governor
echo 1010000000 > /sys/class/kgsl/kgsl-3d0/devfreq/min_freq
echo 0 > /sys/class/kgsl/kgsl-3d0/max_pwrlevel
echo 0 > /sys/class/kgsl/kgsl-3d0/min_pwrlevel
echo -n "set GPU frequency to max at "
cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq
