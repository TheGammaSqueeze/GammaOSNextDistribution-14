# Copyright (C) 2019 The Android Open Source Project
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

MODULES += \
	trusty/kernel/app/btitest \
	trusty/kernel/app/busytest \
	trusty/kernel/app/cachetest \
	trusty/kernel/app/consoletest \
	trusty/kernel/app/dpctest \
	trusty/kernel/app/list-ports \
	trusty/kernel/app/memorytest \
	trusty/kernel/app/memorylatencybench \
	trusty/kernel/app/mmutest \
	trusty/kernel/app/pacbench \
	trusty/kernel/app/pactest \
	trusty/kernel/app/smptest \
	trusty/kernel/app/stdcalltest \
	trusty/kernel/app/uirqtest \
	trusty/kernel/app/usercopytest \
	trusty/kernel/app/userscstest \
	trusty/kernel/lib/ktipc/test/srv \
	trusty/kernel/lib/ktipc/test/main \

ifeq ($(LK_LIBC_IMPLEMENTATION),musl)
MODULES += \
	trusty/kernel/lib/libc-trusty/test \
	trusty/kernel/lib/libcxx-trusty/test \
	trusty/user/base/app/device_tree/tests/kernel \

endif

include $(LKROOT)/kerneltests-inc.mk
