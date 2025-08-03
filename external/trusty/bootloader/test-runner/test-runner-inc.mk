#
# Copyright (c) 2018, Google, Inc. All rights reserved
#
# Permission is hereby granted, free of charge, to any person obtaining
# a copy of this software and associated documentation files
# (the "Software"), to deal in the Software without restriction,
# including without limitation the rights to use, copy, modify, merge,
# publish, distribute, sublicense, and/or sell copies of the Software,
# and to permit persons to whom the Software is furnished to do so,
# subject to the following conditions:
#
# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
# IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
# CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
# TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
# SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
#

TEST_RUNNER_DIR := $(GET_LOCAL_DIR)

TRUSTY_TEST_RUNNER_SAVED_ARCH = $(ARCH)
ifneq ($(strip $(TEST_RUNNER_ARCH)),)
ARCH := $(TEST_RUNNER_ARCH)
endif

TRUSTY_APP_MEMBASE := 0x60000000

# b/254541384, do not link with libclang_rt.builtins-arm-android.a
TRUSTY_APP_LIBGCC :=
TRUSTY_APP_LIBCOV :=

$(eval $(call standard_name_for_arch,STANDARD_ARCH_NAME,$(ARCH),$(SUBARCH)))
include arch/$(ARCH)/toolchain.mk

# The lk module.mk file uses this variable to determine if it should add
# kernel-specific flags to the build.
USER_TASK_MODULE := true

$(eval $(call trusty-build-rule,$(TEST_RUNNER_DIR)))

TRUSTY_APP_MEMBASE :=

ARCH := $(TRUSTY_TEST_RUNNER_SAVED_ARCH)
TRUSTY_TEST_RUNNER_SAVED_ARCH :=
USER_TASK_MODULE :=
