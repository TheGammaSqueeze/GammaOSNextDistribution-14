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

#define TLOG_TAG "pac"

#include <arch/ops.h>
#include <stdbool.h>
#include <stdint.h>

/*
 * FEAT_PAuth is mandatory at ARM-A v8.3.
 * FEAT_FPAC is mandatory at ARM-A v8.6.
 * Assume present at v9 (and above), otherwise probe ID registers.
 */
#if __ARM_ARCH >= 9
bool arch_pac_address_supported(void) {
    return true;
}

bool arch_pac_exception_supported(void) {
    return true;
}
#else

#include <arch/arm64/sregs.h>
#include <arch/arm64.h>

static uint8_t get_nibble(uint64_t reg, uint8_t shift) {
    return (reg >> shift) & 0xf;
}

bool arch_pac_address_supported(void) {
    const uint64_t isar1 = ARM64_READ_SYSREG(id_aa64isar1_el1);
    const uint64_t isar2 = ARM64_READ_SYSREG(id_aa64isar2_el1);

    /* Address & Data authentication (APIxKey_EL1, APDxKey_EL1)*/
    return get_nibble(isar1, ID_AA64ISAR1_EL1_APA_SHIFT) != 0 ||
           get_nibble(isar1, ID_AA64ISAR1_EL1_API_SHIFT) != 0 ||
           get_nibble(isar2, ID_AA64ISAR2_EL1_APA3_SHIFT) != 0;
}

bool arch_pac_exception_supported(void) {

    /*
     * Check each of the sysregs.
     * Only one register should be populated, and gives which algorithm is
     * implemented.  We check bit 0x4 in the field for FPAC or FPACCOMBINE.
     */
    const uint64_t isar1 = ARM64_READ_SYSREG(id_aa64isar1_el1);
    const uint8_t apa = get_nibble(isar1, ID_AA64ISAR1_EL1_APA_SHIFT);
    if (apa) {
        return (apa & 0x4) != 0;
    }

    const uint8_t api = get_nibble(isar1, ID_AA64ISAR1_EL1_API_SHIFT);
    if (api) {
        return (api & 0x4) != 0;
    }

    const uint64_t isar2 = ARM64_READ_SYSREG(id_aa64isar2_el1);
    const uint8_t apa3 = get_nibble(isar2, ID_AA64ISAR2_EL1_APA3_SHIFT);
    if (apa3) {
        return (apa3 & 0x4) != 0;
    }

    return false;
}
#endif
