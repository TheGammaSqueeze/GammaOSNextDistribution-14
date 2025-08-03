LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_SRCS += \
	$(LOCAL_DIR)/debug.c

MODULE_DEPS += \
	trusty/kernel/lib/backtrace \

include make/module.mk
