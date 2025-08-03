/*
 * Copyright (c) 2022, Google Inc. All rights reserved
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

/*
 * Tests for ARM64 FEAT_Puth (Pointer Authentication Codes)
 * This is a CPU feature at ARM-A v8.3
 * Since this is an ARM64 feature, this should not be built for other arches.
 */
#ifndef ARCH_ARM64
#error PAC is an ARM64 feature
#endif

#include "pactest.h"
#include <arch/arm64/sregs.h>
#include <arch/ops.h>
#include <err.h>
#include <lib/unittest/unittest.h>
#include <lk/init.h>
#include <platform/random.h>
#include <stdio.h>

#define MASK(bits) ((1ull << (bits)) - 1)

/*
 * Test addresses for each translation table (TT).
 * Address bit 55 is used to select between page translation tables to use.
 * TT0 is used for user addresses, while TT1 is kernel addresses.
 */
#define PACTEST_TT0_ADDRESS \
    (0x1234567890abcdefu & MASK(MMU_USER_SIZE_SHIFT)) & ~(1ull << 55)
#define PACTEST_TT1_ADDRESS \
    (0x1234567890abcdefu | (~0ull << MMU_KERNEL_SIZE_SHIFT)) | (1ull << 55)
#define PACTEST_MODIFIER 0xfedcba0987654321u

/* Helper function for parameterized calling of specific PAC instructions */
static uint64_t pacxx(bool instr_not_data,
                      bool key_a_not_b,
                      uint64_t address,
                      uint64_t modifier) {
    if (key_a_not_b) {
        if (instr_not_data) {
            __asm__(".arch_extension pauth\n"
                    "\tPACIA %0, %1"
                    : "+r"(address)
                    : "r"(modifier));
        } else {
            __asm__(".arch_extension pauth\n"
                    "\tPACDA %0, %1"
                    : "+r"(address)
                    : "r"(modifier));
        }
    } else {
        if (instr_not_data) {
            __asm__(".arch_extension pauth\n"
                    "\tPACIB %0, %1"
                    : "+r"(address)
                    : "r"(modifier));
        } else {
            __asm__(".arch_extension pauth\n"
                    "\tPACDB %0, %1"
                    : "+r"(address)
                    : "r"(modifier));
        }
    }

    return address;
}

/*
 * Helper function for parameterized calling of specific PAC instructions.
 * The instructions are implemented in assembly as they may generate exceptions
 * (FEAT_FPAC) which need catching.
 */
static int autxx(bool instr_not_data,
                 bool key_a_not_b,
                 uint64_t address,
                 uint64_t modifier,
                 uint64_t* result) {
    if (key_a_not_b) {
        if (instr_not_data) {
            return pactest_autia(address, modifier, result);
        } else {
            return pactest_autda(address, modifier, result);
        }
    } else {
        if (instr_not_data) {
            return pactest_autib(address, modifier, result);
        } else {
            return pactest_autdb(address, modifier, result);
        }
    }
}

static uint8_t get_nibble(uint64_t reg, uint8_t shift) {
    return (reg >> shift) & 0xf;
}

static const char* get_pac_features(uint8_t nibble) {
    switch (nibble) {
    case 0b0001:
        return "PAuth";
    case 0b0010:
        return "PAuth + EPAC";
    case 0b0011:
        return "PAuth + PAuth2";
    case 0b0100:
        return "Pauth + Pauth2 + FPAC";
    case 0b0101:
        return "Pauth + Pauth2 + FPAC + FPACCOMBINE";
    }

    return "<unknown>";
}

