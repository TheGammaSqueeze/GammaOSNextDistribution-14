#!/usr/bin/env python3
#
# Copyright 2023 The Android Open Source Project
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
#

import numpy as np
from scipy import signal
import sys

KERNEL_Q = 512
KERNEL_A = 16

KAISER_BETA = 12.5

#
# Transfer function
#

a = KERNEL_A
q = KERNEL_Q
beta = KAISER_BETA

w = signal.kaiser(2 * a * q + 1, beta)
k = np.sinc(np.linspace(-a, a, 2 * a * q + 1)) * w

h = k[:-1].reshape((2 * a, q)).T
h = np.append(h, [np.roll(h[0], -1)], axis=0)
h = np.flip(h, axis=0)

d = h[1:] - h[:-1]

#
# File header
#

print("""\
/*
 * Copyright 2023 The Android Open Source Project
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

/* This file is auto-generated using "{}".  DO NOT EDIT. */

#include "asrc_tables.h"

namespace bluetooth::audio::asrc {{
""".format(sys.argv[0]))

#
# 32 bits tables
#

h32 = np.clip(np.rint(h * 2**31), -(1 << 31), (1 << 31) - 1).astype(np.int32)
d32 = np.clip(np.rint(d * 2**23), -(1 << 23), (1 << 23) - 1).astype(np.int16)

print("""\
// clang-format off
const ResamplerTables resampler_tables = {

  .h = {
""")
for q in range(len(h) - 1):
    layout = "  {{" + " {:10d}," * 8 + "\n" + \
              "   " + " {:10d}," * 8 + "\n" + \
              "   " + " {:10d}," * 8 + "\n" + \
              "   " + " {:10d}," * 6 + " {:10d} }},"
    print(layout.format(*h32[q]))
print("""
  },
""")

print("""\
  .d = {
""")
for q in range(len(h) - 1):
    layout = "  {{" + " {:6d}," * 10 + "\n" + \
              "   " + " {:6d}," * 10 + "\n" + \
              "   " + " {:6d}," * 10 + " {:2d} }},"
    print(layout.format(*d32[q]))
print("""
  }
};
// clang-format off""")

#
# File footer
#

print("""
} // namespace bluetooth::audio::asrc""")
