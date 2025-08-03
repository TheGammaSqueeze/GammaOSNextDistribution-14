#!/usr/bin/python
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

import json
import sys


def _print_header(arch):
  print("""\
/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef BERBERIS_GUEST_OS_PRIMITIVES_GEN_SYSCALL_NUMBERS_%s_H_
#define BERBERIS_GUEST_OS_PRIMITIVES_GEN_SYSCALL_NUMBERS_%s_H_

namespace berberis {""" % (arch, arch))


def _print_footer(arch):
  print("""\

}  // namespace berberis

#endif  // BERBERIS_GUEST_OS_PRIMITIVES_GEN_SYSCALL_NUMBERS_%s_H_""" % (arch))


def _print_enum(arch, kernel_syscalls):
  print("""\

enum {""")

  for nr, syscall in sorted(kernel_syscalls.items()):
    if arch in syscall:
      assert nr.startswith('__')
      print('  GUEST_%s = %s,' % (nr[2:], syscall[arch]['id']))

  print('};')


def _print_mapping(name, src_arch, dst_arch, kernel_syscalls):
  print("""\

inline int %s(int nr) {
  switch (nr) {""" % (name))

  for nr, syscall in sorted(kernel_syscalls.items()):
    if src_arch in syscall:
      if dst_arch in syscall:
        print('    case %s:  // %s' % (syscall[src_arch]['id'], nr))
        print('      return %s;' % (syscall[dst_arch]['id']))
      else:
        print('    case %s:  // %s - missing on %s' % (syscall[src_arch]['id'], nr, dst_arch))
        print('      return -1;')

  print("""\
    default:
      return -1;
  }
}""")


def main(argv):
  src_arch = argv[1]
  dst_arch = argv[2]

  with open(argv[3]) as json_file:
    kernel_syscalls = json.load(json_file)

  # TODO(b/232598137): merge custom syscalls?

  display_src_arch = src_arch.upper()

  _print_header(display_src_arch)
  _print_enum(src_arch, kernel_syscalls)
  _print_mapping('ToHostSyscallNumber', src_arch, dst_arch, kernel_syscalls)
  _print_mapping('ToGuestSyscallNumber', dst_arch, src_arch, kernel_syscalls)
  _print_footer(display_src_arch)

  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv))
