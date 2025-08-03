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
  agent = "libtiagent.so" if args.O else "libtiagentd.so"
  plugin = "libopenjdkjvmti.so" if args.O else "libopenjdkjvmtid.so"

  if args.switch_interpreter:
    # On switch interpreter we are fully capable of providing the full jvmti api so we
    # have a slightly different expected output.
    ctx.expected_stdout = ctx.expected_stdout.with_suffix(".interpreter.txt")

  ctx.default_run(
      args,
      android_runtime_option=[
          f"-Xplugin:{plugin}", "-Xcompiler-option", "--debuggable"
      ],
      test_args=[f"agent:{agent}=909-attach-agent"])

  ctx.default_run(
      args,
      android_runtime_option=[
          f"-Xcompiler-option", "--debuggable"
      ],
      test_args=[f"agent:{agent}=909-attach-agent"])

  ctx.default_run(
      args,
      test_args=[f"agent:{agent}=909-attach-agent"],
      android_log_tags="*:f")

  ctx.default_run(
      args,
      test_args=[f"agent:{agent}=909-attach-agent", "disallow-debugging"],
      android_log_tags="*:f")
