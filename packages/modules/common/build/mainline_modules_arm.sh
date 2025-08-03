#!/bin/bash -ex
#
# Copyright (C) 2021 The Android Open Source Project
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
#

# Assign to a variable and eval that, since bash ignores any error status from
# the command substitution if it's directly on the eval line.
readonly vars="$(TARGET_PRODUCT='' build/soong/soong_ui.bash --dumpvars-mode \
  --vars="DIST_DIR")"
eval "${vars}"

packages/modules/common/build/build_unbundled_mainline_module.sh \
  --product module_arm \
  --dist_dir "${DIST_DIR}/mainline_modules_arm"
