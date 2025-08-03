# Copyright (C) 2022 The Android Open Source Project
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

MODULE_AIDL_PACKAGE := com/android/trusty/device_tree

MODULE_AIDLS := \
	$(LOCAL_DIR)/$(MODULE_AIDL_PACKAGE)/IDeviceTree.aidl \
	$(LOCAL_DIR)/$(MODULE_AIDL_PACKAGE)/INodeIterator.aidl \
	$(LOCAL_DIR)/$(MODULE_AIDL_PACKAGE)/INode.aidl \
	$(LOCAL_DIR)/$(MODULE_AIDL_PACKAGE)/IPropIterator.aidl \
	$(LOCAL_DIR)/$(MODULE_AIDL_PACKAGE)/Property.aidl \

include make/aidl.mk
