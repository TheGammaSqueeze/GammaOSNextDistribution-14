LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_DEPS += \
	trusty/kernel/lib/unittest \
	trusty/kernel/lib/mmutest \
	trusty/kernel/lib/trusty \

MODULE_SRCS += \
	$(LOCAL_DIR)/userscstest.c \

include make/module.mk