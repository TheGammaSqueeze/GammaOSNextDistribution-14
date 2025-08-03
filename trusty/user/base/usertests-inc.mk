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

include trusty/user/app/cast-auth/usertests-inc.mk
include trusty/user/app/keymaster/usertests-inc.mk
include trusty/user/app/keymint/usertests-inc.mk
include trusty/user/app/sample/usertests-inc.mk
include trusty/user/app/secretkeeper/usertests-inc.mk
include trusty/user/app/storage/usertests-inc.mk

ifeq (false,$(call TOBOOL,$(CONFIRMATIONUI_DISABLED)))
include trusty/user/app/confirmationui/usertests-inc.mk
endif

TRUSTY_USER_TESTS += \
	trusty/user/base/app/acvp \
	trusty/user/base/app/apploader/tests \
	trusty/user/base/app/crash-test \
	trusty/user/base/app/crash-test/crasher \
	trusty/user/base/app/device_tree/tests \
	trusty/user/base/lib/dlmalloc/test \
	trusty/user/base/lib/dlmalloc/test/srv \
	trusty/user/base/app/metrics/test/crasher \
	trusty/user/base/app/hwaes-unittest \
	trusty/user/base/app/hwaes-benchmark \
	trusty/user/base/lib/hwbcc/test \
	trusty/user/base/lib/keymaster/test \
	trusty/user/base/lib/libc-trusty/test \
	trusty/user/base/lib/libstdc++-trusty/test \
	trusty/user/base/lib/smc/tests \
	trusty/user/base/lib/tipc/test/main \
	trusty/user/base/lib/tipc/test/srv \
	trusty/user/base/lib/uirq/test \

ifeq (false,$(call TOBOOL,$(CONFIRMATIONUI_DISABLED)))
TRUSTY_USER_TESTS += \
	trusty/user/base/lib/secure_fb/test
endif

ifeq (true,$(call TOBOOL,$(USER_COVERAGE_ENABLED)))
TRUSTY_USER_TESTS += \
	trusty/user/base/lib/sancov/test/srv \

endif

ifeq (true,$(call TOBOOL,$(USER_HWASAN_ENABLED)))
TRUSTY_USER_TESTS += \
	trusty/user/base/lib/hwasan/test \

endif

ifeq (true,$(call TOBOOL,$(USER_CFI_ENABLED)))
TRUSTY_USER_TESTS += \
	trusty/user/base/app/cfi-test \
	trusty/user/base/app/cfi-test/cfi-crasher \

endif

ifeq (false,$(call TOBOOL,$(KERNEL_32BIT)))
ifeq (false,$(call TOBOOL,$(USER_32BIT)))
TRUSTY_USER_TESTS += \
	trusty/user/base/lib/scudo/test \
	trusty/user/base/lib/scudo/test/srv \

endif
endif

TRUSTY_LOADABLE_USER_TASKS += \
	trusty/user/base/app/apploader/tests/version_test_apps/v1 \
	trusty/user/base/app/apploader/tests/version_test_apps/v2 \
	trusty/user/base/app/apploader/tests/version_test_apps/v3 \
	trusty/user/base/app/apploader/tests/mmio_test_apps/allowed \
	trusty/user/base/app/apploader/tests/mmio_test_apps/bad_uuid \
	trusty/user/base/app/apploader/tests/mmio_test_apps/bad_range_low \
	trusty/user/base/app/apploader/tests/mmio_test_apps/bad_range_high \
	trusty/user/base/app/apploader/tests/encryption_test_apps/encrypted_app/encryption_optional \
	trusty/user/base/app/apploader/tests/encryption_test_apps/encrypted_app/encryption_required \
	trusty/user/base/app/apploader/tests/encryption_test_apps/unencrypted_app/encryption_optional \
	trusty/user/base/app/apploader/tests/encryption_test_apps/unencrypted_app/encryption_required \

TRUSTY_LOADABLE_USER_TESTS += \
	trusty/user/base/app/trusty-crasher \
