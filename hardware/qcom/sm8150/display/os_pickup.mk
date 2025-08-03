ifneq ($(filter msmnile sm6150,$(TARGET_BOARD_PLATFORM)),)
LOCAL_PATH := $(call my-dir)
subdir_makefiles=$(call first-makefiles-under,$(LOCAL_PATH))
$(foreach mk,$(subdir_makefiles),$(info including $(mk) ...)$(eval include $(mk)))
endif
