#!/usr/bin/python3

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
#
"""Tool to analyze CPU performance with some cores disabled.
Should install perfetto: $ pip install perfetto
"""

import argparse
import os
import subprocess
import sys

from config import CpuSettings as CpuSettings
from config import get_script_dir as get_script_dir
from config import parse_config as parse_config
from config import parse_ints as parse_ints
from perfetto_cpu_analysis import run_analysis as run_analysis

ADB_CMD = "adb"
CPU_FREQ_GOVERNOR = [
    "conservative",
    "ondemand",
    "performance",
    "powersave",
    "schedutil",
    "userspace",
]

def init_arguments():
  parser = argparse.ArgumentParser(description='Analyze CPU perf.')
  parser.add_argument('-f', '--configfile', dest='config_file',
                      default=get_script_dir() + '/pixel6.config', type=argparse.FileType('r'),
                      help='CPU config file', )
  parser.add_argument('-c', '--cpusettings', dest='cpusettings', action='store',
                      default='default',
                      help='CPU Settings to apply')
  parser.add_argument('-s', '--serial', dest='serial', action='store',
                      help='android device serial number')
  parser.add_argument('-w', '--waittime', dest='waittime', action='store',
                      help='wait for up to this time in secs to after CPU settings change.' +\
                           ' Default is to wait forever until user press any key')
  parser.add_argument('-l', '--perfetto_tool_location', dest='perfetto_tool_location',
                      action='store', default='./external/perfetto/tools',
                      help='Location of perfetto/tool directory.' +\
                           ' Default is ./external/perfetto/tools.')
  parser.add_argument('-o', '--perfetto_output', dest='perfetto_output', action='store',
                      help='Output trace file for perfetto. If this is not specified' +\
                      ', perfetto tracing will not run.')
  parser.add_argument('-t', '--traceduration', dest='traceduration', action='store',
                      default='5',
                      help='duration of trace capturing. Default is 5 sec.')
  parser.add_argument('-p', '--permanent', dest='permanent',
                      action='store_true',
                      default=False,
                      help='change CPU settings permanently and do not restore original setting')
  parser.add_argument('-g', '--governor', dest='governor', action='store',
                      choices=CPU_FREQ_GOVERNOR,
                      help='CPU governor to apply, overrides the CPU Settings')
  return parser.parse_args()

def run_adb_cmd(cmd):
  r = subprocess.check_output(ADB_CMD + ' ' + cmd, shell=True)
  return r.decode("utf-8")

def run_adb_shell_cmd(cmd):
  return run_adb_cmd('shell ' + cmd)

def run_shell_cmd(cmd):
  return subprocess.check_output(cmd, shell=True)

def read_device_cpusets():
  # needs '' to keep command complete through adb shell
  r = run_adb_shell_cmd("'find /dev/cpuset -name cpus -print -exec cat {} \;'")
  lines = r.split('\n')
  key = None
  sets = {}
  for l in lines:
    l = l.strip()
    if l.find("/dev/cpuset/") == 0:
      l = l.replace("/dev/cpuset/", "")
      l = l.replace("cpus", "")
      l = l.replace("/", "")
      key = l
    elif key is not None:
      cores = parse_ints(l)
      sets[key] = cores
      key = None
  return sets

def read_device_governors():
  governors = {}
  r = run_adb_shell_cmd("'find /sys/devices/system/cpu/cpufreq -name scaling_governor" +\
                        " -print -exec cat {} \;'")
  lines = r.split('\n')
  key = None
  for l in lines:
    l = l.strip()
    if l.find("/sys/devices/system/cpu/cpufreq/") == 0:
      l = l.replace("/sys/devices/system/cpu/cpufreq/", "")
      l = l.replace("/scaling_governor", "")
      key = l
    elif key is not None:
      governors[key] = str(l)
      key = None
  return governors

def read_device_cpu_settings():
  settings = CpuSettings()

  settings.cpusets = read_device_cpusets()

  settings.onlines = parse_ints(run_adb_shell_cmd("cat /sys/devices/system/cpu/online"))
  offline_cores = parse_ints(run_adb_shell_cmd("cat /sys/devices/system/cpu/offline"))
  settings.allcores.extend(settings.onlines)
  settings.allcores.extend(offline_cores)
  settings.allcores.sort()
  settings.governors = read_device_governors()

  return settings

def wait_for_user_input(msg):
  return input(msg)

