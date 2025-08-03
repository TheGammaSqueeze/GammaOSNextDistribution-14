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
  # App images are incompatible with what the test is doing: loading one
  # dex file multiple times.
  ctx.default_run(args, app_image=False)

  # Finalizers of DexFile will complain not being able to close
  # the main dex file, as it's still open. That's OK to ignore.
  # Oat file manager will also complain about duplicate dex files. Ignore.
  ctx.run(
      fr"sed -i -e '/^E\/System/d' -e '/.*oat_file_manager.*/d' '{args.stderr_file}'"
  )
