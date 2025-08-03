#!/usr/bin/python3
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

#
# This scripts prints a version 4 (random) UUID as a series of comma-separated
# bytes in braces, so it can be easily used when developing a new CHPP client /
# service.
#

import uuid

u = uuid.uuid4().hex
print("Generated version 4 (random) UUID is " + u)

print("{", end="")
loc = 0
while loc < len(u) - 2:  # loop through UUID bytes except last byte
    print("0x" + u[loc:loc+2] + ", ", end="")
    loc += 2
print("0x" + u[loc:loc+2] + "}")
