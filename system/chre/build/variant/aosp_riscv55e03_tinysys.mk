#
# Google Reference CHRE Implementation for MTK riscv (v55e03) Tinysys
#

include $(CHRE_PREFIX)/build/clean_build_template_args.mk

TARGET_NAME = aosp_riscv55e03_tinysys
ifneq ($(filter $(TARGET_NAME)% all, $(MAKECMDGOALS)),)

ifeq ($(RISCV_TINYSYS_PREFIX),)
$(error "The tinysys code directory needs to be exported as the RISCV_TINYSYS_PREFIX \
         environment variable")
endif

TARGET_CFLAGS = $(TINYSYS_CFLAGS)
TARGET_VARIANT_SRCS = $(TINYSYS_SRCS)
TARGET_BIN_LDFLAGS = $(AOSP_RISCV_TINYSYS_BIN_LDFLAGS)
TARGET_SO_EARLY_LIBS = $(AOSP_RISCV_TINYSYS_EARLY_LIBS)
TARGET_SO_LATE_LIBS = $(AOSP_RISCV_TINYSYS_LATE_LIBS)
TARGET_PLATFORM_ID = 0x476f6f676c003000

# Macros #######################################################################

TINYSYS_CFLAGS += -D__riscv
TINYSYS_CFLAGS += -DMRV55
TINYSYS_CFLAGS += -D_LIBCPP_HAS_NO_LONG_LONG

TINYSYS_CFLAGS += --target=riscv32-unknown-elf
TINYSYS_CFLAGS += -march=rv32imafcv
TINYSYS_CFLAGS += -mcpu=MRV55E03

# Word size for this architecture
TARGET_CFLAGS += -DCHRE_32_BIT_WORD_SIZE

# chre platform
TARGET_CFLAGS += -DCHRE_FIRST_SUPPORTED_API_VERSION=CHRE_API_VERSION_1_7
TARGET_CFLAGS += -DCHRE_MESSAGE_TO_HOST_MAX_SIZE=4096
TARGET_CFLAGS += -DCHRE_USE_BUFFERED_LOGGING
# enable static allocation in freertos
TINYSYS_CFLAGS += -DCFG_STATIC_ALLOCATE

# Compiling flags ##############################################################

# -fpic and -shared are only needed for dynamic linking
ifeq ($(IS_ARCHIVE_ONLY_BUILD),)
TARGET_SO_LDFLAGS += -shared
TARGET_CFLAGS += -fpic

# Enable compiler-rt dependencies
LLVM_RTLIB=$(RISCV_TOOLCHAIN_PATH)/lib/clang/9.0.1/libpic/riscv32/MRV55E03
TARGET_SO_LDFLAGS += -L$(LLVM_RTLIB)
TARGET_SO_LDFLAGS += -lclang_rt.builtins-riscv32
endif

ifneq ($(IS_NANOAPP_BUILD),)
# Used to expose libc headers to nanoapps that aren't supported on the given platform
TARGET_CFLAGS += -I$(CHRE_PREFIX)/platform/shared/include/chre/platform/shared/libc

TARGET_VARIANT_SRCS += $(DSO_SUPPORT_LIB_SRCS)
TARGET_CFLAGS += $(DSO_SUPPORT_LIB_CFLAGS)

ifeq ($(CHRE_TCM_ENABLED),true)
TARGET_CFLAGS += -DCHRE_TCM_ENABLED
# Flags:
# Signed                 = 0x00000001
# TCM-capable            = 0x00000004
TARGET_NANOAPP_FLAGS = 0x00000005
endif
endif

# Other makefiles ##############################################################

include $(CHRE_PREFIX)/platform/shared/mbedtls/mbedtls.mk
include $(CHRE_PREFIX)/build/arch/riscv.mk
include $(CHRE_PREFIX)/build/build_template.mk
endif

