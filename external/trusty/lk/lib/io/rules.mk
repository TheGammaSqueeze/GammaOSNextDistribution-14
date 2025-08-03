LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_DEPS := \

ifeq (false,$(call TOBOOL,$(WITHOUT_CONSOLE)))
MODULE_DEPS += lib/cbuf
MODULE_SRCS += $(LOCAL_DIR)/console.c

CONSOLE_CALLBACK_DISABLES_SERIAL ?= true
ifeq (true,$(call TOBOOL,$(CONSOLE_CALLBACK_DISABLES_SERIAL)))
MODULE_DEFINES += CONSOLE_CALLBACK_DISABLES_SERIAL=1
endif

# The size of the buffer to capture early boot logs in, which will
# then be dumped to the first print callback to register.
# Set to 0 to disable.
CONSOLE_EARLY_LOG_BUFFER_SIZE ?= 4096
MODULE_DEFINES += EARLY_LOG_BUFFER_SIZE=$(CONSOLE_EARLY_LOG_BUFFER_SIZE)

endif

MODULE_SRCS += \
   $(LOCAL_DIR)/io.c \

include make/module.mk
