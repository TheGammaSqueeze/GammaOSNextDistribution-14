ifeq ($(WITH_SMP),1)

LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_DEPS += \
	trusty/kernel/lib/unittest \

MODULE_SRCS += \
	$(LOCAL_DIR)/smptest.c \

include make/module.mk

endif
