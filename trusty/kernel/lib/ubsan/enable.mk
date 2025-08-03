#
# Copyright (c) 2019, Google, Inc. All rights reserved
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

# Including this file in your project will enable UBSan.
#
# Modules other than the kernel wishing to use UBSan must link in this
# runtime by adding it to MODULE_DEPS, conditional on the UBSAN_ENABLED
# variable (which sindicates whether UBSan is on for the build).
#
# Userspace apps do not need to worry about this as this runtime is already
# being pulled in by libc when needed.
#
# Modules wishing to opt out of UBSan can do so by adding
# the contents of UBSAN_DISABLE to their MODULE_CFLAGS/MODULE_CPPFLAGS or by
# adding to trusty/kernel/lib/ubsan/exemptlist.
#
# Example reasons to do this include:
# * Contexts which cannot easily support the ubsan runtime (e.g. test-runner)
# * External code that is comparatively difficult to change (e.g. boringssl)
# * Code which is highly sensitive to modification (e.g. crypto or performance
#   code) and is already thoroughly tested.
#
# If the code is trusty-owned, please consider either making the code UBSan
# clean or using an __attribute__ decorator on a limited function with an
# appropriate comment explaining why rather than disabling UBSan.
#
# The syntax for suppression is
# __attribute__((no_sanitize("specific-sanitizer")))
#
# Please *DO NOT* use __attribute__((no_sanitize("undefined"))), as which
# sanitizers it disables may expand with compiler revisions and makes it
# harder for a reader to figure out which sanitizer is expected to generate
# a false-positive in that code.

UBSAN_SANITIZERS ?= \
    alignment \
    bool \
    builtin \
    bounds \
    enum \
    float-cast-overflow \
    float-divide-by-zero \
    implicit-unsigned-integer-truncation \
    implicit-signed-integer-truncation \
    implicit-integer-sign-change \
    integer-divide-by-zero \
    pointer-overflow \
    return \
    shift \
    signed-integer-overflow \
    unreachable \
    unsigned-integer-overflow \
    vla-bound \

# object-size only works at higher than -O0 and so is not enabled
#
# non-null sanitizers are not enabled because we are not using the annotations
#
# C++ sanitizers requiring full language features (e.g. RTTI or stdlib) are
# not enabled

UBSAN_ENABLE := \
    $(foreach san,$(UBSAN_SANITIZERS),-fsanitize=$(san)) \
    -fsanitize-blacklist=trusty/kernel/lib/ubsan/exemptlist \

UBSAN_DISABLE := \
    $(foreach san,$(UBSAN_SANITIZERS),-fno-sanitize=$(san))

GLOBAL_SHARED_COMPILEFLAGS += $(UBSAN_ENABLE) -DUBSAN_ENABLED

MODULES += trusty/kernel/lib/ubsan
UBSAN_ENABLED := true
