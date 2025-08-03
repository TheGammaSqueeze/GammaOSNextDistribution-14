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
"""Tool to analyze CPU performance from perfetto trace
This too assumes that core clocks are fixed.
It will not give accurate results if clock frequecies change.
Should install perfetto: $ pip install perfetto
"""

import argparse
import sys

from perfetto.trace_processor import TraceProcessor

from config import get_script_dir as get_script_dir
from config import parse_config as parse_config
from config import add_line_with_indentation

# Get total idle time and active time from each core
QUERY_SCHED_CORE_SUM = """SELECT
  cpu AS core,
  SUM (CASE
        WHEN utid = 0 THEN 0
        ELSE dur
  END) AS activeTime,
  SUM (CASE
        WHEN utid = 0 THEN dur
        ELSE 0
  END) AS idleTime
FROM sched
GROUP BY cpu
ORDER BY cpu"""

class CoreLoad:
  def __init__(self, coreId, totalCycles):
    self.coreId = coreId
    self.totalCycles = totalCycles

class CPUExecutionInfo:
  def __init__(self, name):
    self.name = name
    self.perCoreLoads = {} # key: core, value :CoreLoad

  def addCoreLoad(self, load):
    self.perCoreLoads[load.coreId] = load

  def getCoreCycles(self, coreId):
    l = self.perCoreLoads.get(coreId)
    if l is None:
      return 0
    return l.totalCycles

  def getTotalCycles(self):
    sum = 0
    for c in self.perCoreLoads:
      l = self.perCoreLoads[c]
      sum += l.totalCycles
    return sum

class ThreadInfo(CPUExecutionInfo):
  def print(self, totalCpuCycles, perCoreTotalCycles, loadPercentile):
    indentation = 2
    msgs = []
    totalCpuLoad = float(self.getTotalCycles()) / totalCpuCycles * 100.0
    activeCpuLoad = totalCpuLoad / loadPercentile * 100.0
    add_line_with_indentation(msgs,
                              ("{}: total: {:.3f}% active: {:.3f}%"\
                               .format(self.name, totalCpuLoad, activeCpuLoad)), indentation)
    add_line_with_indentation(msgs, 50 * "-", indentation)
    for c in sorted(self.perCoreLoads):
      l = self.perCoreLoads[c]
      coreLoad = float(l.totalCycles) / perCoreTotalCycles[c] * 100.0
      add_line_with_indentation(msgs,
                                "{:<10} {:<15}".format("Core {}".format(c),
                                                       "{:.3f}%".format(coreLoad)),
                                indentation)

    print("".join(msgs))

class ProcessInfo(CPUExecutionInfo):
  def __init__(self, name):
    super().__init__(name)
    self.threads = [] # ThreadInfo

  def get_filtered_threads(self, threadNames):
    threads = list(filter(
        lambda t: max(map(lambda filterName: t.name.find(filterName), threadNames)) > -1,
        self.threads))

    return threads

  def print(self, totalCpuCycles, perCoreTotalCycles, loadPercentile, showThreads=False):
    msgs = []
    totalCpuLoad = float(self.getTotalCycles()) / totalCpuCycles * 100.0
    activeCpuLoad = totalCpuLoad / loadPercentile * 100.0
    msgs.append("{}: total: {:.3f}% active: {:.3f}%"\
                .format(self.name, totalCpuLoad, activeCpuLoad))
    msgs.append("\n" + 50 * "-")
    for c in sorted(self.perCoreLoads):
      l = self.perCoreLoads[c]
      coreLoad = float(l.totalCycles) / perCoreTotalCycles[c] * 100.0
      msgs.append("\n{:<10} {:<15}".format("Core {}".format(c), "{:.3f}%".format(coreLoad)))

    print(''.join(msgs))

    if showThreads:
      self.threads.sort(reverse = True, key = lambda p : p.getTotalCycles())
      for t in self.threads:
        t.print(totalCpuCycles, perCoreTotalCycles, loadPercentile)

    print('\n')


