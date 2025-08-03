LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_DEPS := \
	lib/debug \
	lib/heap \
	trusty/kernel/lib/rand

ifeq ($(LK_LIBC_IMPLEMENTATION),lk)
MODULE_DEPS += lib/libc
GLOBAL_DEFINES += LK_LIBC_IMPLEMENTATION_IS_LK=1
else ifeq ($(LK_LIBC_IMPLEMENTATION),musl)
MODULE_DEPS += trusty/kernel/lib/libc-trusty
GLOBAL_DEFINES += LK_LIBC_IMPLEMENTATION_IS_MUSL=1
else
$(error Unknown libc implementation selected)
endif

MODULE_SRCS := \
	$(LOCAL_DIR)/debug.c \
	$(LOCAL_DIR)/event.c \
	$(LOCAL_DIR)/init.c \
	$(LOCAL_DIR)/mutex.c \
	$(LOCAL_DIR)/thread.c \
	$(LOCAL_DIR)/timer.c \
	$(LOCAL_DIR)/semaphore.c \
	$(LOCAL_DIR)/mp.c \
	$(LOCAL_DIR)/port.c

ifeq ($(WITH_KERNEL_VM),1)
MODULE_DEPS += kernel/vm
else
MODULE_DEPS += kernel/novm
endif

include make/module.mk
