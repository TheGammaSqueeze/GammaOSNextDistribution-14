# Copyright (C) 2019 The Android Open Source Project
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

TRUSTY_USER_TESTS += \
	trusty/user/app/sample/app-mgmt-test/client\
	trusty/user/app/sample/binder-test/client \
	trusty/user/app/sample/binder-test/service \
	trusty/user/app/sample/hwcrypto-unittest \
	trusty/user/app/sample/hwrng-unittest \
	trusty/user/app/sample/hwrng-bench \
	trusty/user/app/sample/manifest-test \
	trusty/user/app/sample/memref-test \
	trusty/user/app/sample/memref-test/lender \
	trusty/user/app/sample/memref-test/receiver \
	trusty/user/app/sample/timer \
	trusty/user/app/sample/spi/swspi-srv \
	trusty/user/app/sample/spi/swspi-test \
	trusty/user/app/sample/skel_rust \
	trusty/user/app/sample/rust_no_std \

TRUSTY_RUST_USER_TESTS += \
	trusty/user/app/sample/memref-test/rust \
	trusty/user/app/sample/rust-hello-world \

ifeq (true,$(call TOBOOL,$(USER_SCS_ENABLED)))
TRUSTY_USER_TESTS += \
	trusty/user/app/sample/userscs-test/default \
	trusty/user/app/sample/userscs-test/custom \

# TODO: We cannot robustly support trusty apps that opt out of shadow call
# stacks until support for library variants is added to the SDK. Blocked on
# https://android-review.googlesource.com/c/trusty/lib/+/1706286
# USER_COVERAGE_ENABLED is set for fuzzing builds and causes apps to be
# instrumented to track coverage. When user shadow call stacks are enabled
# system wide, even apps that opt out are linked against the sanitizer
# coverage runtime that uses shadow call stacks which causes crashes.
ifeq (false,$(call TOBOOL,$(USER_COVERAGE_ENABLED)))
# TODO: This app requires a libc without shadow call stacks which we cannot
# currently provide for the reasons stated in the comment above.
# TRUSTY_USER_TESTS += \
# 	trusty/user/app/sample/userscs-test/disabled \

endif
endif # USER_SCS_ENABLED

ifneq (,$(findstring arm,$(TRUSTY_USER_ARCH)))
TRUSTY_USER_TESTS += \
	trusty/user/app/sample/prebuilts-test \

endif

TRUSTY_LOADABLE_USER_TASKS += \
	trusty/user/app/sample/app-mgmt-test/boot-start-srv \
	trusty/user/app/sample/app-mgmt-test/dev-only-srv \
	trusty/user/app/sample/app-mgmt-test/never-start-srv \
	trusty/user/app/sample/app-mgmt-test/port-start-srv \
	trusty/user/app/sample/app-mgmt-test/port-start-fail-srv \
	trusty/user/app/sample/app-mgmt-test/restart-srv \
	trusty/user/app/sample/app-mgmt-test/port-waiter-srv \
	trusty/user/app/sample/storage-test \
