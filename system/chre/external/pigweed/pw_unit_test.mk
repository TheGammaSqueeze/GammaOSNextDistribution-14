#
# Makefile for Pigweed's Unit Test Framework
#
PW_DIR = $(ANDROID_BUILD_TOP)/external/pigweed
PW_UT_DIR = $(PW_DIR)/pw_unit_test

PW_UT_SRCS += $(PW_UT_DIR)/framework.cc

PW_UT_CFLAGS += -I$(PW_UT_DIR)/public
PW_UT_CFLAGS += -I$(PW_UT_DIR)/public_overrides
PW_UT_CFLAGS += -I$(PW_DIR)/pw_polyfill/public
PW_UT_CFLAGS += -I$(PW_DIR)/pw_polyfill/public_overrides
PW_UT_CFLAGS += -I$(PW_DIR)/pw_polyfill/standard_library_public
PW_UT_CFLAGS += -I$(PW_DIR)/pw_preprocessor/public
PW_UT_CFLAGS += -I$(PW_DIR)/pw_span/public
PW_UT_CFLAGS += -I$(PW_DIR)/pw_span/public_overrides
PW_UT_CFLAGS += -I$(PW_DIR)/pw_string/public
