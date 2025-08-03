# Copyright (C) 2021 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

soc_ver := $(TARGET_BOARD_PLATFORM)
LOCAL_MODULE := android.hardware.composer.hwc3-service.pixel

LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/NOTICE

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_PROPRIETARY_MODULE := true

LOCAL_CFLAGS += \
	-DSOC_VERSION=$(soc_ver) \
	-DLOG_TAG=\"hwc-3\" \
	-Wthread-safety

# hwc3 re-uses hwc2.2 ComposerResource and libexynosdisplay
LOCAL_SHARED_LIBRARIES := android.hardware.graphics.composer3-V3-ndk \
	android.hardware.graphics.composer@2.1-resources \
        android.hardware.graphics.composer@2.2-resources \
	android.hardware.graphics.composer@2.4 \
	android.hardware.drm-V1-ndk \
	com.google.hardware.pixel.display-V10-ndk \
	android.frameworks.stats-V2-ndk \
	libpixelatoms_defs \
	pixelatoms-cpp \
	libbase \
	libbinder \
	libbinder_ndk \
	libcutils \
	libexynosdisplay \
	libfmq \
	libhardware \
	libhardware_legacy \
	liblog \
	libsync \
	libutils

LOCAL_STATIC_LIBRARIES := libaidlcommonsupport

LOCAL_HEADER_LIBRARIES := \
	android.hardware.graphics.composer3-command-buffer \
	google_hal_headers \
	libgralloc_headers
LOCAL_HEADER_LIBRARIES += device_kernel_headers

LOCAL_C_INCLUDES := \
	$(TOP)/hardware/google/graphics/common/include \
	$(TOP)/hardware/google/graphics/common/libhwc2.1 \
	$(TOP)/hardware/google/graphics/common/libhwc2.1/libdevice \
	$(TOP)/hardware/google/graphics/common/libhwc2.1/libdisplayinterface \
	$(TOP)/hardware/google/graphics/common/libhwc2.1/libdrmresource/include \
	$(TOP)/hardware/google/graphics/common/libhwc2.1/libhwchelper \
	$(TOP)/hardware/google/graphics/common/libhwc2.1/libhwcService \
	$(TOP)/hardware/google/graphics/common/libhwc2.1/libresource \
	$(TOP)/hardware/google/graphics/$(soc_ver)/include \
	$(TOP)/hardware/google/graphics/$(soc_ver)/libhwc2.1 \
	$(TOP)/hardware/google/graphics/$(soc_ver)/libhwc2.1/libcolormanager \
	$(TOP)/hardware/google/graphics/$(soc_ver)/libhwc2.1/libdevice \
	$(TOP)/hardware/google/graphics/$(soc_ver)/libhwc2.1/libmaindisplay \
	$(TOP)/hardware/google/graphics/$(soc_ver)/libhwc2.1/libresource

LOCAL_SRC_FILES := \
	Composer.cpp \
	ComposerClient.cpp \
	ComposerCommandEngine.cpp \
	impl/HalImpl.cpp \
	impl/ResourceManager.cpp \
	service.cpp

ifeq ($(BOARD_USES_HWC_SERVICES),true)
LOCAL_CFLAGS += -DUSES_HWC_SERVICES
LOCAL_SHARED_LIBRARIES += libExynosHWCService
LOCAL_HEADER_LIBRARIES += libbinder_headers
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

LOCAL_VINTF_FRAGMENTS = hwc3-default.xml
LOCAL_INIT_RC := hwc3-pixel.rc

include $(BUILD_EXECUTABLE)
