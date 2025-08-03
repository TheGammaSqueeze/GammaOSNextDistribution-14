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
  plugin = "libartagent.so" if args.O else "libartagentd.so"

  # Adjust the agent path when running on device.
  if not args.host:
    for i, opt in enumerate(args.runtime_option):
      if opt.startswith("-Djava.library.path="):
        libpath = opt.split("=")[-1]
        assert libpath.startswith("/data/nativetest"), libpath

        # The linker configuration used for dalvikvm(64) in the ART APEX requires us
        # to pass the full path to the agent to the runtime when running on device.
        plugin = f"{libpath}/{plugin}"
        break

  ctx.default_run(
      args,
      runtime_option=[
          f"-agentpath:{plugin}=test_900",
          f"-agentpath:{plugin}=test_900_round_2"
      ],
      android_runtime_option=[f"-Xplugin:{plugin}"])
