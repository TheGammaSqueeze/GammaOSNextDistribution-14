LOCAL_PATH := $(call my-dir)

$(eval $(call declare-1p-copy-files,hardware/google/camera,))

# Include the sub-makefiles
include $(call all-makefiles-under,$(LOCAL_PATH))
