LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_ADDITIONAL_DEPENDENCIES += $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr

libarbitrarybytes-def := -Werror
libarbitrarybytes-def += -D_ANDROID_

libarbitrarybytes-inc  := $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr/include
libarbitrarybytes-inc  += $(LOCAL_PATH)/inc
libarbitrarybytes-inc  += $(LOCAL_PATH)/../mm-video-v4l2/vidc/common/inc/
libarbitrarybytes-inc  += $(LOCAL_PATH)/../mm-core/inc

LOCAL_MODULE           := libarbitrarybytes

LOCAL_PRELINK_MODULE   := false
LOCAL_VENDOR_MODULE    := true

LOCAL_C_INCLUDES       := $(libarbitrarybytes-inc)
LOCAL_CFLAGS           := $(libarbitrarybytes-def)

LOCAL_HEADER_LIBRARIES := libutils_headers

LOCAL_SHARED_LIBRARIES := liblog libcutils

LOCAL_SRC_FILES        := src/frameparser.cpp
LOCAL_SRC_FILES        += src/h264_utils.cpp
LOCAL_SRC_FILES        += src/hevc_utils.cpp

include $(BUILD_SHARED_LIBRARY)
