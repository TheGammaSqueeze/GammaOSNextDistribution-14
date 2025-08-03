ifneq (,$(filter arm aarch64 arm64, $(TARGET_ARCH)))
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
ifeq ($(PRODUCT_VENDOR_MOVE_ENABLED),true)
LOCAL_VENDOR_MODULE := true
endif
LOCAL_MODULE := wcnss_service
LOCAL_HEADER_LIBRARIES += vendor_common_inc
LOCAL_SRC_FILES := wcnss_service.c
LOCAL_SHARED_LIBRARIES := libc libcutils libutils liblog
ifeq ($(strip $(TARGET_USES_QCOM_WCNSS_QMI)),true)
LOCAL_CFLAGS += -DWCNSS_QMI
ifeq ($(filter 10% Q% q%,$(TARGET_PLATFORM_VERSION)),)
#For Android R and above, assuming not compiling on Q and lower
LOCAL_HEADER_LIBRARIES += libqmi_common_headers
else
LOCAL_C_INCLUDES += $(TARGET_OUT_HEADERS)/qmi-framework/inc
endif
LOCAL_SRC_FILES += wcnss_qmi_client.c
LOCAL_SHARED_LIBRARIES += libqmiservices libqmi_cci
LOCAL_HEADER_LIBRARIES += libmdmdetect_headers
LOCAL_SHARED_LIBRARIES += libmdmdetect
LOCAL_HEADER_LIBRARIES += libril-qc-qmi-services-headers
endif #TARGET_USES_QCOM_WCNSS_QMI
LOCAL_MODULE_TAGS := optional
LOCAL_CFLAGS += -Wall -Werror
LOCAL_LICENSE_KINDS := SPDX-license-identifier-BSD
LOCAL_LICENSE_CONDITIONS := notice
include $(BUILD_EXECUTABLE)
endif
