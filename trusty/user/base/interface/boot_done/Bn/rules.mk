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

LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)



IFACE_USE_PREBUILTS := 1

ifeq ($(IFACE_USE_PREBUILTS),1)


# for the Bn (Binder Native), i.e. the service, we shall not link in
# the client c wrapper (however the service can reuse the c wrapper header).

MODULE_SRCS := \
	$(LOCAL_DIR)/../generated/IBootDone.cpp \

MODULE_LIBRARY_EXPORTED_DEPS += \
	trusty/user/base/experimental/lib/tidl \

MODULE_EXPORT_INCLUDES += \
	$(LOCAL_DIR)/../generated/include

include make/library.mk

else

AIDL_CWRAPPER_DOMAIN := trusty_user

MODULE_AIDLS := \
	$(LOCAL_DIR)/../IBootDone.aidl \

AIDL_CWRAPPER_BN := $(findstring Bn, $(shell basename $(LOCAL_DIR)))

include make/aidl.mk
endif
