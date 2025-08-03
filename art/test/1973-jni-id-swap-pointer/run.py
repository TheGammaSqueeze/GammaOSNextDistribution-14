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
  for i, opt in enumerate(args.runtime_option):
    if opt == '-Xopaque-jni-ids:true':
      args.runtime_option.pop(i)

  ctx.default_run(
      args,
      android_runtime_option=[
          '-Xopaque-jni-ids:swapable', '-Xauto-promote-opaque-jni-ids:false'
      ])
