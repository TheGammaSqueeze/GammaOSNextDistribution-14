#
# RPC Service Nanoapp Makefile
#
# Environment Checks ###########################################################
ifeq ($(CHRE_PREFIX),)
  ifneq ($(ANDROID_BUILD_TOP),)
    CHRE_PREFIX = $(ANDROID_BUILD_TOP)/system/chre
  else
    $(error "You must run 'lunch' to setup ANDROID_BUILD_TOP, or explicitly \
    define the CHRE_PREFIX environment variable to point to the CHRE root \
    directory.")
  endif
endif

# Nanoapp Configuration ########################################################

NANOAPP_NAME = rpc_world
NANOAPP_ID = 0x0123456789000013
NANOAPP_NAME_STRING = \"RPC\ Service\ World\"
NANOAPP_VERSION = 0x00000001

NANOAPP_PATH = $(CHRE_PREFIX)/apps/rpc_world


# Source Code ##################################################################

COMMON_SRCS += $(NANOAPP_PATH)/src/rpc_world_manager.cc
COMMON_SRCS += $(NANOAPP_PATH)/src/rpc_world.cc

# Compiler Flags ###############################################################

# Defines
COMMON_CFLAGS += -DNANOAPP_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_DEBUG
COMMON_CFLAGS += -DCHRE_ASSERTIONS_ENABLED

# Includes
COMMON_CFLAGS += -I$(NANOAPP_PATH)/inc

# PW RPC protos ################################################################

PW_RPC_SRCS = $(NANOAPP_PATH)/rpc/rpc_world.proto

# Makefile Includes ############################################################

include $(CHRE_PREFIX)/build/nanoapp/app.mk
