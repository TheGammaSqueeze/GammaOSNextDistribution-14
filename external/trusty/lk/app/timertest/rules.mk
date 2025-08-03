LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_DEPS += \
	trusty/kernel/lib/unittest \

MODULE_SRCS += \
	$(LOCAL_DIR)/timertest.c \

ifneq (,$(APP_TIMERTEST_MAX_CLOCK_PERIOD))
MODULE_DEFINES += \
	APP_TIMERTEST_MAX_CLOCK_PERIOD=$(APP_TIMERTEST_MAX_CLOCK_PERIOD)
endif

include make/module.mk
