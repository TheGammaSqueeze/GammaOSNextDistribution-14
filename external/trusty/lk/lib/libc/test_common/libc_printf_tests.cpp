/*
 * Copyright (c) 2022 LK Trusty Authors. All Rights Reserved.
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

#include <lk/compiler.h>
#include <stdlib.h>

#include "gtest/gtest.h"
#include "printf_test_defs.h"

class PrintfTest : public testing::TestWithParam<bool> {
};

#define BUFFER_SIZE 100
TEST(PrintfTest, SmallIntegerPrintTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "%d", 100);
    EXPECT_STREQ(buffer, "100");
}

TEST(PrintfTest, NullPointerPrintTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer: %p", (void*)0);
    EXPECT_STREQ(buffer, "pointer: 0x0");
}

TEST(PrintfTest, SmallPointerPrintTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer: %p", (void*)0x1000);
    EXPECT_STREQ(buffer, "pointer: 0x1000");
}

TEST(PrintfTest, SmallPseudoNegativePointerPrintTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer: %p", (void*)-4096);
    EXPECT_STREQ(buffer, "pointer: 0xfffffffffffff000");
}


TEST(PrintfTest, PointerAndUnsignedOneLineOneBigOneSmall) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer1: %p number: %u", 0x5000, 100);
    EXPECT_STREQ_COND(buffer, "pointer1: 0x*** number: 100", "pointer1: 0x5000 number: 100", RELEASE_BUILD);
}

TEST(PrintfTest, PointerAndUnsignedOneLineOneBigOneSmallInverse) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer1: %p number: %u", 0x500, 10000);
    EXPECT_STREQ_COND(buffer, "pointer1: 0x500 number: ***", "pointer1: 0x500 number: 10000", RELEASE_BUILD);
}

TEST(PrintfTest, OnePointersTwoIntsOneLineOneSmallTwoBig) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer1: %p number: %u hex: %x", 0x5, 10000, 0X70);
    EXPECT_STREQ_COND(buffer, "pointer1: 0x5 number: *** hex: 70", "pointer1: 0x5 number: 10000 hex: 70", RELEASE_BUILD);
}

TEST(PrintfTest, OnePointersTwoIntsOneLineOneSmallTwoBigInverse) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer1: %p number: %u hex: %x", 0x5000, 10, 0X7000);
    EXPECT_STREQ_COND(buffer, "pointer1: 0x*** number: 10 hex: ***", "pointer1: 0x5000 number: 10 hex: 7000", RELEASE_BUILD);
}

TEST(PrintfTest, BiggerPseudoNegativePointerPrintTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer: %p", (void*)-4097);
    EXPECT_STREQ_COND(buffer, "pointer: 0x***", "pointer: 0xffffffffffffefff", RELEASE_BUILD);
}

TEST(PrintfTest, PointerAndUnsignedOneLine) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer1: %p number: %u", 0x5000, 10000);
    EXPECT_STREQ_COND(buffer, "pointer1: 0x*** number: ***", "pointer1: 0x5000 number: 10000", RELEASE_BUILD);
}

TEST(PrintfTest, SmallestPseudoNegativePointerPrintTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer: %p", (void*)-1);
    EXPECT_STREQ(buffer, "pointer: 0xffffffffffffffff");
}

TEST(PrintfTest, PointerPrintTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer: %p", (void*)0x5000);
    EXPECT_STREQ_COND(buffer, "pointer: 0x***", "pointer: 0x5000", RELEASE_BUILD);
}

TEST(PrintfTest, PointerSprintfTest) {
    char buffer[BUFFER_SIZE];

    sprintf(buffer, "pointer: %p", (void*)0x5000);
    EXPECT_STREQ(buffer, "pointer: 0x5000");
}

TEST(PrintfTest, PointerSnprintfTest) {
    char buffer[BUFFER_SIZE];

    snprintf(buffer, BUFFER_SIZE,"pointer: %p", (void*)0x5000);
    EXPECT_STREQ(buffer, "pointer: 0x5000");
}

TEST(PrintfTest, LargerIntTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "integer: %d", 4097);
    EXPECT_STREQ_COND(buffer, "integer: ***", "integer: 4097", RELEASE_BUILD);
}

TEST(PrintfTest, LargerNegIntTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "integer: %d", -4097);
    EXPECT_STREQ_COND(buffer, "integer: ***", "integer: -4097", RELEASE_BUILD);
}

TEST(PrintfTest, SmallIntTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "integer: %d", 4096);
    EXPECT_STREQ(buffer, "integer: 4096");
}

TEST(PrintfTest, SmallNegIntTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "integer: %d", -4096);
    EXPECT_STREQ(buffer, "integer: -4096");
}

TEST(PrintfTest, LargerUintTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "unsigned integer: %u", 4097);
    EXPECT_STREQ_COND(buffer, "unsigned integer: ***", "unsigned integer: 4097", RELEASE_BUILD);
}

TEST(PrintfTest, LargerHexTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "unsigned integer: 0x%x", 0x1001);
    EXPECT_STREQ_COND(buffer, "unsigned integer: 0x***", "unsigned integer: 0x1001", RELEASE_BUILD);
}

TEST(PrintfTest, PrintfBufferLargeEnough) {
    char buffer[BUFFER_SIZE];
    memset(buffer, 0, BUFFER_SIZE);
    buffer[5] = '@';

    snprintf_filtered(buffer, 5, "%x", 0x3000);
    EXPECT_STREQ_COND(buffer, "***",
                      "3000", RELEASE_BUILD);
    EXPECT_EQ(buffer[5], '@');
}

TEST(PrintfTest, PrintfBufferLargeEnoughForRelease) {
    char buffer[BUFFER_SIZE];
    memset(buffer, 0, BUFFER_SIZE);
    buffer[4] = '@';

    snprintf_filtered(buffer, 4, "%x", 0x3000);
    EXPECT_STREQ_COND(buffer, "***",
                      "300", RELEASE_BUILD);
    EXPECT_EQ(buffer[4], '@');
}

TEST(PrintfTest, PrintfBufferTooSmallForRelease) {
    char buffer[BUFFER_SIZE];
    memset(buffer, 0, BUFFER_SIZE);
    buffer[3] = '@';

    snprintf_filtered(buffer, 3, "%x", 0x3000);
    EXPECT_STREQ_COND(buffer, "**",
                      "30", RELEASE_BUILD);
    EXPECT_EQ(buffer[3], '@');
}

TEST(PrintfTest, SmallHexTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "unsigned integer: 0x%x", 0x1000);
    EXPECT_STREQ(buffer, "unsigned integer: 0x1000");
}

TEST(PrintfTest, PointerUnsignedOneLineFilterOne) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer1: %px number: %u", 0x5000, 10000);
    EXPECT_STREQ_COND(buffer, "pointer1: 0x5000 number: ***", "pointer1: 0x5000 number: 10000", RELEASE_BUILD);
}

TEST(PrintfTest, PointerUnsignedOneLineFilterOneInverse) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer1: %p number: %ux", 0x5000, 10000);
    EXPECT_STREQ_COND(buffer, "pointer1: 0x*** number: 10000", "pointer1: 0x5000 number: 10000", RELEASE_BUILD);
}

TEST(PrintfTest, PointerUnsignedHexOneLineFilterOne) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer1: %px number: %u hex: %xx", 0x5000, 10000, 0X7000);
    EXPECT_STREQ_COND(buffer, "pointer1: 0x5000 number: *** hex: 7000", "pointer1: 0x5000 number: 10000 hex: 7000", RELEASE_BUILD);
}

TEST(PrintfTest, PointerUnsignedHexOneLineFilterOneInverse) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer1: %p number: %ux hex: %x", 0x5000, 10000, 0X7000);
    EXPECT_STREQ_COND(buffer, "pointer1: 0x*** number: 10000 hex: ***", "pointer1: 0x5000 number: 10000 hex: 7000", RELEASE_BUILD);
}

TEST(PrintfTest, ReleaseUnfilteredPointerPrintTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer: %px", (void*)0x5000);
    EXPECT_STREQ(buffer, "pointer: 0x5000");
}

TEST(PrintfTest, ReleaseUnfilteredLargNegIntTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "integer: %dx", -4097);
    EXPECT_STREQ(buffer, "integer: -4097");
}

TEST(PrintfTest, ReleaseUnfilteredLargerHexTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "unsigned integer: 0x%xx", 0x1001);
    EXPECT_STREQ(buffer, "unsigned integer: 0x1001");
}

TEST(PrintfTest, ReleaseUnfilteredLargerUintTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "unsigned integer: %ux", 4097);
    EXPECT_STREQ(buffer, "unsigned integer: 4097");
}

TEST(PrintfTest, ReleaseUnfilteredLargerUintXAtEndTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "unsigned integer: %uxx", 34127);
    EXPECT_STREQ(buffer, "unsigned integer: 34127x");
}

TEST(PrintfTest, ReleaseUnfilteredPrintfBufferLargeEnoughTest) {
    char buffer[BUFFER_SIZE];
    memset(buffer, 0, BUFFER_SIZE);
    buffer[5] = '@';

    snprintf_filtered(buffer, 5, "%xx", 0x3000);
    EXPECT_STREQ(buffer, "3000");
    EXPECT_EQ(buffer[5], '@');
}

TEST(PrintfTest, ReleaseUnfilteredPrintfBufferLargeEnoughForReleaseTest) {
    char buffer[BUFFER_SIZE];
    memset(buffer, 0, BUFFER_SIZE);
    buffer[4] = '@';

    snprintf_filtered(buffer, 4, "%xx", 0x3000);
    EXPECT_STREQ(buffer,"300");
    EXPECT_EQ(buffer[4], '@');
}

TEST(PrintfTest, ReleaseUnfilteredPrintfBufferTooSmallForReleaseTest) {
    char buffer[BUFFER_SIZE];
    memset(buffer, 0, BUFFER_SIZE);
    buffer[3] = '@';

    snprintf_filtered(buffer, 3, "%xx", 0x3000);
    EXPECT_STREQ(buffer, "30");
    EXPECT_EQ(buffer[3], '@');
}

TEST(PrintfTest, ReleaseUnfilteredPrintfStringXPrintsTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "%sx", "hello");
    EXPECT_STREQ(buffer, "hellox");
}

TEST(PrintfTest, ThreeModifierTogetherOneNotFilteredTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "%d%xx%u", 98765, 0x43210, 123456);
    EXPECT_STREQ_COND(buffer, "***43210***", "9876543210123456", RELEASE_BUILD);
}

TEST(PrintfTest, ThreeModifierTogetherOneNotFilteredInverseTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "%dx%x%ux", 98765, 0x43210, 123456);
    EXPECT_STREQ_COND(buffer, "98765***123456", "9876543210123456", RELEASE_BUILD);
}

TEST(PrintfTest, ReleaseUnfilteredThreeModifiersTest) {
    char buffer[BUFFER_SIZE];

    snprintf_filtered(buffer, BUFFER_SIZE, "pointer: %px unsigned: %ux signed: %dx", (void*)0x5000, 7000, 80000);
    EXPECT_STREQ(buffer, "pointer: 0x5000 unsigned: 7000 signed: 80000");
}

TEST(PrintfTest, SnprintfModifierNotUsedTest) {
    char buffer[BUFFER_SIZE];

    snprintf(buffer, BUFFER_SIZE, "hex: %xx pointer: %px unsigned: %ux signed: %dx", 2, (void*) 3, 4, 5);

    EXPECT_STREQ(buffer, "hex: 2x pointer: 0x3x unsigned: 4x signed: 5x");
}
