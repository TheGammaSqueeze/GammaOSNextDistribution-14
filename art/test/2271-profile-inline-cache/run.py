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


def run(ctx, args):
  # Use
  # --compiler-filter=verify to make sure that the test is not compiled AOT
  # and to make sure the test is not compiled when loaded (by PathClassLoader)
  # -Xjitsaveprofilinginfo to enable profile saving
  # -Xjitinitialsize:32M to prevent profiling info creation failure.
  ctx.default_run(
      args,
      Xcompiler_option=["--compiler-filter=verify"],
      runtime_option=[
          "-Xjitinitialsize:32M",
          "-Xjitsaveprofilinginfo",
          "-Xps-inline-cache-threshold:3000",
      ])
