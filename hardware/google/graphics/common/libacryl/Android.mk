# Copyright (C) 2016 The Android Open Source Project
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

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CFLAGS += -DLOG_TAG=\"hwc-libacryl\"
LOCAL_CFLAGS += -Wthread-safety
#LOCAL_CFLAGS += -DLIBACRYL_DEBUG

ifdef BOARD_LIBACRYL_DEFAULT_COMPOSITOR
    LOCAL_CFLAGS += -DLIBACRYL_DEFAULT_COMPOSITOR=\"$(BOARD_LIBACRYL_DEFAULT_COMPOSITOR)\"
else
    LOCAL_CFLAGS += -DLIBACRYL_DEFAULT_COMPOSITOR=\"no_default_compositor\"
endif
ifdef BOARD_LIBACRYL_DEFAULT_SCALER
    LOCAL_CFLAGS += -DLIBACRYL_DEFAULT_SCALER=\"$(BOARD_LIBACRYL_DEFAULT_SCALER)\"
else
    LOCAL_CFLAGS += -DLIBACRYL_DEFAULT_SCALER=\"no_default_scaler\"
endif
ifdef BOARD_LIBACRYL_DEFAULT_BLTER
    LOCAL_CFLAGS += -DLIBACRYL_DEFAULT_BLTER=\"$(BOARD_LIBACRYL_DEFAULT_BLTER)\"
else
    LOCAL_CFLAGS += -DLIBACRYL_DEFAULT_BLTER=\"no_default_blter\"
endif

LOCAL_SHARED_LIBRARIES := liblog libutils libcutils libion_google android.hardware.graphics.common-V3-ndk
ifdef BOARD_LIBACRYL_G2D_HDR_PLUGIN
    LOCAL_SHARED_LIBRARIES += $(BOARD_LIBACRYL_G2D_HDR_PLUGIN)
    LOCAL_CFLAGS += -DLIBACRYL_G2D_HDR_PLUGIN
endif

ifeq ($(CLANG_COVERAGE),true)
# enable code coverage (these flags are copied from build/soong/cc/coverage.go)
LOCAL_CFLAGS += -fprofile-instr-generate -fcoverage-mapping
LOCAL_CFLAGS += -Wno-frame-larger-than=
LOCAL_WHOLE_STATIC_LIBRARIES += libprofile-clang-extras_ndk
LOCAL_LDFLAGS += -fprofile-instr-generate
LOCAL_LDFLAGS += -Wl,--wrap,open

ifeq ($(CLANG_COVERAGE_CONTINUOUS_MODE),true)
LOCAL_CFLAGS += -mllvm -runtime-counter-relocation
LOCAL_LDFLAGS += -Wl,-mllvm=-runtime-counter-relocation
endif
endif

LOCAL_HEADER_LIBRARIES += google_libacryl_hdrplugin_headers
LOCAL_HEADER_LIBRARIES += google_hal_headers
LOCAL_HEADER_LIBRARIES += libgralloc_headers

LOCAL_C_INCLUDES := $(LOCAL_PATH)/local_include
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
LOCAL_C_INCLUDES += $(TOP)/hardware/google/graphics/$(TARGET_BOARD_PLATFORM)/libcap

LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/include

LOCAL_SRC_FILES := acrylic.cpp acrylic_g2d.cpp
LOCAL_SRC_FILES += acrylic_factory.cpp acrylic_layer.cpp acrylic_formats.cpp
LOCAL_SRC_FILES += acrylic_performance.cpp acrylic_device.cpp

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libacryl
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/NOTICE
ifeq ($(BOARD_USES_VENDORIMAGE), true)
LOCAL_PROPRIETARY_MODULE := true
endif

include $(BUILD_SHARED_LIBRARY)
