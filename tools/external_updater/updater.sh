#!/bin/bash
#
# Copyright (C) 2007 The Android Open Source Project
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

set -e

# We don't change directory because relative paths passed as arguments should be
# resolved relative to the user's CWD. If we don't do that, tab completion won't
# work, and there are few things worse in life than bad tab completion.
THIS_DIR=$(dirname "$0")
TOP=$THIS_DIR/../..
$TOP/build/soong/soong_ui.bash --make-mode external_updater
$TOP/out/host/linux-x86/bin/external_updater $@
