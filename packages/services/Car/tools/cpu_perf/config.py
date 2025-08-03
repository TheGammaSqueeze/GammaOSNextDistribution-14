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
"""Utility to read CPU configurations from config file or device.
"""

import os
import sys

class CpuSettings:
  def __init__(self):
    self.allcores = [] # core id (int)
    self.onlines = [] # CPU cores online
    self.governor = ""
    self.governors = {} # key: policy, value: governor
    self.cpusets = {} # key: name, value: [] core number

  def __str__(self):
    strs = []
    strs.append("CpuSettings:{")
    add_line_with_indentation(strs, "allcores:", 4)
    strs.append(str(self.allcores))
    add_line_with_indentation(strs, "onlines:", 4)
    strs.append(str(self.onlines))
    add_line_with_indentation(strs, "governor:", 4)
    strs.append(str(self.governor))
    add_line_with_indentation(strs, "governors:[", 4)
    for k in self.governors:
      add_line_with_indentation(strs, str(k), 8)
      strs.append('=')
      strs.append(str(self.governors[k]))
    strs.append("]")
    add_line_with_indentation(strs, "cpusets:[", 4)
    for k in self.cpusets:
      add_line_with_indentation(strs, str(k), 8)
      strs.append('=')
      strs.append(str(self.cpusets[k]))
    strs.append("]")
    strs.append("}\n")
    return ''.join(strs)

class CpuConfig:
  def __init__(self):
    self.allcores = [] # core id (int)
    self.coreMaxFreqKHz = {} # key: core id (int), # value: max freq (int)
    self.configs = {} # key: name, value: CpuSettings

  def __str__(self):
    strs = []
    strs.append("CpuConfig:[")
    add_line_with_indentation(strs, "allcores:", 2)
    strs.append(str(self.allcores))
    add_line_with_indentation(strs, "coreMaxFreqKHz:", 2)
    strs.append(str(self.coreMaxFreqKHz))
    for k in self.configs:
      add_line_with_indentation(strs, str(k), 2)
      strs.append(':')
      strs.append(str(self.configs[k]))
    strs.append("]")
    return ''.join(strs)

def parse_ints(word):
  # valid inputs: 0-7, 0-1,4-7, 0,1
  word = word.strip()
  if word == "":
    return []
  values = []
  pairs = word.split(',')
  for pair in pairs:
    min_max = pair.split('-')
    if len(min_max) == 2:
      min = int(min_max[0])
      max = int(min_max[1])
      if min >= max:
        raise ValueError('min {} larger than max {}'.format(min, max))
      for i in range(min, max + 1):
        values.append(i)
    else:
      values.append(int(pair))
  values.sort()
  return values

def parse_config(configFile):
  lines = configFile.readlines()
  config = CpuConfig()
  allcores = []
  current_settings = None
  default_governor = None
  i = -1
  for line in lines:
    i = i + 1
    line = line.strip()
    if len(line) == 0: # allows empty line
      continue
    if line[0] == '#': # comment
      continue
    try:
      words = line.split(':')
      if words[0] == "allcores":
        allcores = parse_ints(words[1])
        config.allcores = allcores
      elif words[0] == "core_max_freq_khz":
        pair = words[1].split('=')
        if len(pair) != 2:
          raise ValueError("wrong config: {}".format(words[1]))
        cores = parse_ints(pair[0])
        freq = int(pair[1])
        for core in cores:
          config.coreMaxFreqKHz[core] = freq
      elif words[0] == "default_governor":
        default_governor = words[1]
      elif words[0] == "case":
        current_settings = CpuSettings()
        current_settings.allcores = allcores
        current_settings.governor = default_governor
        config.configs[words[1]] = current_settings
      elif words[0] == "online":
        current_settings.onlines = parse_ints(words[1])
      elif words[0] == "offline":
        current_settings.onlines.extend(allcores)
        offlines = parse_ints(words[1])
        for cpu in offlines:
          current_settings.onlines.remove(cpu)
      elif words[0] == "cpuset":
        cpuset_pair = words[1].split('=')
        if len(cpuset_pair) == 1:
          current_settings.cpusets[""] = parse_ints(cpuset_pair[0])
        else:
          current_settings.cpusets[cpuset_pair[0]] = parse_ints(cpuset_pair[1])
      elif words[0] == "governor":
        current_settings.governor = words[1]
      else:
        raise ValueError("Unknown keyword {}".format(words[0]))
    except Exception as e:
      print("Cannot parse line {}: {}".format(i, line))
      raise e

  return config

def get_script_dir():
  return os.path.dirname(os.path.realpath(sys.argv[0]))

def add_line_with_indentation(strs, msg="", spaces=1):
  strs.append("\n" + spaces * " " + msg)
