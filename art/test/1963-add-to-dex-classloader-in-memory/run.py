#!/bin/bash
#
# Copyright 2016 The Android Open Source Project
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

  # Some of our test devices are so old that they don't have memfd_create and are setup in such a way
  # that tmpfile() doesn't work. In these cases this test cannot complete successfully.
  # If we see this in stdout, make the expected stdout identical.
  ctx.run(
      fr"grep -q -- '---NO memfd_create---' '{args.stdout_file}' &&"
      fr" echo '---NO memfd_create---' > expected-stdout.txt",
      check=False)
