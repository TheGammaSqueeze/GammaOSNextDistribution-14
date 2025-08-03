LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE               := init.lineage.atv.rc
LOCAL_MODULE_CLASS         := ETC
LOCAL_SRC_FILES            := init.lineage.atv.rc
LOCAL_MODULE_RELATIVE_PATH := init
LOCAL_PRODUCT_MODULE       := true
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE               := init.lineage.atv.scaling.rc
LOCAL_MODULE_CLASS         := ETC
LOCAL_SRC_FILES            := init.lineage.atv.scaling.rc
LOCAL_MODULE_RELATIVE_PATH := init
LOCAL_PRODUCT_MODULE       := true
include $(BUILD_PREBUILT)
