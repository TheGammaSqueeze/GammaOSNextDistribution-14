LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

GTEST_DIR := external/googletest/googletest

MODULE_LICENSES += $(GTEST_DIR)/LICENSE

# Export gtest headers.
MODULE_EXPORT_INCLUDES += $(GTEST_DIR)/include

# gtest has internal includes relative to its root directory.
MODULE_INCLUDES += $(GTEST_DIR)

# Disable optional features.
MODULE_COMPILEFLAGS += \
	-DGTEST_HAS_CLONE=0 \
	-DGTEST_HAS_EXCEPTIONS=0 \
	-DGTEST_HAS_POSIX_RE=0 \
	-DGTEST_HAS_PTHREAD=0 \
	-DGTEST_HAS_RTTI=0 \
	-DGTEST_HAS_STD_WSTRING=0 \
	-DGTEST_HAS_SEH=0 \
	-DGTEST_HAS_STREAM_REDIRECTION=0 \
	-DGTEST_LINKED_AS_SHARED_LIBRARY=0 \
	-DGTEST_CREATE_SHARED_LIBRARY=0 \

# After disabling a bunch of features, there are dead constants.
MODULE_COMPILEFLAGS += -Wno-unused-const-variable

# Disable the C unittest macros
MODULE_EXPORT_COMPILEFLAGS += -DDISABLE_TRUSTY_UNITTEST_MACROS=1

# Explicitly list the files instead of using gtest-all.cc so the build can be
# parallelized. Note we need to build all the files because of how command line
# flags are handled. For example, we don't support death tests, but still need
# to compile gtest-death-test.cc because gtest.cc references
# GTEST_FLAG(death_test_style).
MODULE_SRCS := \
	$(GTEST_DIR)/src/gtest.cc \
	$(GTEST_DIR)/src/gtest-death-test.cc \
	$(GTEST_DIR)/src/gtest-filepath.cc \
	$(GTEST_DIR)/src/gtest-matchers.cc \
	$(GTEST_DIR)/src/gtest-port.cc \
	$(GTEST_DIR)/src/gtest-printers.cc \
	$(GTEST_DIR)/src/gtest-test-part.cc \
	$(GTEST_DIR)/src/gtest-typed-test.cc \

# aosp/2765314 updated googletest to a more recent version.
# The update brought two significant changes:
# * A new file called gtest-assertion-result.cc, and
# * Changes to GTEST_HAS_DEATH_TEST where it's checked using #ifdef;
#   This means that instead of defining it to 0,
#   we now need to not define it at all.
ifneq ($(wildcard $(GTEST_DIR)/src/gtest-assertion-result.cc),)
	MODULE_SRCS += $(GTEST_DIR)/src/gtest-assertion-result.cc
else
	# Define the macro the old way to get past presubmit
	MODULE_COMPILEFLAGS += -DGTEST_HAS_DEATH_TEST=0
endif

MODULE_EXPORT_INCLUDES += \
	$(LOCAL_DIR)/include \

MODULE_LIBRARY_DEPS += \
        trusty/user/base/lib/libstdc++-trusty \
        trusty/user/base/lib/libcxxabi-trusty \
        trusty/user/base/lib/unittest \

include make/library.mk
