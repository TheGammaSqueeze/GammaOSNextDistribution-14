LOCAL_PATH:= $(call my-dir)
include $(call all-makefiles-under,$(LOCAL_PATH))

LOCAL_CFLAGS += -Wno-unused-variable
LOCAL_CFLAGS += -Wno-unused-parameter
LOCAL_CFLAGS += -Wno-sometimes-uninitialized
LOCAL_CFLAGS += -Wno-macro-redefined
LOCAL_CFLAGS += -Wno-format
