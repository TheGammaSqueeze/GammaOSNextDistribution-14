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
#

LOCAL_DIR := $(GET_LOCAL_DIR)

AIDL_DIR := \
	hardware/interfaces/staging/security/see/storage/aidl

MODULE := $(LOCAL_DIR)

MODULE_AIDL_FLAGS :=

MODULE_AIDL_LANGUAGE := cpp

MODULE_AIDL_PACKAGE := android/hardware/security/see/storage

MODULE_AIDLS := \
	$(AIDL_DIR)/$(MODULE_AIDL_PACKAGE)/CreationMode.aidl \
	$(AIDL_DIR)/$(MODULE_AIDL_PACKAGE)/DeleteOptions.aidl \
	$(AIDL_DIR)/$(MODULE_AIDL_PACKAGE)/FileAvailability.aidl \
	$(AIDL_DIR)/$(MODULE_AIDL_PACKAGE)/FileIntegrity.aidl \
	$(AIDL_DIR)/$(MODULE_AIDL_PACKAGE)/FileMode.aidl \
	$(AIDL_DIR)/$(MODULE_AIDL_PACKAGE)/FileProperties.aidl \
	$(AIDL_DIR)/$(MODULE_AIDL_PACKAGE)/IDir.aidl \
	$(AIDL_DIR)/$(MODULE_AIDL_PACKAGE)/IFile.aidl \
	$(AIDL_DIR)/$(MODULE_AIDL_PACKAGE)/ISecureStorage.aidl \
	$(AIDL_DIR)/$(MODULE_AIDL_PACKAGE)/IStorageSession.aidl \
	$(AIDL_DIR)/$(MODULE_AIDL_PACKAGE)/OpenOptions.aidl \
	$(AIDL_DIR)/$(MODULE_AIDL_PACKAGE)/ReadIntegrity.aidl \
	$(AIDL_DIR)/$(MODULE_AIDL_PACKAGE)/RenameOptions.aidl \
	$(AIDL_DIR)/$(MODULE_AIDL_PACKAGE)/Tamper.aidl \

MODULE_EXPORT_INCLUDES := \
	$(LOCAL_DIR)/include \

include make/aidl.mk