TEST(pactest, pauth_supported) {
    if (!arch_pac_address_supported()) {
        trusty_unittest_printf("[   INFO   ] PAuth is not supported\n");
        GTEST_SKIP();
    }

    const uint64_t isar1 = ARM64_READ_SYSREG(id_aa64isar1_el1);
    const uint64_t isar2 = ARM64_READ_SYSREG(id_aa64isar2_el1);
    const uint8_t pacfrac = get_nibble(isar2, ID_AA64ISAR2_EL1_PAC_FRAC_SHIFT);
    const uint8_t apa3 = get_nibble(isar2, ID_AA64ISAR2_EL1_APA3_SHIFT);
    const uint8_t apa = get_nibble(isar1, ID_AA64ISAR1_EL1_APA_SHIFT);
    const uint8_t api = get_nibble(isar1, ID_AA64ISAR1_EL1_API_SHIFT);
    const char *algo = "none", *features = "", *cpf = "";

    if (apa3) {
        algo = "QARMA3";
        features = get_pac_features(apa3);
        EXPECT_EQ(apa, 0);
        EXPECT_EQ(api, 0);
    } else if (apa) {
        algo = "QARMA5";
        features = get_pac_features(apa);
        EXPECT_EQ(api, 0);
    } else if (api) {
        algo = "implementation defined";
        features = get_pac_features(api);
    }

    if (pacfrac == 1) {
        cpf = ", CONSTPACFIELD";
    } else {
        EXPECT_EQ(pacfrac, 0);
    }

    /* Log the support in case later trying to debug a test */
    trusty_unittest_printf("[   INFO   ] algo: %s\n", algo);
    trusty_unittest_printf("[   INFO   ] feat: %s%s\n", features, cpf);
test_abort:;
}

TEST(pactest, fpac_supported) {
    uint64_t val;
    int rc;

    if (!arch_pac_exception_supported()) {
        trusty_unittest_printf("[   INFO   ] FPAC is not supported\n");
        GTEST_SKIP();
    }

    rc = pactest_autia(PACTEST_TT0_ADDRESS, PACTEST_MODIFIER, &val);
    EXPECT_EQ(rc, ERR_FAULT);
test_abort:;
}

TEST(pactest, enabled) {
    const uint64_t sctlr_el1 = ARM64_READ_SYSREG(SCTLR_EL1);

    /* Check the expected keys are enabled */
    EXPECT_EQ(sctlr_el1 & SCTLR_EL1_ENIA,
              arch_pac_address_supported() ? SCTLR_EL1_ENIA : 0);
    EXPECT_EQ(sctlr_el1 & SCTLR_EL1_ENIB, 0);
    EXPECT_EQ(sctlr_el1 & SCTLR_EL1_ENDA, 0);
    EXPECT_EQ(sctlr_el1 & SCTLR_EL1_ENDB, 0);
}

TEST(pactest, keys) {
    if (!arch_pac_address_supported()) {
        GTEST_SKIP();
    }

    const struct packeys* thread_keys = &get_current_thread()->arch.packeys;
    const uint64_t keyi_lo = ARM64_READ_SYSREG(APIAKeyLo_EL1);
    const uint64_t keyi_hi = ARM64_READ_SYSREG(APIAKeyHi_EL1);

    EXPECT_EQ(thread_keys->apia[0], keyi_lo);
    EXPECT_EQ(thread_keys->apia[1], keyi_hi);

    /*
     * Check the keys are neither all 0's of all 1's.
     * While these values are valid, it may indicate incorrect initialisation.
     */
    EXPECT_NE(UINT64_MAX, keyi_lo);
    EXPECT_NE(UINT64_MAX, keyi_hi);
    EXPECT_NE(0, keyi_lo);
    EXPECT_NE(0, keyi_hi);
test_abort:;
}

typedef struct {
    bool translation_table;
    bool instr_not_data;
    bool key_a_not_b;
    bool key_enabled;
} pactest_t;

static void get_params(pactest_t* p) {
    const bool* const* params = GetParam();
    p->translation_table = *params[0];
    /* Invert for more logical test ordering: AI, AD, BI, BD */
    p->instr_not_data = !*params[1];
    p->key_a_not_b = !*params[2];
}

TEST_F_SETUP(pactest) {
    uint64_t key_enabled_bit;

    get_params(_state);

    if (_state->instr_not_data) {
        key_enabled_bit = _state->key_a_not_b ? SCTLR_EL1_ENIA : SCTLR_EL1_ENIB;
    } else {
        key_enabled_bit = _state->key_a_not_b ? SCTLR_EL1_ENDA : SCTLR_EL1_ENDB;
    }

    _state->key_enabled = ARM64_READ_SYSREG(SCTLR_EL1) & key_enabled_bit;
}

TEST_F_TEARDOWN(pactest) {}

