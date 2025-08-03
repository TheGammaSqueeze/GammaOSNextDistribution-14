#! /usr/bin/env python3
#
# Copyright 2023, The Android Open Source Project
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

import argparse
from collections import namedtuple
import subprocess

try:
  from tqdm import tqdm
except:

  def tqdm(x):
    return x


ProcEntry = namedtuple('ProcEntry', 'pid, ppid, cmd, name, etc_args')


def get_mem_stats(
    zygote_pid,
    target_pid,
    target_name,
    imgdiag_path,
    boot_image,
    device_out_dir,
    host_out_dir,
):
  imgdiag_output_path = (
      f'{device_out_dir}/imgdiag_{target_name}_{target_pid}.txt'
  )
  cmd_collect = (
      'adb shell '
      f'"{imgdiag_path} --zygote-diff-pid={zygote_pid} --image-diff-pid={target_pid} '
      f'--output={imgdiag_output_path} --boot-image={boot_image} --dump-dirty-objects"'
  )

  try:
    subprocess.run(cmd_collect, shell=True, check=True)
  except:
    print('imgdiag call failed on:', target_pid, target_name)
    return

  cmd_pull = f'adb pull {imgdiag_output_path} {host_out_dir}'
  subprocess.run(cmd_pull, shell=True, check=True, capture_output=True)


def main():
  parser = argparse.ArgumentParser(
      description=(
          'Run imgdiag on selected processes and pull results from the device.'
      ),
      formatter_class=argparse.ArgumentDefaultsHelpFormatter,
  )
  parser.add_argument(
      'process_names',
      nargs='*',
      help='Process names to use. If none - dump all zygote children.',
  )
  parser.add_argument(
      '--boot-image',
      dest='boot_image',
      default='/data/misc/apexdata/com.android.art/dalvik-cache/boot.art',
      help='Path to boot.art',
  )
  parser.add_argument(
      '--zygote',
      default='zygote64',
      help='Zygote process name',
  )
  parser.add_argument(
      '--imgdiag',
      default='/apex/com.android.art/bin/imgdiag64',
      help='Path to imgdiag binary.',
  )
  parser.add_argument(
      '--device-out-dir',
      default='/data/local/tmp/imgdiag_out',
      help='Directory for imgdiag output files on the device.',
  )
  parser.add_argument(
      '--host-out-dir',
      default='./',
      help='Directory for imgdiag output files on the host.',
  )

  args = parser.parse_args()

  res = subprocess.run(
      args='adb shell ps -o pid:1,ppid:1,cmd:1,args:1',
      capture_output=True,
      shell=True,
      check=True,
      text=True,
  )

  proc_entries = []
  for line in res.stdout.splitlines()[1:]:  # skip header
    pid, ppid, cmd, name, *etc_args = line.split(' ')
    entry = ProcEntry(int(pid), int(ppid), cmd, name, etc_args)
    proc_entries.append(entry)

  zygote_entry = next(e for e in proc_entries if e.name == args.zygote)
  zygote_children = [e for e in proc_entries if e.ppid == zygote_entry.pid]

  if args.process_names:
    zygote_children = [e for e in proc_entries if e.name in args.process_names]

  print('\n'.join(str(e.pid) + ' ' + e.name for e in zygote_children))

  subprocess.run(
      args=f'adb shell "mkdir -p {args.device_out_dir}"', check=True, shell=True
  )
  subprocess.run(args=f'mkdir -p {args.host_out_dir}', check=True, shell=True)

  for entry in tqdm(zygote_children):
    get_mem_stats(
        zygote_pid=entry.ppid,
        target_pid=entry.pid,
        target_name=entry.name,
        imgdiag_path=args.imgdiag,
        boot_image=args.boot_image,
        device_out_dir=args.device_out_dir,
        host_out_dir=args.host_out_dir,
    )


if __name__ == '__main__':
  main()
