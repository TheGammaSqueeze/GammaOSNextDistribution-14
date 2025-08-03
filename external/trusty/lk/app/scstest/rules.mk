LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_DEPS += \
	trusty/kernel/lib/unittest \
	trusty/kernel/lib/mmutest \

MODULE_SRCS += \
	$(LOCAL_DIR)/scstest.c \

include make/module.mk
