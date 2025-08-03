#!/bin/bash
#
# Copyright 2017 The Android Open Source Project
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


def run(ctx, args):
  ctx.default_run(args, jvmti=True)

  # The RI has restrictions and bugs around some PopFrame behavior that ART lacks.
  # See b/116003018. Some configurations cannot handle the class load events in
  # quite the right way so they are disabled there too.
  if not (not args.prebuild or (args.jvmti_redefine_stress and args.host)):
    ctx.expected_stdout = ctx.expected_stdout.with_suffix(".no-jvm.txt")
