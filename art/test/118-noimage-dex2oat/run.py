#!/bin/bash
#
# Copyright (C) 2014 The Android Open Source Project
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
  # This test is supposed to test without oat files, so doesn't work for prebuild.
  assert not args.prebuild

  # Force relocation otherwise we will just use the already created core.oat/art pair.
  assert args.relocate

  # Make sure we can run without an oat file.
  ctx.echo("Run -Xnoimage-dex2oat")
  ctx.default_run(args, runtime_option=["-Xnoimage-dex2oat"])

  # Make sure we can run with the oat file.
  ctx.echo("Run -Ximage-dex2oat")
  ctx.default_run(args, runtime_option=["-Ximage-dex2oat"])

  # Make sure we can run with the default settings.
  ctx.echo("Run default")
  ctx.default_run(args)

  # Strip the process pids and line numbers from exact error messages.
  ctx.run(fr"sed -i '/^dalvikvm.* E.*\] /d' '{args.stderr_file}'")
