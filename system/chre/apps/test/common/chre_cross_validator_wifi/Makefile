#
# CHRE Cross Validator WiFi Nanoapp Makefile
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

NANOAPP_NAME = chre_cross_validator_wifi
NANOAPP_ID = 0x476f6f6754000005
NANOAPP_NAME_STRING = \"CHRE\ Cross\ Validator\ WiFi\"
NANOAPP_VERSION = 0x00000001

NANOAPP_PATH = $(CHRE_PREFIX)/apps/test/common/chre_cross_validator_wifi
TEST_SHARED_PATH = $(CHRE_PREFIX)/apps/test/common/shared


# Protobuf Sources #############################################################

NANOPB_EXTENSION = nanopb

NANOPB_SRCS += $(NANOAPP_PATH)/../proto/chre_cross_validation_wifi.proto
NANOPB_SRCS += $(NANOAPP_PATH)/../proto/chre_test_common.proto
NANOPB_INCLUDES = $(NANOAPP_PATH)/../proto

# Source Code ##################################################################

COMMON_SRCS += $(NANOAPP_PATH)/src/chre_cross_validator_wifi.cc
COMMON_SRCS += $(NANOAPP_PATH)/src/chre_cross_validator_wifi_manager.cc
COMMON_SRCS += $(NANOAPP_PATH)/src/wifi_scan_result.cc
COMMON_SRCS += $(TEST_SHARED_PATH)/src/send_message.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/dynamic_vector_base.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/callbacks.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/wifi.cc

# Compiler Flags ###############################################################

# Defines
COMMON_CFLAGS += -DNANOAPP_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_INFO
COMMON_CFLAGS += -DLOG_TAG=\"[ChreCrossValidatorWifi]\"
COMMON_CFLAGS += -DCHRE_ASSERTIONS_ENABLED

# Includes
COMMON_CFLAGS += -I$(NANOAPP_PATH)/inc
COMMON_CFLAGS += -I$(TEST_SHARED_PATH)/inc

# Permission declarations ######################################################

CHRE_NANOAPP_USES_WIFI = true

# Makefile Includes ############################################################

include $(CHRE_PREFIX)/build/nanoapp/app.mk
