#!/usr/bin/python
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
#

import json
import os.path
import sys


# <num> <abi> <name> [<entry point> [<oabi compat entry point>]]
def _parse_arm_syscalls(tbl_file):
  res = {}

  for line in tbl_file:
    line = line.strip()
    if line.startswith('#'):
      continue

    words = line.split()
    id = words[0]
    abi = words[1]
    name = '__NR_' + words[2]

    if abi == 'oabi':
      continue
    assert abi == 'common' or abi == 'eabi'

    if len(words) < 4:
      # TODO(b/232598137): ok? missing syscall should SIGILL, sys_ni_syscall returns ENOSYS!
      entry = 'sys_ni_syscall'
    else:
      entry = words[3]
      # TODO(b/232598137): ok? check if wrappers somehow change action!
      if entry.endswith('_wrapper'):
        entry = entry[:-8]

    res[name] = {'id': id, 'entry': entry}

  return res


# <number> <abi> <name> <entry point> <compat entry point>
def _parse_x86_syscalls(tbl_file):
  res = {}

  for line in tbl_file:
    line = line.strip()
    if line.startswith('#'):
      continue

    words = line.split()
    id = words[0]
    abi = words[1]
    name = '__NR_' + words[2]

    assert abi == 'i386'

    if len(words) < 4:
      # TODO(b/232598137): ok? missing syscall should SIGILL, sys_ni_syscall returns ENOSYS!
      entry = 'sys_ni_syscall'
    else:
      entry = words[3]

    res[name] = {'id': id, 'entry': entry}

  return res


# <number> <abi> <name> <entry point>[/<qualifier>]
def _parse_x86_64_syscalls(tbl_file):
  res = {}

  for line in tbl_file:
    line = line.strip()
    if not line:
      continue
    if line.startswith('#'):
      continue

    words = line.split()
    id = words[0]
    abi = words[1]
    name = '__NR_' + words[2]

    if abi == 'x32':
      continue
    assert abi == 'common' or abi == '64'

    if len(words) < 4:
      # TODO(berberis): Is it ok? Missing syscall should SIGILL, sys_ni_syscall returns ENOSYS.
      entry = 'sys_ni_syscall'
    else:
      entry = words[3]

    res[name] = {'id': id, 'entry': entry}

  return res


# #define __NR_read 63
# __SYSCALL(__NR_read, sys_read)
def _parse_unistd_syscalls(header_file):
  res = {}

  defines = {}
  syscalls = {}

  prefix = ''
  cond = []

  for line in header_file:
    line = line.strip()
    if not line:
      continue

    # handle line concatenation
    if line.endswith('\\'):
      prefix += line[:-1]
      continue
    if prefix:
      line = prefix + line
      prefix = ''
    # add new conditional
    if line in [
        '#ifndef __SYSCALL',
        '#if __BITS_PER_LONG == 32 || defined(__SYSCALL_COMPAT)',
        '#if defined(__SYSCALL_COMPAT) || __BITS_PER_LONG == 32',
        '#ifdef __SYSCALL_COMPAT',
        '#ifdef __ARCH_WANT_SYNC_FILE_RANGE2',
        '#if __BITS_PER_LONG == 32',
        '#ifdef __NR3264_stat',
        ]:
      cond.append(False)
      continue
    if line in [
        '#if defined(__ARCH_WANT_TIME32_SYSCALLS) || __BITS_PER_LONG != 32',
        '#ifdef __ARCH_WANT_RENAMEAT',
        '#if defined(__ARCH_WANT_NEW_STAT) || defined(__ARCH_WANT_STAT64)',
        '#ifdef __ARCH_WANT_SET_GET_RLIMIT',
        '#ifndef __ARCH_NOMMU',
        '#ifdef __ARCH_WANT_SYS_CLONE3',
        '#if __BITS_PER_LONG == 64 && !defined(__SYSCALL_COMPAT)',
        '#ifdef __ARCH_WANT_MEMFD_SECRET',
        ]:
      cond.append(True)
      continue
    if line.startswith('#if'):
      assert False

    # change current conditional
    if line.startswith('#endif'):
      cond.pop()
      continue
    if line.startswith('#else'):
      cond.append(not cond.pop())
      continue
    if line.startswith('#elif'):
      assert False

    # check current conditional
    if False in cond:
      continue

    # defines
    if line.startswith('#define __NR'):
      words = line.split()
      defines[words[1]] = words[2]
      continue

    # syscall
    if line.startswith('__SYSCALL('):
      words = line.replace('(', ' ').replace(')', ' ').replace(',', ' ').split()
      syscalls[words[1]] = words[2]
      continue
    if line.startswith('__SC_COMP('):
      words = line.replace('(', ' ').replace(')', ' ').replace(',', ' ').split()
      syscalls[words[1]] = words[2]
      continue
    if line.startswith('__SC_3264('):
      words = line.replace('(', ' ').replace(')', ' ').replace(',', ' ').split()
      syscalls[words[1]] = words[3]
      continue
    if line.startswith('__SC_COMP_3264('):
      words = line.replace('(', ' ').replace(')', ' ').replace(',', ' ').split()
      syscalls[words[1]] = words[3]
      continue
    if line.startswith('__S'):
      assert False

  for name, entry in syscalls.items():
    id = defines[name]

    # apply redefines
    for name_to, name_from in defines.items():
      if name == name_from:
        name = name_to
        break

    assert name.startswith('__NR_')

    res[name] = {'id': id, 'entry': entry}

  return res


