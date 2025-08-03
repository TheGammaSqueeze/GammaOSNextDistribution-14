ifneq ($(BUILD_TINY_ANDROID),true)

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

OMXCORE_CFLAGS := -g -O3 -DVERBOSE
OMXCORE_CFLAGS += -O0 -fno-inline -fno-short-enums
OMXCORE_CFLAGS += -D_ANDROID_
OMXCORE_CFLAGS += -U_ENABLE_QC_MSG_LOG_

#===============================================================================
#             Figure out the targets
#===============================================================================

ifeq ($(filter $(TARGET_BOARD_PLATFORM), sdm845),$(TARGET_BOARD_PLATFORM))
MM_CORE_TARGET = sdm845
else ifeq ($(filter $(TARGET_BOARD_PLATFORM), msmpeafowl),$(TARGET_BOARD_PLATFORM))
MM_CORE_TARGET = msmpeafowl
else ifeq ($(filter $(TARGET_BOARD_PLATFORM), sdm710),$(TARGET_BOARD_PLATFORM))
MM_CORE_TARGET = sdm710
else ifeq ($(filter $(TARGET_BOARD_PLATFORM), qcs605),$(TARGET_BOARD_PLATFORM))
MM_CORE_TARGET = qcs605
else
MM_CORE_TARGET = default
endif

#===============================================================================
#             LIBRARY for Android apps
#===============================================================================

LOCAL_C_INCLUDES        := $(LOCAL_PATH)/src/common
LOCAL_C_INCLUDES        += $(call project-path-for,qcom-media)/libplatformconfig

LOCAL_HEADER_LIBRARIES := \
        libutils_headers \
        libomxcore_headers

LOCAL_EXPORT_HEADER_LIBRARY_HEADERS := libomxcore_headers

LOCAL_PRELINK_MODULE    := false
LOCAL_MODULE            := libOmxCore
LOCAL_MODULE_TAGS       := optional
LOCAL_VENDOR_MODULE     := true
LOCAL_SHARED_LIBRARIES  := liblog libdl libcutils
ifeq ($(call is-board-platform-in-list, $(MSM_VIDC_TARGET_LIST)),true)
LOCAL_SHARED_LIBRARIES  += libplatformconfig
endif
LOCAL_CFLAGS            := $(OMXCORE_CFLAGS)

LOCAL_SRC_FILES         := src/common/omx_core_cmp.cpp
LOCAL_SRC_FILES         += src/common/qc_omx_core.c
ifneq (,$(filter sdm845 msmpeafowl sdm710 qcs605,$(TARGET_BOARD_PLATFORM)))
LOCAL_SRC_FILES         += src/$(MM_CORE_TARGET)/registry_table_android.c
else
LOCAL_SRC_FILES         += src/$(MM_CORE_TARGET)/qc_registry_table_android.c
endif

include $(BUILD_SHARED_LIBRARY)

#===============================================================================
#             LIBRARY for command line test apps
#===============================================================================

include $(CLEAR_VARS)

LOCAL_C_INCLUDES        := $(LOCAL_PATH)/src/common
LOCAL_C_INCLUDES        += $(call project-path-for,qcom-media)/libplatformconfig

LOCAL_HEADER_LIBRARIES := \
        libutils_headers \
        libomxcore_headers

LOCAL_EXPORT_HEADER_LIBRARY_HEADERS := libomxcore_headers

LOCAL_PRELINK_MODULE    := false
LOCAL_MODULE            := libmm-omxcore
LOCAL_MODULE_TAGS       := optional
LOCAL_VENDOR_MODULE     := true
LOCAL_SHARED_LIBRARIES  := liblog libdl libcutils
ifeq ($(call is-board-platform-in-list, $(MSM_VIDC_TARGET_LIST)),true)
LOCAL_SHARED_LIBRARIES  += libplatformconfig
endif
LOCAL_CFLAGS            := $(OMXCORE_CFLAGS)

LOCAL_SRC_FILES         := src/common/omx_core_cmp.cpp
LOCAL_SRC_FILES         += src/common/qc_omx_core.c
ifneq (,$(filter sdm845 msmpeafowl sdm710 qcs605,$(TARGET_BOARD_PLATFORM)))
LOCAL_SRC_FILES         += src/$(MM_CORE_TARGET)/registry_table.c
else
LOCAL_SRC_FILES         += src/$(MM_CORE_TARGET)/qc_registry_table.c
endif

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE := libomxcore_headers
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/inc
LOCAL_PROPRIETARY_MODULE := true

include $(BUILD_HEADER_LIBRARY)

endif #BUILD_TINY_ANDROID
