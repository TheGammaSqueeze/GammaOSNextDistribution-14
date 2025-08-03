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
  # Make verification soft fail so that we can re-verify boot classpath
  # methods at runtime.
  #
  # N.B. Compilation of secondary dexes can prevent hidden API checks, e.g. if
  # a blocklisted field get is inlined.
  ctx.default_run(args, verify_soft_fail=True, secondary_compilation=False)

  ctx.run(fr"sed -i -E '/(JNI_OnLoad|JNI_OnUnload)/d' '{args.stdout_file}'")
  ctx.run(fr"sed -i -E '/^dalvikvm(32|64) E [^]]+]/d' '{args.stderr_file}'")
