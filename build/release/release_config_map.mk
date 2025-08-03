# Copyright (C) 2023 The Android Open Source Project
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

# Get the directory for this file, and use that instead of a fixed path.
local_dir := $(dir $(lastword $(MAKEFILE_LIST)))

# FLAG_DECLARATION_FILES gives the path(s) of flag declaration files that
# should be included in the build.
FLAG_DECLARATION_FILES := $(local_dir)build_flags.scl

# 24Q2, aka AP2A
$(call declare-release-config, ap2a, $(local_dir)build_config/ap2a.scl)

local_dir :=
