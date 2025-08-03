# Ignore this file during non-NDK builds.
ifdef NDK_ROOT
LOCAL_PATH := $(call my-dir)

################################################################################
# libimageio_util

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    imageio_util.c \

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../src

LOCAL_MODULE := imageio_util

LOCAL_LICENSE_KINDS := SPDX-license-identifier-BSD
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/../COPYING $(LOCAL_PATH)/../NOTICE $(LOCAL_PATH)/../PATENTS
include $(BUILD_STATIC_LIBRARY)

################################################################################
# libimagedec

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    image_dec.c \
    jpegdec.c \
    metadata.c \
    pngdec.c \
    pnmdec.c \
    tiffdec.c \
    webpdec.c \

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../src
LOCAL_STATIC_LIBRARIES := imageio_util

LOCAL_MODULE := imagedec

LOCAL_LICENSE_KINDS := SPDX-license-identifier-BSD
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/../COPYING $(LOCAL_PATH)/../NOTICE $(LOCAL_PATH)/../PATENTS
include $(BUILD_STATIC_LIBRARY)

################################################################################
# libimageenc

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    image_enc.c \

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../src
LOCAL_STATIC_LIBRARIES := imageio_util

LOCAL_MODULE := imageenc

LOCAL_LICENSE_KINDS := SPDX-license-identifier-BSD
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/../COPYING $(LOCAL_PATH)/../NOTICE $(LOCAL_PATH)/../PATENTS
include $(BUILD_STATIC_LIBRARY)
endif  # NDK_ROOT
