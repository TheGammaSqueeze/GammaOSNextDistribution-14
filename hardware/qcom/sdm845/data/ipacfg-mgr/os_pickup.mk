ifneq ($(filter sdm845 sdm710,$(TARGET_BOARD_PLATFORM)),)
LOCAL_PATH := $(call my-dir)
include $(call first-makefiles-under,$(LOCAL_PATH))
endif
