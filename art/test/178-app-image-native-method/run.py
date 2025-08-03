#!/bin/bash
#
# Copyright (C) 2019 The Android Open Source Project
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


def run(ctx, args):
  # Use a profile to put specific classes in the app image. Increase the large
  # method threshold to compile Main.$noinline$opt$testCriticalSignatures().
  ctx.default_run(
      args,
      profile=True,
      Xcompiler_option=[
          "--compiler-filter=speed-profile", "--large-method-max=2000"
      ])

  # Also run with the verify filter to avoid compiling JNI stubs.
  ctx.default_run(
      args, profile=True, Xcompiler_option=["--compiler-filter=verify"])

  # Filter out error messages for missing native methods.
  ctx.run(fr"sed -i '/No implementation found for/d' '{args.stderr_file}'")
