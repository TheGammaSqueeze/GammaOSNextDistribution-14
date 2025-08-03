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
  ctx.default_run(
      args,
      # Disable app image to make sure we compile dex files individually.
      app_image=False,
      # Pass a .dm file to run FastVerify and ask to compile dex files
      # individually in order to run the problematic code.
      Xcompiler_option=[f"--dm-file={ctx.env.DEX_LOCATION}/classes.dm", "--compile-individually"])
