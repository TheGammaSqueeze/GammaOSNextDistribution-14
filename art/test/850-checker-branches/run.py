#!/bin/bash
#
# Copyright (C) 2023 The Android Open Source Project
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
  # Pass --verbose-methods to only generate the CFG of these methods.
  # The test is for JIT, but we run in "optimizing" (AOT) mode, so that the Checker
  # stanzas in Main.java will be checked.
  # Also pass a large JIT code cache size to avoid getting the branch caches GCed.
  ctx.default_run(
      args,
      jit=True,
      runtime_option=["-Xjitinitialsize:32M"],
      Xcompiler_option=[
          "--profile-branches",
          "--verbose-methods=withBranch"
      ])
