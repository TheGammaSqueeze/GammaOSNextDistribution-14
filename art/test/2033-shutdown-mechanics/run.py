#!/bin/bash
#
# Copyright 2022 The Android Open Source Project
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
  ctx.default_run(args)

  # The daemon thread seems to occasionally get stopped before finishing.
  # Check that the actual output is a line-by-line prefix of expected.
  ctx.run(
      fr"head -n $(wc -l < '{args.stdout_file}') expected-stdout.txt > expected-stdout.txt.tmp &&"
      fr"mv expected-stdout.txt.tmp expected-stdout.txt")
