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
import sys


def main(argv):
  arch = argv[1]

  with open(argv[2]) as json_file:
    syscalls = json.load(json_file)

  for name in sorted(syscalls.keys()):
    syscall = syscalls[name]
    if arch in syscall:
      print('__do_%s' % (syscall[arch]["entry"]))

  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv))
