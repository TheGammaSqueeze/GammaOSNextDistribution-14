#
# CHRE GNSS Cross Validator Test Nanoapp Makefile
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

NANOAPP_NAME = chre_cross_validator_gnss
NANOAPP_ID = 0x476f6f6754000006
NANOAPP_NAME_STRING = \"CHRE\ Cross\ Validator\ GNSS\"
NANOAPP_VERSION = 0x00000001

NANOAPP_PATH = $(CHRE_PREFIX)/apps/test/common/chre_cross_validator_gnss


# Source Code ##################################################################

COMMON_SRCS += $(NANOAPP_PATH)/src/chre_cross_validator_gnss.cc

# Compiler Flags ###############################################################

# Defines
COMMON_CFLAGS += -DNANOAPP_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_INFO
COMMON_CFLAGS += -DCHRE_ASSERTIONS_ENABLED

# Permission declarations ######################################################

CHRE_NANOAPP_USES_GNSS = true

# Makefile Includes ############################################################

include $(CHRE_PREFIX)/build/nanoapp/app.mk