static void user_param_to_string(const void* param,
                                 char* buf,
                                 size_t buf_size) {
    pactest_t p;
    get_params(&p);

    snprintf(buf, buf_size, "TT%u/%s%s", p.translation_table ? 1 : 0,
             p.instr_not_data ? "PACI" : "PACD", p.key_a_not_b ? "A" : "B");
}

INSTANTIATE_TEST_SUITE_P(pac,
                         pactest,
                         testing_Combine(testing_Bool(),
                                         testing_Bool(),
                                         testing_Bool()),
                         user_param_to_string);

TEST_P(pactest, instr) {
    if (!arch_pac_address_supported()) {
        GTEST_SKIP();
    }

    const uint64_t test_address = _state->translation_table
                                          ? PACTEST_TT1_ADDRESS
                                          : PACTEST_TT0_ADDRESS;
    uint64_t address = test_address;
    int rc;

    if (_state->key_enabled) {
        /* Test instruction adds a PAC */
        address = pacxx(_state->instr_not_data, _state->key_a_not_b, address,
                        PACTEST_MODIFIER);

        /* Address should have been modified to include PAC */
        EXPECT_NE(test_address, address);

        uint64_t pac_address = address;

        /* Check AUT returns the original pointer */
        rc = autxx(_state->instr_not_data, _state->key_a_not_b, address,
                   PACTEST_MODIFIER, &address);

        EXPECT_EQ(rc, 0)
        EXPECT_EQ(test_address, address);

        /* Check the pointer is invalidated when the modifier is changed */
        rc = autxx(_state->instr_not_data, _state->key_a_not_b, pac_address,
                   ~PACTEST_MODIFIER, &address);
        if (arch_pac_exception_supported()) {
            EXPECT_EQ(rc, ERR_FAULT);
        } else {
            /* Address should have been invalidated */
            EXPECT_EQ(rc, 0);
            EXPECT_NE(address, test_address);
        }
    } else { /* Key disabled */

        address = pacxx(_state->instr_not_data, _state->key_a_not_b, address,
                        PACTEST_MODIFIER);
        EXPECT_EQ(test_address, address);

        rc = autxx(_state->instr_not_data, _state->key_a_not_b, address,
                   PACTEST_MODIFIER, &address);
        EXPECT_EQ(rc, 0)
        EXPECT_EQ(test_address, address);
    }
test_abort:;
}

TEST_P(pactest, pac_length) {
    if (!arch_pac_address_supported()) {
        GTEST_SKIP();
    }

    uint8_t top = 0, bot = 64;

    /*
     * Probe a number of times in order to ensure we find the top and bottom
     * bits used.  Odds of missing correct bounds are about P=(1/2)^32.
     */
    for (uint16_t t = 0; t < 32; t++) {
        uint64_t val, orig;

        /* Get 64-bit random value */
        platform_random_get_bytes((void*)&orig, sizeof(orig));

        /*
         * Select which of T0SZ or T1SZ we should probe.
         * Address bit 55 is used to select between page translation tables
         * to use (e.g. TTBRx and TxSZ, where x is 0 or 1).
         */
        val = orig & ~(1ull << 55);
        if (_state->translation_table) {
            val |= 1ull << 55;
        }

        /* Call specific instruction variant */
        val = pacxx(_state->instr_not_data, _state->key_a_not_b, val, 0);

        /* Remove un-changed bits and clear bit 55 */
        val ^= orig;
        val &= ~(1ull << 55);

        /* Find highest and lowest set bit positions */
        if (val) {
            top = MAX(top, 63 - __builtin_clzll(val));
            bot = MIN(bot, __builtin_ctzll(val));
        }
    }

    if (_state->key_enabled) {
        /* If this is not true, the PAC key not be functioning */
        ASSERT_GT(top, bot);

        /* Count bit range, except bit 55 if it is in the range */
        int bits = (top + 1) - bot;
        if (bot < 55 && top > 55) {
            bits--;
        }

        trusty_unittest_printf("[   INFO   ] PAC bits %" PRIu8 ":%" PRIu8
                               " = %d effective bits\n",
                               top, bot, bits);
    } else {
        trusty_unittest_printf("[   INFO   ] PAC key disabled\n");

        ASSERT_EQ(top, 0);
        ASSERT_EQ(bot, 64);
    }

test_abort:;
}

PORT_TEST(pactest, "com.android.kernel.pactest");
