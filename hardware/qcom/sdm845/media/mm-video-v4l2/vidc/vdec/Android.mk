LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# ---------------------------------------------------------------------------------
# 				Common definitons
# ---------------------------------------------------------------------------------

libmm-vdec-def := -D__alignx\(x\)=__attribute__\(\(__aligned__\(x\)\)\)
libmm-vdec-def += -D__align=__alignx
libmm-vdec-def += -Dinline=__inline
libmm-vdec-def += -g -O3
libmm-vdec-def += -DIMAGE_APPS_PROC
libmm-vdec-def += -D_ANDROID_
libmm-vdec-def += -DCDECL
libmm-vdec-def += -DT_ARM
libmm-vdec-def += -DNO_ARM_CLZ
libmm-vdec-def += -UENABLE_DEBUG_LOW
libmm-vdec-def += -UENABLE_DEBUG_HIGH
libmm-vdec-def += -DENABLE_DEBUG_ERROR
libmm-vdec-def += -UINPUT_BUFFER_LOG
libmm-vdec-def += -UOUTPUT_BUFFER_LOG
libmm-vdec-def += -Wno-parentheses
libmm-vdec-def += -D_ANDROID_ICS_
libmm-vdec-def += -DPROCESS_EXTRADATA_IN_OUTPUT_PORT

TARGETS_THAT_HAVE_VENUS_HEVC := apq8084 msm8994 msm8996
TARGETS_THAT_SUPPORT_UBWC := msm8996 msm8953 msm8998 sdm660 sdm845 msmpeafowl sdm710 qcs605
TARGETS_THAT_NEED_SW_VDEC := msm8937 sdm845 msmpeafowl sdm710 qcs605

ifneq (,$(call is-board-platform-in-list2, $(TARGETS_THAT_HAVE_VENUS_HEVC)))
libmm-vdec-def += -DVENUS_HEVC
endif

ifeq ($(TARGET_BOARD_PLATFORM),msm8610)
libmm-vdec-def += -DSMOOTH_STREAMING_DISABLED
endif

ifneq (,$(call is-board-platform-in-list2, $(TARGETS_THAT_SUPPORT_UBWC)))
libmm-vdec-def += -D_UBWC_
endif

ifeq ($(TARGET_USES_ION),true)
libmm-vdec-def += -DUSE_ION
endif

ifneq (1,$(filter 1,$(shell echo "$$(( $(PLATFORM_SDK_VERSION) >= 18 ))" )))
libmm-vdec-def += -DANDROID_JELLYBEAN_MR1=1
endif

ifneq (,$(call is-board-platform-in-list2, $(MASTER_SIDE_CP_TARGET_LIST)))
libmm-vdec-def += -DMASTER_SIDE_CP
endif

include $(CLEAR_VARS)

# Common Includes
libmm-vdec-inc          := $(LOCAL_PATH)/inc
libmm-vdec-inc          += $(QCOM_MEDIA_ROOT)/mm-video-v4l2/vidc/common/inc
libmm-vdec-inc          += $(QCOM_MEDIA_ROOT)/mm-core/inc
libmm-vdec-inc          += $(QCOM_MEDIA_ROOT)/libplatformconfig
libmm-vdec-inc          += $(TARGET_OUT_HEADERS)/adreno
libmm-vdec-inc          += $(QCOM_MEDIA_ROOT)/libc2dcolorconvert
libmm-vdec-inc          += $(TARGET_OUT_HEADERS)/mm-video/SwVdec
libmm-vdec-inc          += $(TARGET_OUT_HEADERS)/mm-video/swvdec
libmm-vdec-inc          += $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr/include

ifeq ($(PLATFORM_SDK_VERSION), 18)  #JB_MR2
libmm-vdec-def += -DANDROID_JELLYBEAN_MR2=1
libmm-vdec-inc += $(QCOM_MEDIA_ROOT)/libstagefrighthw
endif

# Common Dependencies
libmm-vdec-add-dep := $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr

