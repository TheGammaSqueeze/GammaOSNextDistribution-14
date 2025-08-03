LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_DEPS += \
	trusty/kernel/lib/unittest \

# Only build the test module if PAC is configured on a supported system
ifneq (true-arm64,$(call TOBOOL,$(KERNEL_PAC_ENABLED))-$(ARCH))
MODULE_SRCS += \
	$(LOCAL_DIR)/pactest_stub.c
else
MODULE_SRCS += \
	$(LOCAL_DIR)/pactest.c \
	$(LOCAL_DIR)/pactest_arm64.S
endif

include make/module.mk
