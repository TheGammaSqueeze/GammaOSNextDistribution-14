#
# Test Makefile
#

# TODO (b/175919480) - General improvements to make adding tests easier
# - How best to configure it at build time? For example, is it sufficient
#   to supply an env var when invoking make, or would a different build
#   target be useful (similar to how we have *_debug targets)?
# - How can we add tests for one specific platform without impacting a
#   different platform where it doesn't apply (and may not compile)?
# - Can we also loop in tests for common code, which are currently
#   executed off-target via ./run_tests.sh (those added via GOOGLETEST_SRCS,
#   e.g. core/tests/*, util/tests/*)

ifeq ($(CHRE_ON_DEVICE_TESTS_ENABLED),true)
include $(CHRE_PREFIX)/external/pigweed/pw_unit_test.mk
COMMON_CFLAGS += $(PW_UT_CFLAGS)
COMMON_CFLAGS += -I$(CHRE_PREFIX)/test/common/include
COMMON_SRCS += $(CHRE_PREFIX)/test/common/run_tests.cc

COMMON_CFLAGS += -DCHRE_ON_DEVICE_TESTS_ENABLED

COMMON_SRCS += $(PW_UT_SRCS)

# Core unit tests
COMMON_SRCS += $(CHRE_PREFIX)/core/tests/audio_util_test.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/tests/memory_manager_test.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/tests/request_multiplexer_test.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/tests/sensor_request_test.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/tests/wifi_scan_request_test.cc

# CHPP tests
COMMON_SRCS += $(CHRE_PREFIX)/chpp/test/wifi_convert_test.cpp
COMMON_SRCS += $(CHRE_PREFIX)/chpp/test/wwan_convert_test.cpp

# PAL unit tests
COMMON_CFLAGS += -I$(CHRE_PREFIX)/pal/tests/include
COMMON_CFLAGS += -I$(CHRE_PREFIX)/pal/util/include

COMMON_SRCS += $(CHRE_PREFIX)/pal/tests/src/version_test.cc
COMMON_SRCS += $(CHRE_PREFIX)/pal/tests/src/wwan_test.cc
COMMON_SRCS += $(CHRE_PREFIX)/pal/util/wifi_pal_convert.c
COMMON_SRCS += $(CHRE_PREFIX)/pal/util/tests/wifi_pal_convert_test.cc

endif
