LOCAL_DIR := $(GET_LOCAL_DIR)
COMMON_DIR := $(LOCAL_DIR)/common

TRUSTY_APP_NAME := cast_auth

MODULE := $(LOCAL_DIR)

MODULE_INCLUDES += \
	$(COMMON_DIR) \

MANIFEST := $(LOCAL_DIR)/manifest.json

MODULE_SRCS += \
	$(LOCAL_DIR)/main.cc \
	$(LOCAL_DIR)/cast_auth_impl.cc \

MODULE_LIBRARY_DEPS += \
	trusty/user/base/lib/libstdc++-trusty \
	external/boringssl \
	trusty/user/base/lib/keybox/client \
	trusty/user/base/lib/storage \
	trusty/user/base/lib/system_state \
	trusty/user/base/experimental/lib/tidl \
	trusty/user/base/lib/keybox/client \
	frameworks/native/libs/binder/trusty \

MODULE_LIBRARY_DEPS += \
	trusty/user/app/cast-auth/aidl \

include make/trusted_app.mk
