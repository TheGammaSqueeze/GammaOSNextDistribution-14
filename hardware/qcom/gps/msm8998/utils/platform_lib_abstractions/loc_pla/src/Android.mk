ifneq ($(BOARD_VENDOR_QCOM_GPS_LOC_API_HARDWARE),)
ifneq ($(BUILD_TINY_ANDROID),true)
#Compile this library only for builds with the latest modem image

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

## Libs
LOCAL_SHARED_LIBRARIES := \
    libutils \
    libcutils \
    liblog \
    libloc_stub \
    libprocessgroup

LOCAL_SRC_FILES += \
        platform_lib_gettid.cpp \
        platform_lib_log_util.cpp \
        platform_lib_property_service.cpp \
        platform_lib_sched_policy.cpp \
        platform_lib_time.cpp

LOCAL_CFLAGS += \
     -fno-short-enums \
     -D_ANDROID_ \
     -Wall \
     -Werror \
     -Wno-macro-redefined \
     -std=c++11

## Includes
LOCAL_C_INCLUDES:= \
    $(LOCAL_PATH)/../include

LOCAL_HEADER_LIBRARIES := \
    libgps.utils_headers \
    libloc_stub_headers

LOCAL_MODULE := libloc_pla
LOCAL_LICENSE_KINDS := SPDX-license-identifier-BSD
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_VENDOR_MODULE := true
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_OWNER := qti

LOCAL_PRELINK_MODULE := false

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libloc_pla_headers
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/../include
include $(BUILD_HEADER_LIBRARY)

endif # not BUILD_TINY_ANDROID
endif # BOARD_VENDOR_QCOM_GPS_LOC_API_HARDWARE
