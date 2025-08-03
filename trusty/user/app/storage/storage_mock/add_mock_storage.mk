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

# This rule is intended to provide a mock secure storage for host unittest
# to use. This mock storage currently does not support multiple session,
# moving files, opening/closing directories, reading directories, and
# transactions. To use this mock secure storage interface, include this rule
# at the end in your test rule.
LOCAL_DIR := $(GET_LOCAL_DIR)
HOST_SRCS += \
	$(LOCAL_DIR)/storage_mock.c \

HOST_INCLUDE_DIRS += \
	$(LOCAL_DIR) \
	trusty/user/base/lib/storage/include \
	trusty/user/base/interface/storage/include \