ifeq (T,T)  # TODO: Obsolete, please remove
# This feature is enabled for Android KK+
libmm-vdec-def += -DADAPTIVE_PLAYBACK_SUPPORTED
endif

ifeq (T,T)  # TODO: Obsolete, please remove
# This feature is enabled for Android LMR1
libmm-vdec-def += -DFLEXYUV_SUPPORTED
endif

libmm-vdec-def += -DALLOCATE_OUTPUT_NATIVEHANDLE

# ---------------------------------------------------------------------------------
# 			Make the Shared library (libOmxVdec)
# ---------------------------------------------------------------------------------

include $(CLEAR_VARS)

LOCAL_MODULE                    := libOmxVdec
LOCAL_LICENSE_KINDS             := SPDX-license-identifier-BSD
LOCAL_LICENSE_CONDITIONS        := notice
LOCAL_NOTICE_FILE               := $(LOCAL_PATH)/../../../NOTICE
LOCAL_MODULE_TAGS               := optional
LOCAL_VENDOR_MODULE             := true
LOCAL_CFLAGS                    := $(libmm-vdec-def) -Werror

LOCAL_HEADER_LIBRARIES := \
        display_headers \
        media_plugin_headers \
        libnativebase_headers \
        libutils_headers \
        libhardware_headers

LOCAL_C_INCLUDES                += $(libmm-vdec-inc)
LOCAL_ADDITIONAL_DEPENDENCIES   := $(libmm-vdec-add-dep)

LOCAL_PRELINK_MODULE    := false
LOCAL_SHARED_LIBRARIES  := liblog libcutils libdl
LOCAL_SHARED_LIBRARIES  += libc2dcolorconvert
LOCAL_SHARED_LIBRARIES  += libqdMetaData
LOCAL_SHARED_LIBRARIES  += libplatformconfig
LOCAL_SHARED_LIBRARIES  += libnativewindow

LOCAL_SRC_FILES         := src/ts_parser.cpp
LOCAL_STATIC_LIBRARIES  := libOmxVidcCommon
LOCAL_SRC_FILES         += src/omx_vdec_v4l2.cpp

LOCAL_CFLAGS            += -Wno-error

include $(BUILD_SHARED_LIBRARY)



# ---------------------------------------------------------------------------------
# 			Make the Shared library (libOmxSwVdec)
# ---------------------------------------------------------------------------------

include $(CLEAR_VARS)
ifneq "$(wildcard $(QCPATH) )" ""
ifneq (,$(call is-board-platform-in-list2, $(TARGETS_THAT_NEED_SW_VDEC)))

LOCAL_MODULE                  := libOmxSwVdec
LOCAL_LICENSE_KINDS           := SPDX-license-identifier-BSD
LOCAL_LICENSE_CONDITIONS      := notice
LOCAL_NOTICE_FILE             := $(LOCAL_PATH)/../../../NOTICE
LOCAL_MODULE_TAGS             := optional
LOCAL_VENDOR_MODULE           := true
LOCAL_CFLAGS                  := $(libmm-vdec-def)

LOCAL_HEADER_LIBRARIES := \
        display_headers \
        media_plugin_headers \
        libnativebase_headers \
        libutils_headers \
        libhardware_headers

LOCAL_C_INCLUDES              += $(libmm-vdec-inc)
LOCAL_ADDITIONAL_DEPENDENCIES := $(libmm-vdec-add-dep)

LOCAL_PRELINK_MODULE          := false
LOCAL_SHARED_LIBRARIES        := liblog libcutils libc2dcolorconvert libswvdec
LOCAL_SHARED_LIBRARIES        += libnativewindow

LOCAL_SRC_FILES               := src/omx_swvdec.cpp
LOCAL_SRC_FILES               += src/omx_swvdec_utils.cpp

include $(BUILD_SHARED_LIBRARY)
endif
endif

# ---------------------------------------------------------------------------------
#                END
# ---------------------------------------------------------------------------------
