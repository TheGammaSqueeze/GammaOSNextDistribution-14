#!/bin/bash
#
# Copyright (C) 2008 The Android Open Source Project
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

import sys


def run(ctx, args):
  # Test with full DWARF debugging information.
  # Check full signatures of methods.
  ctx.default_run(
      args,
      Xcompiler_option=["--generate-debug-info"],
      test_args=["--test-local", "--test-remote"])

  # The option jitthreshold:0 ensures that if we run the test in JIT mode,
  # there will be JITed frames on the callstack (it synchronously JITs on first use).
  ctx.default_run(
      args,
      Xcompiler_option=["--generate-debug-info"],
      runtime_option=["-Xjitthreshold:0"],
      test_args=["--test-local", "--test-remote"])

  # Test with minimal compressed debugging information.
  # Check only method names (parameters are omitted to save space).
  # Check only remote unwinding since decompression is disabled in local unwinds (b/27391690).
  ctx.default_run(
      args,
      Xcompiler_option=["--generate-mini-debug-info"],
      runtime_option=["-Xjitthreshold:0"],
      test_args=["--test-remote"])
