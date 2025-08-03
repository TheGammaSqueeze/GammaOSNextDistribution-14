# TODO: Remove this file, bootloader_unit_test is still referenced on old branches

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := bootloader_unit_test
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/../NOTICE
LOCAL_MODULE_TAGS := tests

include $(BUILD_PHONY_PACKAGE)
