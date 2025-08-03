LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE       := init.qti.display_boot.sh
LOCAL_MODULE_TAGS  := optional
LOCAL_MODULE_CLASS := ETC
LOCAL_SRC_FILES    := init.qti.display_boot.sh
LOCAL_INIT_RC      := init.qti.display_boot.rc
LOCAL_MODULE_PATH  := $(TARGET_OUT_VENDOR_EXECUTABLES)
LOCAL_LICENSE_KINDS := SPDX-license-identifier-BSD
LOCAL_LICENSE_CONDITIONS := notice
include $(BUILD_PREBUILT)
