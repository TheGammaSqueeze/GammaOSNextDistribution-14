#!/usr/bin/env python3
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
#

"""ipc.py: Capture the Instructions per Cycle (IPC) of the system during a
           specified duration.

  Example:
    ./ipc.py
    ./ipc.py 2 20          # Set interval to 2 secs and total duration to 20 secs
    ./ipc.py -p 284 -C 4   # Only profile the PID 284 while running on core 4
    ./ipc.py -c 'sleep 5'  # Only profile the command to run

  Result looks like:
    K_CYCLES   K_INSTR      IPC
    36840      14138       0.38
    70701      27743       0.39
    104562     41350       0.40
    138264     54916       0.40
"""

import io
import logging
import subprocess
import sys
import time

from simpleperf_utils import (
        AdbHelper, BaseArgumentParser, get_target_binary_path, log_exit)

def start_profiling(adb, args, target_args):
    """Start simpleperf process on device."""
    shell_args = ['simpleperf', 'stat', '-e', 'cpu-cycles',
            '-e', 'instructions', '--interval', str(args.interval * 1000),
            '--duration', str(args.duration)]
    shell_args += target_args
    adb_args = [adb.adb_path, 'shell'] + shell_args
    logging.info('run adb cmd: %s' % adb_args)
    return subprocess.Popen(adb_args, stdout=subprocess.PIPE)

def capture_stats(adb, args, stat_subproc):
    """Capture IPC profiling stats or stop profiling when user presses Ctrl-C."""
    try:
        print("%-10s %-10s %5s" % ("K_CYCLES", "K_INSTR", "IPC"))
        cpu_cycles = 0
        for line in io.TextIOWrapper(stat_subproc.stdout, encoding="utf-8"):
            if 'cpu-cycles' in line:
                if args.cpu == None:
                    cpu_cycles = int(line.split()[0].replace(",", ""))
                    continue
                columns = line.split()
                if args.cpu == int(columns[0]):
                    cpu_cycles = int(columns[1].replace(",", ""))
            elif 'instructions' in line:
                if cpu_cycles == 0: cpu_cycles = 1 # PMCs are broken, or no events
                ins = -1
                columns = line.split()
                if args.cpu == None:
                    ins = int(columns[0].replace(",", ""))
                elif args.cpu == int(columns[0]):
                    ins = int(columns[1].replace(",", ""))
                if ins >= 0:
                    print("%-10d %-10d %5.2f" %
                            (cpu_cycles / 1000, ins / 1000, ins / cpu_cycles))

    except KeyboardInterrupt:
        stop_profiling(adb)
        stat_subproc = None

def stop_profiling(adb):
    """Stop profiling by sending SIGINT to simpleperf and wait until it exits."""
    has_killed = False
    while True:
        (result, _) = adb.run_and_return_output(['shell', 'pidof', 'simpleperf'])
        if not result:
            break
        if not has_killed:
            has_killed = True
            adb.run_and_return_output(['shell', 'pkill', '-l', '2', 'simpleperf'])
        time.sleep(1)

def capture_ipc(args):
    # Initialize adb and verify device
    adb = AdbHelper(enable_switch_to_root=True)
    if not adb.is_device_available():
        log_exit('No Android device is connected via ADB.')
    is_root_device = adb.switch_to_root()
    device_arch = adb.get_device_arch()

    if args.pid:
       (result, _) = adb.run_and_return_output(['shell', 'ls', '/proc/%s' % args.pid])
       if not result:
           log_exit("Pid '%s' does not exist" % args.pid)

    target_args = []
    if args.cpu is not None:
        target_args += ['--per-core']
    if args.pid:
        target_args += ['-p', args.pid]
    elif args.command:
        target_args += [args.command]
    else:
        target_args += ['-a']

    stat_subproc = start_profiling(adb, args, target_args)
    capture_stats(adb, args, stat_subproc)

def main():
    parser = BaseArgumentParser(description=__doc__)
    parser.add_argument('-C', '--cpu', type=int, help='Capture IPC only for this CPU core')
    process_group = parser.add_mutually_exclusive_group()
    process_group.add_argument('-p', '--pid', help='Capture IPC only for this PID')
    process_group.add_argument('-c', '--command', help='Capture IPC only for this command')
    parser.add_argument('interval', nargs='?', default=1, type=int, help='sampling interval in seconds')
    parser.add_argument('duration', nargs='?', default=10, type=int, help='sampling duration in seconds')

    args = parser.parse_args()
    if args.interval > args.duration:
        log_exit("interval cannot be greater than duration")

    capture_ipc(args)

if __name__ == '__main__':
    main()