class TotalCoreLoad:
  def __init__(self, coreId, activeTime, idleTime):
    self.coreId = coreId
    self.activeTime = activeTime
    self.idleTime = idleTime
    self.loadPercentile = float(activeTime) / (idleTime + activeTime) * 100.0

class SystemLoad:
  def __init__(self):
    self.totalLoads = [] # TotalCoreLoad
    self.totalLoad = 0.0
    self.processes = [] # ProcessInfo

  def addTimeMeasurements(self, coreData, allCores):
    coreLoads = {} # k: core, v: TotalCoreLoad
    maxTotalTime = 0
    for entry in coreData:
      coreId = entry.core
      activeTime = entry.activeTime
      idleTime = entry.idleTime
      totalTime = activeTime + idleTime
      if maxTotalTime < totalTime:
        maxTotalTime = totalTime
      load = TotalCoreLoad(coreId, activeTime, idleTime)
      coreLoads[coreId] = load
    for c in allCores:
      if coreLoads.get(c) is not None:
        continue
      # this core was not used at all. So add it with idle only
      coreLoads[c] = TotalCoreLoad(c, 0, maxTotalTime)
    for c in sorted(coreLoads):
      self.totalLoads.append(coreLoads[c])

  def get_filtered_processes(self, process_names):
      processPerName = {}
      for name in process_names:
        processes = list(filter(lambda p: p.name.find(name) > -1, self.processes))
        if len(processes) > 0:
          processPerName[name] = processes
      return processPerName

  def print(self, cpuConfig, numTopN, filterProcesses, filterThreads):
    print("\nTime based CPU load\n" + 30 * "=")
    loadXClkSum = 0.0
    maxCapacity = 0.0
    perCoreCpuCycles = {}
    totalCpuCycles = 0
    maxCpuGHz = 0.0
    print("{:<10} {:<15} {:<15} {:<15}\n{}".\
          format("CPU", "CPU Load %", "CPU Usage", "Max CPU Freq.", 60 * "-"))
    for l in self.totalLoads:
      coreMaxFreqGHz = float(cpuConfig.coreMaxFreqKHz[l.coreId]) / 1e6
      coreIdStr = "Core {}".format(l.coreId)
      loadPercentileStr = "{:.3f}%".format(l.loadPercentile)
      loadUsageStr = "{:.3f} GHz".format(l.loadPercentile * coreMaxFreqGHz / 100)
      coreMaxFreqStr = "{:.3f} GHz".format(coreMaxFreqGHz)
      print("{:<10} {:<15} {:<15} {:<15}".\
            format(coreIdStr, loadPercentileStr, loadUsageStr, coreMaxFreqStr))
      maxCpuGHz += coreMaxFreqGHz
      loadXClkSum += l.loadPercentile * coreMaxFreqGHz
      perCoreCpuCycles[l.coreId] = (l.activeTime + l.idleTime) * coreMaxFreqGHz
      totalCpuCycles += perCoreCpuCycles[l.coreId]
    loadPercentile = float(loadXClkSum) / maxCpuGHz
    print("\nTotal Load: {:.3f}%, {:.2f} GHz with system max {:.2f} GHz".\
          format(loadPercentile, loadPercentile * maxCpuGHz / 100.0, maxCpuGHz))

    self.processes.sort(reverse = True, key = lambda p : p.getTotalCycles())
    if filterThreads is not None:
      print("\nFiltered threads\n" + 30 * "=")
      processPerName = self.get_filtered_processes(filterThreads.keys())
      if len(processPerName) == 0:
        print("No process found matching filters.")
      for name in processPerName:
        for p in processPerName[name]:
          threads = p.get_filtered_threads(filterThreads[name])
          print("\n{}\n".format(p.name) + 30 * "-")
          for t in threads:
            t.print(totalCpuCycles, perCoreCpuCycles, loadPercentile)


    if filterProcesses is not None:
      print("\nFiltered processes\n" + 30 * "=")
      processPerName = self.get_filtered_processes(filterProcesses)
      if len(processPerName) == 0:
        print("No process found matching filters.")
      processes = sum(processPerName.values(), []) # flattens 2-D list
      processes.sort(reverse = True, key = lambda p : p.getTotalCycles())
      for p in processes:
        p.print(totalCpuCycles, perCoreCpuCycles, loadPercentile, showThreads=True)

    print("\nTop processes\n" + 30 * "=")
    for p in self.processes[:numTopN]:
      p.print(totalCpuCycles, perCoreCpuCycles, loadPercentile)

