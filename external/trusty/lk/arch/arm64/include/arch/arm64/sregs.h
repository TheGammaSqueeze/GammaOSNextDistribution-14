/*
 * Copyright (c) 2023 Google Inc. All rights reserved
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

#pragma once

/*
 * PAuth key special registers
 */

#define APIAKeyLo_EL1		s3_0_c2_c1_0
#define APIAKeyHi_EL1		s3_0_c2_c1_1
#define APIBKeyLo_EL1		s3_0_c2_c1_2
#define APIBKeyHi_EL1		s3_0_c2_c1_3
#define APDAKeyLo_EL1		s3_0_c2_c2_0
#define APDAKeyHi_EL1		s3_0_c2_c2_1
#define APDBKeyLo_EL1		s3_0_c2_c2_2
#define APDBKeyHi_EL1		s3_0_c2_c2_3
#define APGAKeyLo_EL1		s3_0_c2_c3_0
#define APGAKeyHi_EL1		s3_0_c2_c3_1

/*
 * CPACR_EL1 - Architectural Feature Access Control Register
 */

#define CPACR_EL1_FPEN_SHIFT 20u
#define CPACR_EL1_FPEN_SVE_ENABLE 0x3
#define CPACR_EL1_FPEN_SVE_DISABLE 0x0

#define CPACR_EL1_ZEN_SHIFT 16u
#define CPACR_EL1_ZEN_SVE_ENABLE 0x3
#define CPACR_EL1_ZEN_SVE_DISABLE 0x0

/*
 * SCTLR_EL1 bits
 */

/* PACIxSP as as valid jump targets for BTI */
#define SCTLR_EL1_BT0 (1llu << 35u)
#define SCTLR_EL1_BT1 (1llu << 36u)

/* PAuth key enabling bits in SCTLR_EL1 */
#define SCTLR_EL1_ENIA (1llu << 31u)
#define SCTLR_EL1_ENIB (1llu << 30u)
#define SCTLR_EL1_ENDA (1llu << 27u)
#define SCTLR_EL1_ENDB (1llu << 13u)

/* PAN */
#define SCTLR_EL1_SPAN_SHIFT 23u    /* SPAN is from FEAT_PAN1 */
#define SCTLR_EL1_EPAN_SHIFT 57u    /* EPAN is from FEAT_PAN3 */

/*
 * ID_AA64ISAR1_EL1 bits - Instruction Set Attribute Register 1
 */

#define ID_AA64ISAR1_EL1_GPI_SHIFT 28u
#define ID_AA64ISAR1_EL1_GPI_MASK 0xfu
#define ID_AA64ISAR1_EL1_GPA_SHIFT 28u
#define ID_AA64ISAR1_EL1_GPA_MASK 0xfu
#define ID_AA64ISAR1_EL1_API_SHIFT 8u
#define ID_AA64ISAR1_EL1_API_MASK 0xfu
#define ID_AA64ISAR1_EL1_APA_SHIFT 4u
#define ID_AA64ISAR1_EL1_APA_MASK 0xfu

/*
 * ID_AA64ISAR2_EL1 bits - Instruction Set Attribute Register 2
 */

#define ID_AA64ISAR2_EL1_PAC_FRAC_SHIFT 24u
#define ID_AA64ISAR2_EL1_PAC_FRAC_MASK 0xfu
#define ID_AA64ISAR2_EL1_APA3_SHIFT 12u
#define ID_AA64ISAR2_EL1_APA3_MASK 0xfu
#define ID_AA64ISAR2_EL1_GPA3_SHIFT 8u
#define ID_AA64ISAR2_EL1_GPA3_MASK 0xf4

/*
 * ID_AA64MMFR1_EL1 - AArch64 Memory Model Feature Register 1
 */

#define ID_AA64MMFR1_EL1_PAN_SHIFT 20u
#define ID_AA64MMFR1_EL1_PAN_MASK 0xfu
#define ID_AA64MMFR1_EL1_PAN_NOT_SUPPORTED 0x0u
#define ID_AA64MMFR1_EL1_PAN_SUPPORTED 0x1u
#define ID_AA64MMFR1_EL1_PAN2_SUPPORTED 0x2u
#define ID_AA64MMFR1_EL1_PAN3_SUPPORTED 0x3u

/*
 * ID_AA64PFR0 - AArch64 Processor Feature Register 0
 */

#define ID_AA64PFR0_EL1_SVE_SHIFT 32u
#define ID_AA64PFR0_EL1_SVE_MASK 0xf
#define ID_AA64PFR0_EL1_SVE_SUPPORTED 0x1u

/*
 * ID_AA64PFR1_EL1 - AArch64 Processor Feature Register 1
 */

#define ID_AA64PFR1_EL1_BTI_SHIFT 0u
#define ID_AA64PFR1_EL1_BIT_MASK 0xfu
#define ID_AA64PFR1_EL1_BTI_NOT_SUPPORTED 0x0u
#define ID_AA64PFR1_EL1_BTI_SUPPORTED 0x1u

/*
 * PAN - Privileged Access Never
 */

#define PAN s3_0_c4_c2_3

#define PAN_PAN_SHIFT 22u
#define PAN_PAN_MASK 0x1ull
