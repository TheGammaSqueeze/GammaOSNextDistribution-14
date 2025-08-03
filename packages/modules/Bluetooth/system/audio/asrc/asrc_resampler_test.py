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

import ctypes
import numpy as np
from scipy import signal
from mobly import test_runner, base_test
from mobly.asserts import assert_greater
import sys
import os


class CResampler:

    def __init__(self, lib, channels, bitdepth):

        self.lib = lib
        self.channels = channels
        self.bitdepth = bitdepth

    def resample(self, xs, ratio):

        c_int = ctypes.c_int
        c_size_t = ctypes.c_size_t
        c_double = ctypes.c_double
        c_int16_p = ctypes.POINTER(ctypes.c_int16)
        c_int32_p = ctypes.POINTER(ctypes.c_int32)

        channels = self.channels
        bitdepth = self.bitdepth

        xs_min = -(2**(bitdepth - 1))
        xs_max = (2**(bitdepth - 1) - 1)
        xs_int = np.rint(np.clip(np.ldexp(xs, bitdepth-1), xs_min, xs_max)).\
                 astype([np.int16, np.int32][bitdepth > 16], 'C')

        ys_int = np.empty(int(np.ceil(len(xs) / ratio)), dtype=xs_int.dtype)

        if bitdepth <= 16:
            lib.resample_i16(c_int(channels), c_int(bitdepth), c_double(ratio), xs_int.ctypes.data_as(c_int16_p),
                             c_size_t(len(xs_int)), ys_int.ctypes.data_as(c_int16_p), c_size_t(len(ys_int)))
        else:
            lib.resample_i32(c_int(channels), c_int(bitdepth), c_double(ratio), xs_int.ctypes.data_as(c_int32_p),
                             c_size_t(len(xs_int)), ys_int.ctypes.data_as(c_int32_p), c_size_t(len(ys_int)))

        return np.ldexp(ys_int, 1 - bitdepth)


FS = 48e3


def snr(x, fs=FS):

    f, p = signal.periodogram(x, fs=fs, scaling='spectrum', window=('kaiser', 38))

    k = np.argmax(p)
    s = np.sum(p[k - 19:k + 20])
    n = np.sum(p[20:k - 19]) + np.sum(p[k + 20:])

    return 10 * np.log10(s / n)


def mean_snr(resampler, ratio):
    N = 8192
    xt = np.arange(2 * N + 128) / FS

    frequencies = []
    values = []

    for f in range(200, 20000, 99):
        xs = np.sin(2 * np.pi * xt * f)

        frequencies += [f]
        result = resampler.resample(xs, ratio)
        values += [snr(result[128:128 + N])]

    k = np.argmin(np.abs(np.array(frequencies) - 18e3))
    return np.mean(values[:k])


root = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
lib = ctypes.cdll.LoadLibrary(os.path.join(root, "libasrc_resampler_test.so"))

cresampler_16 = CResampler(lib, 1, 16)
cresampler_24 = CResampler(lib, 1, 24)


class SnrTest(base_test.BaseTestClass):

    def test_16bit_48000_to_44100(self):
        assert_greater(mean_snr(cresampler_16, 44.1 / 48.0), 94)

    def test_16bit_44100_to_48000(self):
        assert_greater(mean_snr(cresampler_16, 48.0 / 44.1), 94)

    def test_24bit_48000_to_44100(self):
        assert_greater(mean_snr(cresampler_24, 44.1 / 48.0), 114)

    def test_24bit_44100_to_48000(self):
        assert_greater(mean_snr(cresampler_24, 48.0 / 44.1), 114)


if __name__ == '__main__':
    index = sys.argv.index('--')
    sys.argv = sys.argv[:1] + sys.argv[index + 1:]
    test_runner.main()
