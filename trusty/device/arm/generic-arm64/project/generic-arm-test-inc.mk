# Copyright (C) 2016-2017 The Android Open Source Project
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

# Enabling this changes benchmark output to be in json format.
# See go/trusty-benchmark-json-output for the schema.
# Build with BENCHMARK_MACHINE_READABLE=1 to enable.
BENCHMARK_MACHINE_READABLE?=0
GLOBAL_DEFINES += BENCHMARK_MACHINE_READABLE=$(BENCHMARK_MACHINE_READABLE)

UBSAN_ENABLED ?= true

RELEASE_BUILD ?= false

include project/generic-arm-inc.mk

include frameworks/native/libs/binder/trusty/usertests-inc.mk
include trusty/kernel/kerneltests-inc.mk
include trusty/user/app/sample/stats-test/usertests-inc.mk
include trusty/user/base/usertests-inc.mk
include trusty/user/base/usertests-rust-inc.mk

# Only enable pattern init in test-builds, as it has runtime overhead
# and intentionally attempts to induce crashes for bad assumptions.
GLOBAL_SHARED_COMPILEFLAGS += -ftrivial-auto-var-init=pattern

WITH_HWCRYPTO_UNITTEST := 1

# Enable hwcrypto unittest keyslots and tests
GLOBAL_USER_COMPILEFLAGS += -DWITH_HWCRYPTO_UNITTEST=$(WITH_HWCRYPTO_UNITTEST)

TEST_BUILD := true
