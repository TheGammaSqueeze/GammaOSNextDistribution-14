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

USER_32BIT := true

QEMU_TRUSTY_PROJECT := generic-arm64-test-debug

# D-cache lines are 64 bytes on QEMU arm64
GLOBAL_DEFINES += CACHE_LINE=64

# Version checks are enforced by default. We override this to ensure that we
# have at least one target to test that we can disable this enforcement.
#
# a value of 1 indicates that we will skip updating the rollback version, 2 will
# skip the version check entirely.
STATIC_SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK := 2

include project/debugging-inc.mk
include project/qemu-inc.mk
