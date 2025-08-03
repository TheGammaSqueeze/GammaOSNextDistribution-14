#!/bin/bash
#
# Copyright (C) 2018 The Android Open Source Project
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
  # Redirect logger to stderr, as the test relies on error
  # messages being printed there.
  ctx.default_run(
      args,
      Xcompiler_option=["--copy-dex-files=always"],
      runtime_option=["-Xonly-use-system-oat-files", "-Xuse-stderr-logger"])

  # Only keep the lines we're interested in.
  ctx.run(fr"sed -i '/Hello World/!d' '{args.stdout_file}'")
  ctx.run(
      fr"sed -i '/^.*: oat file has dex code, but APK has uncompressed dex code/!d' '{args.stderr_file}'"
  )

  # Remove part of message containing filename.
  ctx.run(fr"sed -i 's/^.*: //' '{args.stderr_file}'")
