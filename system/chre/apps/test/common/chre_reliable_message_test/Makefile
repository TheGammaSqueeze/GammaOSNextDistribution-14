#
# CHRE Reliable Message Test Nanoapp Makefile
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

NANOAPP_NAME = chre_reliable_message_test
NANOAPP_ID = 0x476f6f6754000010
NANOAPP_NAME_STRING = \"CHRE\ Reliable\ Message\ Test\"
NANOAPP_VERSION = 0x00000001

NANOAPP_PATH = $(CHRE_PREFIX)/apps/test/common/chre_reliable_message_test
TEST_SHARED_PATH = $(CHRE_PREFIX)/apps/test/common/shared

# Source Code ##################################################################

COMMON_SRCS += $(NANOAPP_PATH)/src/chre_reliable_message_test.cc
COMMON_SRCS += $(NANOAPP_PATH)/src/chre_reliable_message_test_manager.cc

COMMON_SRCS += $(TEST_SHARED_PATH)/src/send_message.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/callbacks.cc

# Protobuf Sources #############################################################

NANOPB_EXTENSION = nanopb
NANOPB_SRCS += $(NANOAPP_PATH)/../proto/chre_test_common.proto
NANOPB_SRCS += $(NANOAPP_PATH)/../proto/chre_reliable_message_test.proto
NANOPB_INCLUDES = $(NANOAPP_PATH)/../proto

# Compiler Flags ###############################################################

# Defines
COMMON_CFLAGS += -DNANOAPP_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_INFO
COMMON_CFLAGS += -DCHRE_ASSERTIONS_ENABLED
COMMON_CFLAGS += -DLOG_TAG=\"[ReliableMessageTest]\"

# Includes
COMMON_CFLAGS += -I$(TEST_SHARED_PATH)/inc
COMMON_CFLAGS += -I$(NANOAPP_PATH)/inc

# Makefile Includes ############################################################

include $(CHRE_PREFIX)/build/nanoapp/app.mk
