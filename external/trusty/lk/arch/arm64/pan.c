/*
 * Copyright (c) 2022, Google, Inc. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#include <arch/arm64/sregs.h>
#include <arch/arm64.h>
#include <arch/pan.h>
#include <inttypes.h>
#include <lk/init.h>
#include <stdio.h>

static uint8_t arm64_pan_support_level(void) {
    uint64_t v = ARM64_READ_SYSREG(id_aa64mmfr1_el1);
    return ((v >> ID_AA64MMFR1_EL1_PAN_SHIFT) & ID_AA64MMFR1_EL1_PAN_MASK);
}

static void arm64_pan_init(uint level) {
    const uint8_t pan_support = arm64_pan_support_level();

    if (pan_support != ID_AA64MMFR1_EL1_PAN_NOT_SUPPORTED) {
        uint64_t sctlr_el1;

        arm64_enable_pan();

        /* clear SPAN bit in SCTLR_EL1 - exceptions to EL1 set PSTATE.PAN */
        sctlr_el1 = ARM64_READ_SYSREG(SCTLR_EL1);
        sctlr_el1 &= ~(1ull << SCTLR_EL1_SPAN_SHIFT);

        /* set EPAN if PAN3 supported - don't allocate cache for speculative
         * accesses which would generate a Permission fault if not speculative.
         */
        if (pan_support == ID_AA64MMFR1_EL1_PAN3_SUPPORTED) {
            sctlr_el1 |= 1ull << SCTLR_EL1_EPAN_SHIFT;
        }

        ARM64_WRITE_SYSREG(SCTLR_EL1, sctlr_el1);
    }
}

LK_INIT_HOOK_FLAGS(arm64_pan_init,
                   arm64_pan_init,
                   LK_INIT_LEVEL_ARCH,
                   LK_INIT_FLAG_ALL_CPUS)

bool arm64_pan_supported(void) {
    return arm64_pan_support_level() != ID_AA64MMFR1_EL1_PAN_NOT_SUPPORTED;
}

bool arm64_pan_enabled(void) {
    /* Only access PAN sysreg if supported to avoid UNDEFINED */
    return arm64_pan_supported() &&
           ((ARM64_READ_SYSREG(PAN) >> PAN_PAN_SHIFT) & PAN_PAN_MASK) != 0;
}

void arm64_disable_pan(void) {
    if (arm64_pan_supported()) {
        uint64_t pan = ARM64_READ_SYSREG(PAN);
        pan &= ~(1ull << PAN_PAN_SHIFT);
        ARM64_WRITE_SYSREG(PAN, pan);
    }
}

void arm64_enable_pan(void) {
    if (arm64_pan_supported()) {
        uint64_t pan = ARM64_READ_SYSREG(PAN);
        pan |= 1ull << PAN_PAN_SHIFT;
        ARM64_WRITE_SYSREG(PAN, pan);
    }
}
