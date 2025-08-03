ifeq ($(TARGET_BOARD_PLATFORM),lito)
ifneq ($(BUILD_WITHOUT_VENDOR),true)
LOCAL_PATH := $(call my-dir)
include $(call first-makefiles-under,$(LOCAL_PATH))
endif
endif
