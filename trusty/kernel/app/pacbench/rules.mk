LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_CFLAGS += -include $(LOCAL_DIR)/pacbench_$(ARCH).h

MODULE_DEPS += \
	trusty/kernel/lib/unittest \

MODULE_SRCS += \
	$(LOCAL_DIR)/pacbench.c

include make/module.mk
