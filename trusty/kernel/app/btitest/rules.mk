LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_DEPS += \
	trusty/kernel/lib/unittest \

# Only build the test module if BTI is configured on a supported system
ifneq (true-arm64,$(call TOBOOL,$(KERNEL_BTI_ENABLED))-$(ARCH))
MODULE_SRCS += \
	$(LOCAL_DIR)/btitest_stub.c
else
MODULE_SRCS += \
	$(LOCAL_DIR)/btitest.c \
	$(LOCAL_DIR)/btitest_$(ARCH).S
endif

include make/module.mk
