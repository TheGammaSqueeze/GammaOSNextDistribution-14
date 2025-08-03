LOCAL_PATH:= $(call my-dir)

ifneq ($(QCPATH),)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=     \
    src/hidl_client.cpp    \
    src/client_reader.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)/inc

LOCAL_CFLAGS += -Wno-unused-variable
LOCAL_CFLAGS += -Wno-unused-parameter
LOCAL_CFLAGS += -Wno-macro-redefined

LOCAL_MODULE:= libbt-hidlclient
LOCAL_MODULE_SUFFIX:= .so
LOCAL_SHARED_LIBRARIES += libutils
LOCAL_SHARED_LIBRARIES += libhidlbase
LOCAL_SHARED_LIBRARIES += libhidltransport
LOCAL_SHARED_LIBRARIES += android.hardware.bluetooth@1.0
LOCAL_SHARED_LIBRARIES += com.qualcomm.qti.ant@1.0
LOCAL_SHARED_LIBRARIES += vendor.qti.hardware.fm@1.0
LOCAL_SHARED_LIBRARIES += liblog

LOCAL_MODULE_PATH_32 := $(TARGET_OUT_VENDOR)/lib
LOCAL_MODULE_PATH_64 := $(TARGET_OUT_VENDOR)/lib64
LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)
endif
