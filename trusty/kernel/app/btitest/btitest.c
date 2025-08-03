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

/* Tests for ARM64 FEAT_BTI (Branch Target Identification)
 *  This is a mandatory CPU feature at ARM-A v8.5.  Prior to that level, the
 *  BTI instructions are treated as NOPs and there is no enforcement.
 *  The test here will check either case, based on build configuration and
 *  runtime target support.
 * Since this is an ARM64 feature, this should not be built for other arches.
 */
#ifndef ARCH_ARM64
#error BTI is an ARM64 feature
#endif

#include "btitest.h"
#include <arch/ops.h>
#include <err.h>
#include <lib/unittest/unittest.h>
#include <lk/init.h>
#include <stdio.h>

/** Assembly relative function call test of all BTI-relevant calls.
 */
int btitest_bl(void);

/* Assembly indirect call functions, using all BTI-relevant calls.
 *  BR via x16 and x17 is often used by linkers for veneers and has some
 *  additional handling described in the ARM Architecture Reference Manual,
 *  Table D8-36 (IYWFHD).
 * The passed func_idx should be one of the BTITEST_CALLEE_ macros, which covers
 *  the landing pad instruction types given in the ARM Architecture Reference
 *  Manual, Table D8-37 (ICNBPL).
 */
int btitest_blr(int func_idx);
int btitest_br_x16(int func_idx);
int btitest_br_x17(int func_idx);
int btitest_br(int func_idx);

/* Get the expected return code when a BTI access should be trapped */
static int bti_trap_code(void) {
#ifdef KERNEL_BTI_ENABLED
    if (arch_bti_supported()) {
        return ERR_FAULT;
    }
#endif
    /* No BTI, or not enabled - faults are not detected */
    return 0;
}

TEST(btitest, supported) {
    if (!arch_bti_supported()) {
        trusty_unittest_printf("[   INFO   ] BTI is not supported\n");
        GTEST_SKIP();
    }
    EXPECT_EQ(true, arch_bti_supported());
test_abort:;
}

/* Smoke-test the callee functions; they should return 0 when called with BL */
TEST(btitest, smoke) {
    EXPECT_EQ(0, btitest_bl());
}

TEST(btitest, nop) {
    /* Fault on jump or call to non-bti target */
    EXPECT_EQ(bti_trap_code(), btitest_br(BTITEST_CALLEE_NOP));
    EXPECT_EQ(bti_trap_code(), btitest_blr(BTITEST_CALLEE_NOP));
    EXPECT_EQ(bti_trap_code(), btitest_br_x16(BTITEST_CALLEE_NOP));
    EXPECT_EQ(bti_trap_code(), btitest_br_x17(BTITEST_CALLEE_NOP));
}

TEST(btitest, bti) {
    /* Fault on any jump or call to non-target bti */
    EXPECT_EQ(bti_trap_code(), btitest_br(BTITEST_CALLEE_BTI));
    EXPECT_EQ(bti_trap_code(), btitest_blr(BTITEST_CALLEE_BTI));
    EXPECT_EQ(bti_trap_code(), btitest_br_x16(BTITEST_CALLEE_BTI));
    EXPECT_EQ(bti_trap_code(), btitest_br_x17(BTITEST_CALLEE_BTI));
}

TEST(btitest, bti_c) {
    /* Call or branch via x16/17 to a call target is valid */
    EXPECT_EQ(0, btitest_blr(BTITEST_CALLEE_BTI_C));
    EXPECT_EQ(0, btitest_br_x16(BTITEST_CALLEE_BTI_C));
    EXPECT_EQ(0, btitest_br_x17(BTITEST_CALLEE_BTI_C));

    /* Fault on branch to call target */
    EXPECT_EQ(bti_trap_code(), btitest_br(BTITEST_CALLEE_BTI_C));
}

TEST(btitest, bti_j) {
    /* Any branch to jump target is valid */
    EXPECT_EQ(0, btitest_br(BTITEST_CALLEE_BTI_J));
    EXPECT_EQ(0, btitest_br_x16(BTITEST_CALLEE_BTI_J));
    EXPECT_EQ(0, btitest_br_x17(BTITEST_CALLEE_BTI_J));

    /* Fault on call to jump target */
    EXPECT_EQ(bti_trap_code(), btitest_blr(BTITEST_CALLEE_BTI_J));
}

TEST(btitest, bti_jc) {
    /* Either branch type allowed to call and jump target */
    EXPECT_EQ(0, btitest_br(BTITEST_CALLEE_BTI_JC));
    EXPECT_EQ(0, btitest_br_x16(BTITEST_CALLEE_BTI_JC));
    EXPECT_EQ(0, btitest_br_x17(BTITEST_CALLEE_BTI_JC));
    EXPECT_EQ(0, btitest_blr(BTITEST_CALLEE_BTI_JC));
}

TEST(btitest, pacisp) {
    if (!arch_pac_address_supported()) {
        trusty_unittest_printf("[   INFO   ] PAC is not supported\n");
        GTEST_SKIP();
    }

    /* PACIASP is a valid target for all branch types */
    EXPECT_EQ(0, btitest_br(BTITEST_CALLEE_PACIASP));
    EXPECT_EQ(0, btitest_br_x16(BTITEST_CALLEE_PACIASP));
    EXPECT_EQ(0, btitest_br_x17(BTITEST_CALLEE_PACIASP));
    EXPECT_EQ(0, btitest_blr(BTITEST_CALLEE_PACIASP));

    /* PACIBSP is a valid target for all branch types */
    EXPECT_EQ(0, btitest_br(BTITEST_CALLEE_PACIBSP));
    EXPECT_EQ(0, btitest_br_x16(BTITEST_CALLEE_PACIBSP));
    EXPECT_EQ(0, btitest_br_x17(BTITEST_CALLEE_PACIBSP));
    EXPECT_EQ(0, btitest_blr(BTITEST_CALLEE_PACIBSP));
test_abort:;
}

PORT_TEST(btitest, "com.android.kernel.btitest");
