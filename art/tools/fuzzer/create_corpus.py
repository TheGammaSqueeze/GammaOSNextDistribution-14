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

import pathlib
import sys
import zipfile

""" Extract dex files into flat directory with unique names """

assert len(sys.argv) >= 3, "Usage: " + __file__ + " output_dir input_zip+"
dst = pathlib.Path(sys.argv[1])
assert dst.exists()
srcs = [pathlib.Path(f) for f in sys.argv[2:]]
assert all(src.exists() for src in srcs)

for src in srcs:
  with zipfile.ZipFile(src, 'r') as zip:
    for name in zip.namelist():
      if name.endswith(".dex"):
        with zip.open(name) as f:
          (dst / name.replace("/", "_")).write_bytes(f.read())
