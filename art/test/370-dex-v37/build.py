#
# Copyright (C) 2022 The Android Open Source Project
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

def build(ctx):
  ctx.default_build()
  if ctx.jvm:
    return
  # Change the generated dex file to have a v37 magic number if it is version 35
  with open(ctx.test_dir / "classes.dex", "rb+") as f:
    if f.read(8) == b"dex\n035\x00":
      f.seek(0)
      f.write(b"dex\n037\x00")
  (ctx.test_dir / "370-dex-v37.jar").unlink()
  ctx.soong_zip([
      "-o", ctx.test_dir / "370-dex-v37.jar", "-j", "-f",
      ctx.test_dir / "classes.dex"
  ])
