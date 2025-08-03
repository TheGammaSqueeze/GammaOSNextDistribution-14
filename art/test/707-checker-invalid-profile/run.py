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


def run(ctx, args):
  ctx.default_run(
      args, profile=True, Xcompiler_option=["--compiler-filter=speed-profile"])

  # When profile verification fails, dex2oat logs an error. The following
  # command strips out the error message.
  ctx.run(
      fr"grep -v -f expected-stdout.txt '{args.stdout_file}' > expected-stdout.txt",
      check=False)
  ctx.run(
      fr"grep -v -f expected-stderr.txt '{args.stderr_file}' > expected-stderr.txt",
      check=False)
