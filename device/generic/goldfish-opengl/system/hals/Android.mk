#
# Copyright 2015 The Android Open-Source Project
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

LOCAL_MODULE := android.hardware.graphics.allocator@3.0-service.ranchu
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/../../LICENSE
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_VENDOR_MODULE := true
LOCAL_SRC_FILES := allocator3.cpp
LOCAL_INIT_RC := android.hardware.graphics.allocator@3.0-service.ranchu.rc
LOCAL_VINTF_FRAGMENTS := android.hardware.graphics.gralloc3.ranchu.xml

LOCAL_SHARED_LIBRARIES += \
    android.hardware.graphics.allocator@3.0 \
    android.hardware.graphics.mapper@3.0 \
    libOpenglSystemCommon \
    libOpenglCodecCommon$(GOLDFISH_OPENGL_LIB_SUFFIX) \
    libbase \
    libcutils \
    libhidlbase \
    liblog \
    libutils \

LOCAL_STATIC_LIBRARIES += libqemupipe.ranchu libGoldfishAddressSpace$(GOLDFISH_OPENGL_LIB_SUFFIX)
LOCAL_HEADER_LIBRARIES += libgralloc_cb.ranchu

LOCAL_C_INCLUDES += \
    hardware/google/gfxstream/guest/GoldfishAddressSpace/include \
    hardware/google/gfxstream/guest/include \
    hardware/google/gfxstream/guest/platform/include \
    hardware/google/gfxstream/guest/iostream/include/libOpenglRender \
    hardware/google/gfxstream/guest/OpenglCodecCommon \
    hardware/google/gfxstream/guest/OpenglSystemCommon \
    hardware/google/gfxstream/guest/renderControl_enc \

LOCAL_CFLAGS += -DVIRTIO_GPU
LOCAL_C_INCLUDES += external/libdrm external/minigbm/cros_gralloc
LOCAL_SHARED_LIBRARIES += libdrm

include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)

LOCAL_MODULE := android.hardware.graphics.mapper@3.0-impl-ranchu
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/../../LICENSE
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_VENDOR_MODULE := true
LOCAL_SRC_FILES := mapper3.cpp

#     android.hardware.graphics.allocator@3.0 \

LOCAL_SHARED_LIBRARIES += \
    android.hardware.graphics.mapper@3.0 \
    libOpenglSystemCommon \
    libOpenglCodecCommon$(GOLDFISH_OPENGL_LIB_SUFFIX) \
    libbase \
    libcutils \
    libhidlbase \
    liblog \
    libutils \
    libsync \
	libandroidemu \

LOCAL_STATIC_LIBRARIES += libqemupipe.ranchu libGoldfishAddressSpace$(GOLDFISH_OPENGL_LIB_SUFFIX)
LOCAL_HEADER_LIBRARIES += libgralloc_cb.ranchu

LOCAL_C_INCLUDES += \
    hardware/google/gfxstream/guest/GoldfishAddressSpace/include \
    hardware/google/gfxstream/guest/include \
    hardware/google/gfxstream/guest/platform/include \
    hardware/google/gfxstream/guest/iostream/include/libOpenglRender \
    hardware/google/gfxstream/guest/OpenglCodecCommon \
    hardware/google/gfxstream/guest/OpenglSystemCommon \
    hardware/google/gfxstream/guest/renderControl_enc \

LOCAL_CFLAGS += -DVIRTIO_GPU
LOCAL_C_INCLUDES += external/libdrm external/minigbm/cros_gralloc
LOCAL_SHARED_LIBRARIES += libdrm

include $(BUILD_SHARED_LIBRARY)
