#!/bin/bash
#
# Copyright (C) 2017 The Android Open Source Project
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

import sys


def run(ctx, args):
  # Enable lock contention logging.
  ctx.default_run(args, runtime_option=["-Xlockprofthreshold:10"])

  # Run locks-only mode with stack-dump lock profiling. Reduce the number of total operations from
  # the default 1000 to 100.
  ctx.default_run(
      args,
      test_args=["--locks-only -o 100"],
      runtime_option=[
          "-Xlockprofthreshold:10", "-Xstackdumplockprofthreshold:20"
      ])

  # Do not compare numbers, so replace numbers with 'N'.
  ctx.run(fr"sed -i 's/[0-9][0-9]*/N/g' '{args.stdout_file}'")

  # Remove all messages relating to failing to allocate a java-peer for the
  # shutdown thread. This can occasionally happen with this test but it is not
  # something we really need to worry about here.
  ctx.run(fr"sed -i '/Exception creating thread peer:/d' '{args.stderr_file}'")
