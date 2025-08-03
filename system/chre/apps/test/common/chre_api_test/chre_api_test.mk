#
# CHRE API Test Nanoapp Makefile
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

NANOAPP_PATH = $(CHRE_PREFIX)/apps/test/common/chre_api_test

# Source Code ##################################################################

COMMON_SRCS += $(NANOAPP_PATH)/src/chre_api_test_manager.cc
COMMON_SRCS += $(NANOAPP_PATH)/src/chre_api_test_service.cc
COMMON_SRCS += $(NANOAPP_PATH)/src/chre_api_test.cc

# Utilities ####################################################################

COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/ble.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/string.cc

# Compiler Flags ###############################################################

# Defines
COMMON_CFLAGS += -DNANOAPP_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_DEBUG
COMMON_CFLAGS += -DCHRE_ASSERTIONS_ENABLED
COMMON_CFLAGS += -DPB_FIELD_16BIT
COMMON_CFLAGS += -fno-threadsafe-statics

# Includes
COMMON_CFLAGS += -I$(NANOAPP_PATH)/inc

# Permission declarations ######################################################

CHRE_NANOAPP_USES_BLE = true
CHRE_NANOAPP_USES_AUDIO = true

# PW RPC protos ################################################################

PW_RPC_SRCS = $(NANOAPP_PATH)/rpc/chre_api_test.proto
PW_RPC_SRCS += $(ANDROID_BUILD_TOP)/external/protobuf/src/google/protobuf/empty.proto
