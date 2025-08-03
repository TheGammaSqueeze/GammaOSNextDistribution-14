LOCAL_DIR := $(GET_LOCAL_DIR)
COMMON_DIR := $(LOCAL_DIR)/../common

TRUSTY_APP_NAME := cast_auth_test

MODULE := $(LOCAL_DIR)

MODULE_INCLUDES += \
	$(COMMON_DIR) \

MANIFEST := $(LOCAL_DIR)/manifest.json

MODULE_SRCS += \
	$(LOCAL_DIR)/main.cc

MODULE_LIBRARY_DEPS += \
	trusty/user/base/lib/libstdc++-trusty \
	trusty/user/base/lib/libc-trusty \
	trusty/user/base/lib/unittest \
	trusty/user/base/experimental/lib/tidl \
	frameworks/native/libs/binder/trusty \

MODULE_LIBRARY_DEPS += \
	trusty/user/app/cast-auth/aidl \

include make/trusted_app.mk