def get_cores_to_offline(settings, deviceSettings = None):
  allcores = []
  allcores.extend(settings.allcores)
  if deviceSettings is not None:
    for core in deviceSettings.allcores:
      if not core in allcores:
        allcores.append(core)
  allcores.sort()
  for core in settings.onlines:
    allcores.remove(core) # remove online cores
  return allcores

def write_sysfs(node, contents):
  run_adb_shell_cmd("chmod 666 {}".format(node))
  run_adb_shell_cmd("\"echo '{}' > {}\"".format(contents, node))

def enable_disable_cores(onlines, offlines):
  for core in onlines:
    write_sysfs("/sys/devices/system/cpu/cpu{}/online".format(core), '1')
  for core in offlines:
    write_sysfs("/sys/devices/system/cpu/cpu{}/online".format(core), '0')

def update_cpusets(settings, offlines, deviceSettings):
  cpusets = {}
  if deviceSettings is not None:
    for k in deviceSettings.cpusets:
      cpusets[k] = deviceSettings.cpusets[k].copy()
  for k in settings.cpusets:
    if deviceSettings is not None:
      if not k in deviceSettings.cpusets:
        print("CPUSet {} not existing in device, ignore".format(k))
        continue
    cpusets[k] = settings.cpusets[k].copy()
  for k in cpusets:
    if k == "": # special case, no need to touch
      continue
    cores = cpusets[k]
    for core in offlines:
      if core in cores:
        cores.remove(core)
    cores_string = []
    for core in cores:
      cores_string.append(str(core))
    write_sysfs("/dev/cpuset/{}/cpus".format(k), ','.join(cores_string))

def update_policies(settings, deviceSettings = None):
  policies = {}
  if len(settings.governors) == 0:
    # at least governor should be set
    for k in deviceSettings.governors:
      policies[k] = settings.governor
  else:
    policies = settings.governors

  print("Setting policies:{}".format(policies))
  for k in policies:
    try:
      write_sysfs("/sys/devices/system/cpu/cpufreq/{}/scaling_governor".\
                   format(k), policies[k])
    except subprocess.CalledProcessError as e:
      # policies can be gone when all cpus are gone
      print("Cannot set policy {}".format(k))

def apply_cpu_settings(settings, deviceSettings = None):
  print("Applying CPU Settings:\n" + 30 * "-")
  print(str(settings))
  offlines = get_cores_to_offline(settings, deviceSettings)
  print("Cores going offline:{}".format(offlines))

  update_cpusets(settings, offlines, deviceSettings)
  # change cores
  enable_disable_cores(settings.onlines, offlines)
  # change policies
  update_policies(settings, deviceSettings)


def main():
  global ADB_CMD
  args = init_arguments()
  if args.serial is not None:
    ADB_CMD = "%s %s" % ("adb -s", args.serial)

  print("config file:{}".format(args.config_file.name))
  run_adb_cmd('root')

  # parse config
  cpuConfig = parse_config(args.config_file)
  if args.governor is not None:
    for k in cpuConfig.configs:
      cpuConfig.configs[k].governor = args.governor
  print("CONFIG:\n" + 30 * "-" + "\n" + str(cpuConfig))

  # read CPU settings
  deviceSettings = read_device_cpu_settings();
  print("Current device CPU settings:\n" + 30 * "-" + "\n"  + str(deviceSettings))

  # change CPU setting
  newCpuSettings = cpuConfig.configs.get(args.cpusettings)
  if newCpuSettings is None:
    print("Cannot find cpusettings {}".format(args.cpusettings))
  apply_cpu_settings(newCpuSettings, deviceSettings)

  # wait
  if args.waittime is None:
    wait_for_user_input("Press enter to start capturing perfetto trace")
  else:
    print ("Wait {} secs before capturing perfetto trace".format(args.waittime))
    sleep(int(args.waittime))

  if args.perfetto_output is not None:
    # run perfetto & analysis if output file is specified
    serial_str = ""
    if args.serial is not None:
      serial_str = "--serial {}".format(args.serial)
    outputName = args.perfetto_output
    if not outputName.endswith('.pftrace'):
      outputName = outputName + '.pftrace'
    print("Starting capture to {}".format(outputName))
    r = run_shell_cmd("{}/record_android_trace {} -t {}s -o {} sched freq idle".\
                  format(args.perfetto_tool_location, serial_str, args.traceduration, outputName))
    print(r)
    # analysis
    run_analysis(outputName, cpuConfig, newCpuSettings)

  # restore
  if not args.permanent:
    apply_cpu_settings(deviceSettings)

if __name__ == '__main__':
  main()