# asmlinkage long sys_read(unsigned int fd, char __user *buf, size_t count);
def _parse_protos(header_file):
  res = {}

  prefix = ''
  proto = ''

  for line in header_file:
    line = line.strip()
    if not line:
      continue

    # handle line concatenation (to skip protos within multiline #define)
    if line.endswith('\\'):
      prefix += line[:-1]
      continue
    if prefix:
      line = prefix + line
      prefix = ''

    if line.startswith('asmlinkage long '):
      assert not proto
      proto = line
    elif proto:
      proto += ' '
      proto += line
    else:
      continue

    if ');' in proto:
      # As arch might have specific calling conventions, so prototype might be not precise.
      # We'll extract precise prototype from DWARF.
      # Here, we just distinguish entries with and without parameters.
      # unfortunately, few entries have multiple protos, selection configured by arch defines
      # (why not providing distinct entries instead???)
      entry = proto[16: proto.find('(')].strip()
      if '(void);' not in proto:
        res[entry] = True
      else:
        res.setdefault(entry, False)
      proto = ''

  return res


def main(argv):
  if len(argv) != 2:
    print('Usage: %s <kernel-src-dir>' % (argv[0]))
    return -1

  _KERNEL_SRC = argv[1]

  # syscall name, number, entry names

  with open(os.path.join(_KERNEL_SRC, 'arch/arm/tools/syscall.tbl')) as tbl_file:
    arm_syscalls = _parse_arm_syscalls(tbl_file)

  with open(os.path.join(_KERNEL_SRC, 'arch/x86/entry/syscalls/syscall_32.tbl')) as tbl_file:
    x86_syscalls = _parse_x86_syscalls(tbl_file)

  with open(os.path.join(_KERNEL_SRC, 'include/uapi/asm-generic/unistd.h')) as header_file:
    arm64_syscalls = _parse_unistd_syscalls(header_file)

  with open(os.path.join(_KERNEL_SRC, 'arch/x86/entry/syscalls/syscall_64.tbl')) as tbl_file:
    x86_64_syscalls = _parse_x86_64_syscalls(tbl_file)

  with open(os.path.join(_KERNEL_SRC, 'include/linux/syscalls.h')) as header_file:
    protos = _parse_protos(header_file)

  # riscv64 syscalls are also defined by unistd.h, so we can just copy over from arm64.
  riscv64_syscalls = arm64_syscalls

  all_syscalls = {}

  for arch, syscalls in [
      ('arm', arm_syscalls),
      ('x86', x86_syscalls),
      ('arm64', arm64_syscalls),
      ('x86_64', x86_64_syscalls),
      ('riscv64', riscv64_syscalls)]:
    for name, info in syscalls.items():
      all_syscalls.setdefault(name, {})[arch] = info

      if not protos.get(info['entry'], True):
        all_syscalls[name][arch]['params'] = []

  print(json.dumps(all_syscalls, indent=2, sort_keys=True))

  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv))
