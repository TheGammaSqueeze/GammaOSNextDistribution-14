LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_DEPS += \
	trusty/kernel/lib/unittest \
	trusty/kernel/lib/mmutest \

MODULE_SRCS += \
	$(LOCAL_DIR)/mmutest.c \

include make/module.mk
