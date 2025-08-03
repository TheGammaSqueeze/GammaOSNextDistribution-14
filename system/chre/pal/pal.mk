#
# PAL Makefile
#

# Common Compiler Flags ########################################################

# Include paths.
COMMON_CFLAGS += -I$(CHRE_PREFIX)/pal/include

# GoogleTest Source Files ######################################################

GOOGLETEST_CFLAGS += -I$(CHRE_PREFIX)/pal/tests/include

GOOGLETEST_SRCS += $(CHRE_PREFIX)/pal/tests/src/version_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/pal/tests/src/wwan_test.cc

GOOGLETEST_PAL_IMPL_SRCS += $(CHRE_PREFIX)/pal/tests/src/audio_pal_impl_test.cc
GOOGLETEST_PAL_IMPL_SRCS += $(CHRE_PREFIX)/pal/tests/src/ble_pal_impl_test.cc
GOOGLETEST_PAL_IMPL_SRCS += $(CHRE_PREFIX)/pal/tests/src/gnss_pal_impl_test.cc
GOOGLETEST_PAL_IMPL_SRCS += $(CHRE_PREFIX)/pal/tests/src/sensor_pal_impl_test.cc
GOOGLETEST_PAL_IMPL_SRCS += $(CHRE_PREFIX)/pal/tests/src/wifi_pal_impl_test.cc
