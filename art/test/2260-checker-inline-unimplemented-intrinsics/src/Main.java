/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class Main {
    public static void main(String[] args) {
        $noinline$testDivideUnsignedLong();
    }

    private static void $noinline$testDivideUnsignedLong() {
        assertEquals(0x1234567800000000L, $noinline$divideUnsignedLong(0x1234567800000000L, 0x1L));
        assertEquals(0x1234567887654321L, $noinline$divideUnsignedLong(0x1234567887654321L, 0x1L));
        assertEquals(-0x1234567800000000L,
                     $noinline$divideUnsignedLong(-0x1234567800000000L, 0x1L));
        assertEquals(-0x1234567887654321L,
                     $noinline$divideUnsignedLong(-0x1234567887654321L, 0x1L));

        assertEquals(0x12345678L, $noinline$divideUnsignedLong(0x1234567800000000L, 0x100000000L));
        assertEquals(0x12345678L, $noinline$divideUnsignedLong(0x1234567887654321L, 0x100000000L));
        assertEquals(0x100000000L - 0x12345678L,
                     $noinline$divideUnsignedLong(-0x1234567800000000L, 0x100000000L));
        assertEquals(0x100000000L-0x12345678L - 1L,
                     $noinline$divideUnsignedLong(-0x1234567887654321L, 0x100000000L));
    }

    // LongDivideUnsigned is part of core-oj and we will not inline on host.
    // And it is actually implemented on arm64.

    /// CHECK-START: long Main.$noinline$divideUnsignedLong(long, long) inliner (before)
    /// CHECK:     InvokeStaticOrDirect intrinsic:LongDivideUnsigned

    /// CHECK-START-ARM: long Main.$noinline$divideUnsignedLong(long, long) inliner (after)
    /// CHECK-NOT: InvokeStaticOrDirect intrinsic:LongDivideUnsigned
    private static long $noinline$divideUnsignedLong(long dividend, long divisor) {
        return Long.divideUnsigned(dividend, divisor);
    }

    private static void assertEquals(long expected, long result) {
        if (expected != result) {
            throw new Error("Expected: " + expected + ", found: " + result);
        }
    }
}
