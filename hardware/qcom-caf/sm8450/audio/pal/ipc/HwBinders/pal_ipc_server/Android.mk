LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

ifeq ($(SOONG_CONFIG_android_hardware_audio_run_64bit), true)
LOCAL_MULTILIB := 64
endif

LOCAL_MODULE := vendor.qti.hardware.pal@1.0-impl
LOCAL_MODULE_OWNER := qti
LOCAL_VENDOR_MODULE := true
LOCAL_CFLAGS += -v
LOCAL_CFLAGS += \
    -Wno-unused-parameter \
    -Wno-unused-variable \
    -Wno-format \
    -Wno-missing-field-initializers \
    -Wno-unused-function

LOCAL_SRC_FILES := \
    src/pal_server_wrapper.cpp

LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/inc

LOCAL_SHARED_LIBRARIES := \
    libhidlbase \
    libhidltransport \
    libutils \
    liblog \
    libcutils \
    libhardware \
    libbase \
    vendor.qti.hardware.pal@1.0 \
    libar-pal

LOCAL_HEADER_LIBRARIES := \
    libarpal_headers

include $(BUILD_SHARED_LIBRARY)

