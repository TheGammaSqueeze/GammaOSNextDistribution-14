#
# Copyright 2023 The Android Open Source Project
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


import os


def run(ctx, args):
  ctx.default_run(args)
  if os.environ.get("ART_TEST_ON_VM"):
    # On Linux, max thread priority is not the same as on Android, but otherwise
    # test results are the same. Cut the offending line to make the test pass.
    line = "thread priority for t[12] was 5 \[expected Thread.MAX_PRIORITY\]"
    ctx.run(fr"sed -i -E '/{line}/d' '{args.stdout_file}'")
