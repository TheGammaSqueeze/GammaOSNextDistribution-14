LOCAL_PATH := $(call my-dir)
ifneq ($(filter $(LOCAL_PATH),$(PRODUCT_SOONG_NAMESPACES)),)
ifneq ($(BUILD_WITHOUT_VENDOR), true)
include $(call first-makefiles-under,$(LOCAL_PATH))
endif
endif
