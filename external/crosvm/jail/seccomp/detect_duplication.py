#!/usr/bin/env python3
# -*- coding: utf-8 -*-
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

import sys

data = list(map(str.strip, sys.stdin.readlines()))

last_seen = dict()

for i, l in enumerate(data):
    if not l.startswith("#") and ":" in l:
        syscall = l.split()[0]
        if syscall in last_seen:
            sys.exit("syscall %s redefined in L%d(previous definition in L%d)" % (syscall, i, last_seen[syscall]))
        last_seen[syscall] = i
    print(l)