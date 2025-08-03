/*
 * Copyright (c) 2023, Google, Inc. All rights reserved
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
#include <arch/ops.h>
#include <inttypes.h>
#include <stdbool.h>

bool arch_sve_supported(void) {
    uint64_t v = ARM64_READ_SYSREG(id_aa64pfr0_el1);
    return ((v >> ID_AA64PFR0_EL1_SVE_SHIFT) & ID_AA64PFR0_EL1_SVE_MASK) ==
           ID_AA64PFR0_EL1_SVE_SUPPORTED;
}

uint64_t arch_disable_sve(void) {
    uint64_t v = ARM64_READ_SYSREG(cpacr_el1);
    uint64_t v_tmp = v & (CPACR_EL1_ZEN_SVE_DISABLE << CPACR_EL1_ZEN_SHIFT);
    v_tmp = v_tmp & (CPACR_EL1_FPEN_SVE_DISABLE << CPACR_EL1_FPEN_SHIFT);

    ARM64_WRITE_SYSREG(cpacr_el1, v_tmp);

    return v;
}

uint64_t arch_enable_sve(void) {
    uint64_t v = ARM64_READ_SYSREG(cpacr_el1);

    arch_disable_sve();
    uint64_t v_tmp = v | (CPACR_EL1_ZEN_SVE_ENABLE << CPACR_EL1_ZEN_SHIFT);
    v_tmp = v_tmp | (CPACR_EL1_FPEN_SVE_ENABLE << CPACR_EL1_FPEN_SHIFT);

    ARM64_WRITE_SYSREG(cpacr_el1, v_tmp);

    return v;
}

