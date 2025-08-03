#!/usr/bin/python3 -B
# Copyright 2015 The Android Open Source Project
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

"""Regenerates (just) ICU data files used in the Android system image."""

from __future__ import print_function

import sys

import icuutil


# Run with no arguments from any directory, with no special setup required.
def main():
  icu_dir = icuutil.icuDir()
  print('Found icu in %s ...' % icu_dir)

  icuutil.GenerateIcuDataFiles()

  print('Look in %s for new data files' % icu_dir)
  sys.exit(0)

if __name__ == '__main__':
  main()
