
ifeq ($(ANDROID_BUILD_TOP),)
$(error "You should supply an ANDROID_BUILD_TOP environment variable \
         containing a path to the Android source tree. This is typically \
         provided by initializing the Android build environment.")
endif

# Variant Prefix ###############################################################

VARIANT_PREFIX = $(ANDROID_BUILD_TOP)/system/chre/variant

# Chre Version String ##########################################################

COMMIT_HASH_COMMAND = git describe --always --long --dirty
COMMIT_HASH = $(shell $(COMMIT_HASH_COMMAND))

COMMON_CFLAGS += -DCHRE_VERSION_STRING="\"chre=tinysys@$(COMMIT_HASH)\""

# Platform-specific Settings ###################################################

TINYSYS_CFLAGS += -DP_MODE_0
TINYSYS_CFLAGS += -DCFG_AMP_CORE1_EN
TINYSYS_CFLAGS += -DCFG_DMA_SUPPORT

TINYSYS_CFLAGS += -DCHRE_FREERTOS_TASK_PRIORITY=2

# Platform-specific Includes ###################################################

# Tinysys include paths
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/kernel/FreeRTOS_v10.1.0.1/FreeRTOS/Source/include
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/kernel/FreeRTOS_v10.1.0.1/FreeRTOS/Source/portable/LLVM/RV55
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/common/drivers/dma/v3/inc
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/common/drivers/irq/v3/inc
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/common/drivers/mbox/v2/inc
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/common/include
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/common/middleware/MemMang/inc
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/scp/drivers/RV55_A/$(TINYSYS_PLATFORM)/dma
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/scp/drivers/RV55_A/$(TINYSYS_PLATFORM)/dvfs/inc
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/scp/drivers/RV55_A/$(TINYSYS_PLATFORM)/intc/inc
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/scp/drivers/RV55_A/$(TINYSYS_PLATFORM)/mbox
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/scp/drivers/RV55_A/$(TINYSYS_PLATFORM)/resrc_req/inc
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/scp/drivers/common/dma/inc
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/scp/drivers/common/dvfs/v2/inc
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/scp/drivers/common/dram_region_mgmt
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/scp/drivers/common/MemMang/inc
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/scp/drivers/common/xgpt/inc
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/scp/middleware/sensorhub/include
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/scp/project/RV55_A/$(TINYSYS_PLATFORM)/platform/inc
TINYSYS_CFLAGS += -I$(RISCV_TINYSYS_PREFIX)/scp/project/RV55_A/common/platform/inc

# Clang include paths
TINYSYS_CFLAGS += -I$(RISCV_TOOLCHAIN_PATH)/lib/clang/9.0.1/include
TINYSYS_CFLAGS += -I$(RISCV_TOOLCHAIN_PATH)/dkwlib/MRV55E03v/include

# Common Compiler Flags ########################################################

# Supply a symbol to indicate that the build variant supplies the static
# nanoapp list.
COMMON_CFLAGS += -DCHRE_VARIANT_SUPPLIES_STATIC_NANOAPP_LIST

# CHRE event count #############################################################

TINYSYS_CFLAGS += -DCHRE_EVENT_PER_BLOCK=32
TINYSYS_CFLAGS += -DCHRE_MAX_EVENT_BLOCKS=4

# Optional Features ############################################################

CHRE_AUDIO_SUPPORT_ENABLED = true
CHRE_GNSS_SUPPORT_ENABLED = false
CHRE_SENSORS_SUPPORT_ENABLED = true
CHRE_WIFI_SUPPORT_ENABLED = false
CHRE_WWAN_SUPPORT_ENABLED = false
CHRE_BLE_SUPPORT_ENABLED = true

# Common Source Files ##########################################################

COMMON_SRCS += $(VARIANT_PREFIX)/tinysys/static_nanoapps.cc
