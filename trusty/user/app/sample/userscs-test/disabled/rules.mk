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

# TODO TRUSTY_APP_DISABLE_SCS currently only works for apps w/o library
# dependencies. We need a way to disable shadow call stacks for an app and its
# libraries. Using TRUSTY_APP_DISABLE_SCS only works because this app has no
# library dependencies other than libc-trusty which already disables SCS.
# Blocked on https://android-review.googlesource.com/c/trusty/lib/+/1706286
TRUSTY_APP_DISABLE_SCS := true

MODULE_CONSTANTS := $(LOCAL_DIR)/consts.json

MODULE_SRCS += \
	$(LOCAL_DIR)/../scs_test_app.c \

MODULE_LIBRARY_DEPS += \
	trusty/user/base/lib/libc-trusty \

include make/trusted_app.mk
