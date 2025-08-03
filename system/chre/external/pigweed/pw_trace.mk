#
# Makefile for Pigweed's trace module
#

# Environment Checks
ifeq ($(ANDROID_BUILD_TOP),)
$(error "You should supply an ANDROID_BUILD_TOP environment variable \
         containing a path to the Android source tree. This is typically \
         provided by initializing the Android build environment.")
endif

# Location of Pigweed
PIGWEED_DIR = $(ANDROID_BUILD_TOP)/external/pigweed

# Pigweed include paths
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_trace/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_preprocessor/public