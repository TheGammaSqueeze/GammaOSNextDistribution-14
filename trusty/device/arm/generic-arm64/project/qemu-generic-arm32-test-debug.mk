# Copyright (C) 2018 The Android Open Source Project
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

QEMU_TRUSTY_PROJECT := generic-arm32-test-debug
TEST_RUNNER_ARCH := arm64
LINUX_ARCH := arm64

# Override the app loading unlock state to test with app loading "locked" (i.e.
# key 1 disabled). See qemu-inc.mk for more details on app loading locking
STATIC_SYSTEM_STATE_FLAG_APP_LOADING_UNLOCKED := 0

include project/debugging-inc.mk
include project/qemu-inc.mk
