local_target_dir := $(TARGET_OUT_DATA)/local/tmp
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=    \
    pagingtest.c    \
    mmap_test.c      \
    pageinout_test.c \
    thrashing_test.c

LOCAL_MODULE:= pagingtest
LOCAL_LICENSE_KINDS:= SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS:= notice
LOCAL_NOTICE_FILE:= $(LOCAL_PATH)/../NOTICE

LOCAL_CFLAGS := -Wall -Werror

LOCAL_MODULE_TAGS := tests

LOCAL_MODULE_PATH := $(local_target_dir)
LOCAL_MULTILIB := both
LOCAL_MODULE_STEM_32 := pagingtest
LOCAL_MODULE_STEM_64 := pagingtest64

include $(BUILD_EXECUTABLE)
