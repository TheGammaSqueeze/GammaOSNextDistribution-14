LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_SRCS += \
	$(LOCAL_DIR)/asid.c \
	$(LOCAL_DIR)/bootalloc.c \
	$(LOCAL_DIR)/mmu_common.c \
	$(LOCAL_DIR)/physmem.c \
	$(LOCAL_DIR)/pmm.c \
	$(LOCAL_DIR)/relocate.c \
	$(LOCAL_DIR)/res_group.c \
	$(LOCAL_DIR)/vm.c \
	$(LOCAL_DIR)/vmm.c \

ifeq ($(call TOBOOL,$(KERNEL_BASE_ASLR)), true)
MODULE_SRCS += $(LOCAL_DIR)/aslr.c
endif

MODULE_DEPS += \
	lib/binary_search_tree \

include make/module.mk
