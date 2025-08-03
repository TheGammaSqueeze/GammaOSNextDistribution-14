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

ifeq ($(filter $(TARGET_BOARD_PLATFORM), msmnile),$(TARGET_BOARD_PLATFORM))
OMXCORE_CFLAGS += -D_NILE_
else ifeq ($(filter $(TARGET_BOARD_PLATFORM), $(MSMSTEPPE)),$(TARGET_BOARD_PLATFORM))
OMXCORE_CFLAGS += -D_STEPPE_
else ifeq ($(filter $(TARGET_BOARD_PLATFORM), $(TRINKET)),$(TARGET_BOARD_PLATFORM))
OMXCORE_CFLAGS += -D_TRINKET_
else ifeq ($(filter $(TARGET_BOARD_PLATFORM), atoll),$(TARGET_BOARD_PLATFORM))
OMXCORE_CFLAGS += -D_ATOLL_
else
OMXCORE_CFLAGS += -D_DEFAULT_
endif

ifeq ($(call is-platform-sdk-version-at-least,27),true) # O-MR1
OMXCORE_CFLAGS += -D_ANDROID_O_MR1_DIVX_CHANGES
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
ifneq (,$(filter msmnile sdmshrike $(MSMSTEPPE) $(TRINKET) atoll,$(TARGET_BOARD_PLATFORM)))
LOCAL_SRC_FILES         += src/registry_table_android.c
else
LOCAL_SRC_FILES         += src/qc_registry_table_android.c
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
ifneq (,$(filter msmnile sdmshrike $(MSMSTEPPE) $(TRINKET) atoll,$(TARGET_BOARD_PLATFORM)))
LOCAL_SRC_FILES         += src/$(MM_CORE_TARGET)/registry_table.c
else
LOCAL_SRC_FILES         += src/$(MM_CORE_TARGET)/qc_registry_table.c
endif

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE := libomxcore_headers
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/inc
LOCAL_VENDOR_MODULE := true

include $(BUILD_HEADER_LIBRARY)

endif #BUILD_TINY_ANDROID
