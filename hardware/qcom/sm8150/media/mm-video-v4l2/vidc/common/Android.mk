ROOT_DIR := $(call my-dir)

include $(CLEAR_VARS)

LIBION_HEADER_PATHS = system/memory/libion/include \
                      system/memory/libion/kernel-headers

LOCAL_PATH:= $(ROOT_DIR)

# ---------------------------------------------------------------------------------
# 				Common definitons
# ---------------------------------------------------------------------------------

libmm-vidc-def := -g -O3 -Dlrintf=_ffix_r
libmm-vidc-def += -D__align=__alignx
libmm-vidc-def += -D__alignx\(x\)=__attribute__\(\(__aligned__\(x\)\)\)
libmm-vidc-def += -DT_ARM
libmm-vidc-def += -Dinline=__inline
libmm-vidc-def += -D_ANDROID_
libmm-vidc-def += -Werror
libmm-vidc-def += -D_ANDROID_ICS_

# ---------------------------------------------------------------------------------
# 			Make the Shared library (libOmxVidcCommon)
# ---------------------------------------------------------------------------------

libmm-vidc-inc      := $(LOCAL_PATH)/inc
libmm-vidc-inc      += $(QCOM_MEDIA_ROOT)/mm-core/inc
libmm-vidc-inc      += $(QCOM_MEDIA_ROOT)/libc2dcolorconvert
libmm-vidc-inc      += $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr/include
libmm-vidc-inc      += $(TOP)/hardware/libhardware/include
libmm-vidc-inc      += $(LIBION_HEADER_PATHS)

LOCAL_MODULE                    := libOmxVidcCommon
LOCAL_LICENSE_KINDS             := SPDX-license-identifier-BSD
LOCAL_LICENSE_CONDITIONS        := notice
LOCAL_NOTICE_FILE               := $(LOCAL_PATH)/../../../LICENSE
LOCAL_MODULE_TAGS               := optional
LOCAL_VENDOR_MODULE             := true
LOCAL_CFLAGS                    := $(libmm-vidc-def)
LOCAL_C_INCLUDES                := $(libmm-vidc-inc)

LOCAL_PRELINK_MODULE      := false
LOCAL_SHARED_LIBRARIES    := liblog libcutils libdl

LOCAL_HEADER_LIBRARIES := \
        libutils_headers display_intf_headers

LOCAL_SRC_FILES   += src/vidc_common.cpp
LOCAL_SRC_FILES   += src/vidc_vendor_extensions.cpp

LOCAL_ADDITIONAL_DEPENDENCIES += $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr

include $(BUILD_STATIC_LIBRARY)

# ---------------------------------------------------------------------------------
# 					END
# ---------------------------------------------------------------------------------
