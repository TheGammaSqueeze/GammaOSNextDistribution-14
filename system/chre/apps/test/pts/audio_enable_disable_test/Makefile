#
# PTS Basic Audio Enable/Disable Test Nanoapp Makefile
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

NANOAPP_NAME = pts_audio_enable_disable_test
NANOAPP_ID = 0x476f6f6754100000
NANOAPP_NAME_STRING = \"Pts\ Audio\ Enable\ Disable\ Test\"
NANOAPP_VERSION = 0x00000002

NANOAPP_PATH = $(CHRE_PREFIX)/apps/test/pts/audio_enable_disable_test


# Protobuf Sources #############################################################

NANOPB_EXTENSION = nanopb

NANOPB_SRCS += $(NANOAPP_PATH)/../proto/pts_chre.proto
NANOPB_INCLUDES = $(NANOAPP_PATH)/../proto

# Source Code ##################################################################

COMMON_SRCS += $(NANOAPP_PATH)/src/audio_enable_disable_test.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/callbacks.cc

# Compiler Flags ###############################################################

# Defines
COMMON_CFLAGS += -DNANOAPP_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_INFO

# Permission declarations ######################################################

CHRE_NANOAPP_USES_AUDIO = true

# Makefile Includes ############################################################

include $(CHRE_PREFIX)/build/nanoapp/app.mk
