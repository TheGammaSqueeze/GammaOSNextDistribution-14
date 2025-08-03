#
# Power Test Makefile
#

# Environment Checks ###########################################################

ifeq ($(CHRE_PREFIX),)
ifneq ($(ANDROID_BUILD_TOP),)
CHRE_PREFIX = $(ANDROID_BUILD_TOP)/system/chre
else
$(error "You must run 'lunch' to setup ANDROID_BUILD_TOP, or explicitly define \
         the CHRE_PREFIX environment variable to point to the CHRE root \
         directory.")
endif
endif

# Nanoapp Configuration ########################################################

NANOAPP_VERSION = 0x00020000

# Common Compiler Flags ########################################################

# Include paths.
COMMON_CFLAGS += -I$(CHRE_PREFIX)/apps/power_test/common/include
COMMON_CFLAGS += -I$(CHRE_PREFIX)/external/flatbuffers/include
COMMON_CFLAGS += -I$(CHRE_PREFIX)/util/include

# Defines
COMMON_CFLAGS += -DNANOAPP_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_DEBUG
COMMON_CFLAGS += -DCHRE_ASSERTIONS_ENABLED

# Flatbuffers configuration
include $(CHRE_PREFIX)/external/flatbuffers/flatbuffers.mk
COMMON_CFLAGS += $(FLATBUFFERS_CFLAGS)

# Common Source Files ##########################################################

COMMON_SRCS += $(CHRE_PREFIX)/apps/power_test/common/power_test.cc
COMMON_SRCS += $(CHRE_PREFIX)/apps/power_test/common/request_manager.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/dynamic_vector_base.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/audio.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/callbacks.cc

# Permission declarations ######################################################

CHRE_NANOAPP_USES_AUDIO = true
CHRE_NANOAPP_USES_GNSS = true
CHRE_NANOAPP_USES_WIFI = true
CHRE_NANOAPP_USES_WWAN = true

# Makefile Includes ############################################################

include $(CHRE_PREFIX)/build/nanoapp/app.mk
