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
  # This test checks whether dex files can be injected into parent classloaders. App images preload
  # classes, which will make the injection moot. Turn off app images to avoid the issue.
  # Pass the correct `--secondary-class-loader-context` for the "-ex" jar.

  pcl = f"PCL[{ctx.env.DEX_LOCATION}/{ctx.env.TEST_NAME}.jar]"
  ctx.default_run(
      args, jvmti=True, app_image=False, secondary_class_loader_context=pcl)
