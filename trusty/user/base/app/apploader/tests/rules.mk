# Copyright (C) 2020 The Android Open Source Project
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

LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MANIFEST := $(LOCAL_DIR)/manifest.json

MODULE_SRCS += \
	$(LOCAL_DIR)/apploader_test.c \
	$(LOCAL_DIR)/test_apps.S \

MODULE_LIBRARY_DEPS += \
	trusty/user/base/lib/libc-trusty \
	trusty/user/base/lib/system_state \
	trusty/user/base/lib/tipc \
	trusty/user/base/lib/unittest \
	trusty/user/base/interface/apploader \

APPLOADER_TESTS_DIR := \
	$(TRUSTY_APP_BUILDDIR)/trusty/user/base/app/apploader/tests

VERSION_TEST_APP_V1 := \
	$(APPLOADER_TESTS_DIR)/version_test_apps/v1/v1.app
VERSION_TEST_APP_V2 := \
	$(APPLOADER_TESTS_DIR)/version_test_apps/v2/v2.app
VERSION_TEST_APP_V3 := \
	$(APPLOADER_TESTS_DIR)/version_test_apps/v3/v3.app

MMIO_TEST_APP_ALLOWED := \
	$(APPLOADER_TESTS_DIR)/mmio_test_apps/allowed/allowed.app
MMIO_TEST_APP_BAD_UUID := \
	$(APPLOADER_TESTS_DIR)/mmio_test_apps/bad_uuid/bad_uuid.app
MMIO_TEST_APP_BAD_RANGE_LOW := \
	$(APPLOADER_TESTS_DIR)/mmio_test_apps/bad_range_low/bad_range_low.app
MMIO_TEST_APP_BAD_RANGE_HIGH := \
	$(APPLOADER_TESTS_DIR)/mmio_test_apps/bad_range_high/bad_range_high.app

ENCRYPTION_TEST_APP_ENCRYPTED_APP_ENCRYPTION_OPTIONAL := \
	$(APPLOADER_TESTS_DIR)/encryption_test_apps/encrypted_app/encryption_optional/encryption_optional.app
ENCRYPTION_TEST_APP_ENCRYPTED_APP_ENCRYPTION_REQUIRED := \
	$(APPLOADER_TESTS_DIR)/encryption_test_apps/encrypted_app/encryption_required/encryption_required.app
ENCRYPTION_TEST_APP_UNENCRYPTED_APP_ENCRYPTION_OPTIONAL := \
	$(APPLOADER_TESTS_DIR)/encryption_test_apps/unencrypted_app/encryption_optional/encryption_optional.app
ENCRYPTION_TEST_APP_UNENCRYPTED_APP_ENCRYPTION_REQUIRED := \
	$(APPLOADER_TESTS_DIR)/encryption_test_apps/unencrypted_app/encryption_required/encryption_required.app

MODULE_ASMFLAGS += \
		-DVERSION_TEST_APP_V1=\"$(VERSION_TEST_APP_V1)\" \
		-DVERSION_TEST_APP_V2=\"$(VERSION_TEST_APP_V2)\" \
		-DVERSION_TEST_APP_V3=\"$(VERSION_TEST_APP_V3)\" \
		-DMMIO_TEST_APP_ALLOWED=\"$(MMIO_TEST_APP_ALLOWED)\" \
		-DMMIO_TEST_APP_BAD_UUID=\"$(MMIO_TEST_APP_BAD_UUID)\" \
		-DMMIO_TEST_APP_BAD_RANGE_LOW=\"$(MMIO_TEST_APP_BAD_RANGE_LOW)\" \
		-DMMIO_TEST_APP_BAD_RANGE_HIGH=\"$(MMIO_TEST_APP_BAD_RANGE_HIGH)\" \
		-DENCRYPTION_TEST_APP_ENCRYPTED_APP_ENCRYPTION_OPTIONAL=\"$(ENCRYPTION_TEST_APP_ENCRYPTED_APP_ENCRYPTION_OPTIONAL)\" \
		-DENCRYPTION_TEST_APP_ENCRYPTED_APP_ENCRYPTION_REQUIRED=\"$(ENCRYPTION_TEST_APP_ENCRYPTED_APP_ENCRYPTION_REQUIRED)\" \
		-DENCRYPTION_TEST_APP_UNENCRYPTED_APP_ENCRYPTION_OPTIONAL=\"$(ENCRYPTION_TEST_APP_UNENCRYPTED_APP_ENCRYPTION_OPTIONAL)\" \
		-DENCRYPTION_TEST_APP_UNENCRYPTED_APP_ENCRYPTION_REQUIRED=\"$(ENCRYPTION_TEST_APP_UNENCRYPTED_APP_ENCRYPTION_REQUIRED)\" \

MODULE_SRCDEPS += \
       $(VERSION_TEST_APP_V1) \
       $(VERSION_TEST_APP_V2) \
       $(VERSION_TEST_APP_V3) \
       $(MMIO_TEST_APP_ALLOWED) \
       $(MMIO_TEST_APP_BAD_UUID) \
       $(MMIO_TEST_APP_BAD_RANGE_LOW) \
       $(MMIO_TEST_APP_BAD_RANGE_HIGH) \
	   $(ENCRYPTION_TEST_APP_ENCRYPTED_APP_ENCRYPTION_OPTIONAL) \
	   $(ENCRYPTION_TEST_APP_ENCRYPTED_APP_ENCRYPTION_REQUIRED) \
	   $(ENCRYPTION_TEST_APP_UNENCRYPTED_APP_ENCRYPTION_OPTIONAL) \
	   $(ENCRYPTION_TEST_APP_UNENCRYPTED_APP_ENCRYPTION_REQUIRED) \

include make/trusted_app.mk
