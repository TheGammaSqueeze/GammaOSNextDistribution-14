LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_SRCS := \
	$(LOCAL_DIR)/gatekeeper_messages.cpp \
	$(LOCAL_DIR)/gatekeeper.cpp

MODULE_EXPORT_INCLUDES += $(LOCAL_DIR)/include/

MODULE_CPPFLAGS := -std=c++11 -Werror -Wunused-parameter

MODULE_INCLUDES := \
	$(LOCAL_DIR)/../../hardware/libhardware/include

MODULE_LIBRARY_DEPS := \
	trusty/user/base/lib/libc-trusty \
	trusty/user/base/lib/libstdc++-trusty \

include make/library.mk
