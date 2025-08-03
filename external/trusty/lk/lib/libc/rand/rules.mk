
# compile libc rand as a separate module as it is build
# every time due to randomly changing module define

LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

# Generate a random 32-bit seed for the RNG
KERNEL_LIBC_RANDSEED_HEX := $(shell xxd -l4 -g0 -p /dev/urandom)
KERNEL_LIBC_RANDSEED := 0x$(KERNEL_LIBC_RANDSEED_HEX)U

MODULE_DEFINES += \
	KERNEL_LIBC_RANDSEED=$(KERNEL_LIBC_RANDSEED) \

$(info KERNEL_LIBC_RANDSEED = $(KERNEL_LIBC_RANDSEED))

MODULE_SRCS += \
	$(LOCAL_DIR)/rand.c \

include make/module.mk
