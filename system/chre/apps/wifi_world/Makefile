#
# Wifi World Nanoapp Makefile
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

NANOAPP_NAME = wifi_world
NANOAPP_ID = 0x0123456789000006
NANOAPP_VERSION = 0x00000000

NANOAPP_NAME_STRING = \"Wi-Fi\ World\"

# Common Compiler Flags ########################################################

COMMON_CFLAGS += -I.

COMMON_CFLAGS += -DNANOAPP_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_DEBUG
COMMON_CFLAGS += -DLOG_TAG=\"[WifiWorld]\"
COMMON_CFLAGS += -DCHRE_ASSERTIONS_ENABLED

# Common Source Files ##########################################################

COMMON_SRCS += wifi_world.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/wifi.cc

# Permission declarations ######################################################

CHRE_NANOAPP_USES_WIFI = true

# Makefile Includes ############################################################

include $(CHRE_PREFIX)/build/nanoapp/app.mk