def init_arguments():
  parser = argparse.ArgumentParser(description='Analyze CPU perf.')
  parser.add_argument('-f', '--configfile', dest='config_file',
                      default=get_script_dir() + '/pixel6.config', type=argparse.FileType('r'),
                      help='CPU config file', )
  parser.add_argument('-c', '--cpusettings', dest='cpusettings', action='store',
                      default='default',
                      help='CPU Settings to apply')
  parser.add_argument('-n', '--number_of_top_processes', dest='number_of_top_processes',
                      action='store', type=int, default=5,
                      help='Number of processes to show in performance report')
  parser.add_argument('-p', '--process-name', dest='process_names', action='append',
                      help='Name of process to filter')
  parser.add_argument('-t', '--thread-name', dest='thread_names', action='append',
                      help='Name of thread to filter. Format: <process-name>:<thread-name>')
  parser.add_argument('trace_file', action='store', nargs=1,
                      help='Perfetto trace file to analyze')
  return parser.parse_args()

def get_core_load(coreData, cpuConfig):
  cpuFreqKHz = cpuConfig.coreMaxFreqKHz[coreData.id]
  if coreData.metrics.HasField('avg_freq_khz'):
    cpuFreqKHz = coreData.metrics.avg_freq_khz
  cpuCycles = cpuFreqKHz * coreData.metrics.runtime_ns / 1000000 # unit should be Hz * s
  return CoreLoad(coreData.id, cpuCycles)

def run_analysis(
    traceFile,
    cpuConfig,
    cpuSettings,
    numTopN=5,
    filterProcesses=None,
    filterThreads=None
):
  tp = TraceProcessor(file_path=traceFile)

  systemLoad = SystemLoad()
  # get idle and active times per each cores
  core_times = tp.query(QUERY_SCHED_CORE_SUM)
  systemLoad.addTimeMeasurements(core_times, cpuSettings.onlines)

  cpu_metrics = tp.metric(['android_cpu']).android_cpu
  for p in cpu_metrics.process_info:
    info = ProcessInfo(p.name)
    for c in p.core:
      l = get_core_load(c, cpuConfig)
      info.addCoreLoad(l)
    for t in p.threads:
      thread_info = ThreadInfo(t.name)
      for tc in t.core:
        tl = get_core_load(tc, cpuConfig)
        thread_info.addCoreLoad(tl)
      info.threads.append(thread_info)
    systemLoad.processes.append(info)

  systemLoad.print(cpuConfig, numTopN, filterProcesses, filterThreads)

def main():
  args = init_arguments()

  # parse config
  cpuConfig = parse_config(args.config_file)
  cpuSettings = cpuConfig.configs.get(args.cpusettings)
  if cpuSettings is None:
    print("Cannot find cpusettings {}".format(args.cpusettings))
    return

  threadsPerProcess = None
  if args.thread_names is not None:
    threadsPerProcess = {}
    for threadName in args.thread_names:
      names = threadName.split(':')
      if len(names) != 2:
        print(" Skipping {}: invalid format".format(threadName))
        continue
      process, thread = names
      if process not in threadsPerProcess:
        threadsPerProcess[process] = []
      threadsPerProcess[process].append(thread)
    if len(threadsPerProcess) == 0:
      threadsPerProcess = None

  run_analysis(args.trace_file[0],
               cpuConfig,
               cpuSettings,
               args.number_of_top_processes,
               args.process_names,
               threadsPerProcess)

if __name__ == '__main__':
  main()
