#!/bin/bash
#
# Copyright (C) 2017 The Android Open Source Project
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
  # Ensure the minimum log severity is at least 'WARNING' to display the
  # stack trace shown before exception
  #
  #   "java.lang.OutOfMemoryError: OutOfMemoryError thrown while trying
  #   to throw OutOfMemoryError; no stack trace available"
  #
  # is set, to try to understand a recurring crash in this test (b/77567088).
  if ctx.env.ANDROID_LOG_TAGS in ["*:e", "*:f", "*:s"]:
    # Lower the minimum log severity to WARNING if it was initialy set
    # to a higher level ('ERROR', 'FATAL' or 'SILENT' -- see
    # https://developer.android.com/studio/command-line/logcat#filteringOutput).
    ctx.env.ANDROID_LOG_TAGS = "*:w"

  ctx.default_run(args, runtime_option=["-Xmx16m"])
