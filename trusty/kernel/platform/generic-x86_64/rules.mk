LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

CPU := generic

SMP_MAX_CPUS ?= 1

MODULE_DEPS += \
	lib/cbuf

MEMBASE := 0x00200000
MEMSIZE := 0x0fe00000

GLOBAL_DEFINES += \
	MEMBASE=$(MEMBASE) \
	MEMSIZE=$(MEMSIZE) \

MODULE_DEFINES += \
	TARGET_SERIAL_IO_BASE=0x3f8 \

GLOBAL_INCLUDES += \
	$(LOCAL_DIR)/include

MODULE_SRCS += \
	$(LOCAL_DIR)/debug.c \
	$(LOCAL_DIR)/platform.c \

MODULE_DEPS += \
	dev/interrupt/x86_lapic \
	dev/timer/x86_generic \

include make/module.mk
