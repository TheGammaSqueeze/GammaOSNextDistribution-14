#!/bin/sh
#
# Copyright (C) 2012 The Android Open Source Project
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

import re


def run(ctx, args):
  bridge_so = "libnativebridgetest.so" if args.O else "libnativebridgetestd.so"
  test_dir = ctx.env.DEX_LOCATION

  # Use libnativebridgetest as a native bridge, start NativeBridgeMain (Main is JniTest main file).
  for i, opt in enumerate(args.runtime_option):
    if opt.startswith("-Djava.library.path="):
      libpath = opt.split(":")[-1]  # last entry in libpath is nativetest[64]
      args.runtime_option[i] = "-Djava.library.path=" + test_dir

  assert libpath
  ctx.run(f"ln -sf {libpath}/{bridge_so} {test_dir}/.")
  ctx.run(
      f"touch {test_dir}/libarttest.so {test_dir}/libarttestd.so {test_dir}/libinvalid.so"
  )
  ctx.run(f"ln -sf {libpath}/libarttest.so {test_dir}/libarttest2.so")
  ctx.run(f"ln -sf {libpath}/libarttestd.so {test_dir}/libarttestd2.so")

  ctx.default_run(
      args,
      runtime_option=["-Xforce-nb-testing", f"-XX:NativeBridge={bridge_so}"],
      main="NativeBridgeMain")

  # ASAN prints a warning here.
  ctx.run(
      fr"sed -i '/WARNING: ASan is ignoring requested __asan_handle_no_return/,+2d' '{args.stderr_file}'"
  )
