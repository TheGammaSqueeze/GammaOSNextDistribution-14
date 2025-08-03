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

LOCAL_PATH := $(call my-dir)

# ============================================================

include $(CLEAR_VARS)

LOCAL_REQUIRED_MODULES := libhardware_legacy
LOCAL_LDLIBS := -llog

LOCAL_CFLAGS += -Wno-unused-parameter -Wno-int-to-pointer-cast
LOCAL_CFLAGS += -Wno-maybe-uninitialized -Wno-parentheses
LOCAL_CPPFLAGS += -Wno-conversion-null -Wunused-variable

LOCAL_C_INCLUDES += \
        frameworks/opt/net/wifi/libwifi_hal/include/wifi_hal \
        frameworks/opt/net/wifi/libwifi_system_iface/include/wifi_system \
        hardware/broadcom/wlan/bcmdhd/wifi_hal \
        external/libnl/include \
        $(call include-path-for, libhardware_legacy)/hardware_legacy \
        external/wpa_supplicant_8/src/drivers \
        libcore/include

LOCAL_SHARED_LIBRARIES += \
                libcutils \
        libc \
        libutils

LOCAL_STATIC_LIBRARIES += libwifi-hal-bcm libnl
LOCAL_PROPRIETARY_MODULE := true
LOCAL_SHARED_LIBRARIES += libcrypto libwifi-hal
LOCAL_SHARED_LIBRARIES += libwifi-system-iface

ifneq ($(wildcard vendor/google/libraries/GoogleWifiConfigLib),)
    LOCAL_SHARED_LIBRARIES += \
        google_wifi_firmware_config_version_c_wrapper
 LOCAL_CFLAGS += -DGOOGLE_WIFI_FW_CONFIG_VERSION_C_WRAPPER
endif

LOCAL_SRC_FILES := \
        halutil.cpp

LOCAL_MODULE := halutil_brcm
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
include $(BUILD_EXECUTABLE)
