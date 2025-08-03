/*
 * Copyright (C) 2024 The Android Open Source Project
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
        // Test with even/odd input, and even/odd amount of loop iterations.
        assertEquals(-33, $noinline$TwoBitwiseOperations(32, 4));
        assertEquals(1, $noinline$TwoBitwiseOperations(32, 5));
        assertEquals(-31, $noinline$TwoBitwiseOperations(31, 4));
        assertEquals(0, $noinline$TwoBitwiseOperations(31, 5));
    }

    /// CHECK-START-ARM: int Main.$noinline$TwoBitwiseOperations(int, int) instruction_simplifier_arm (after)
    /// CHECK:       BitwiseNegatedRight kind:And
    /// CHECK:       BitwiseNegatedRight kind:Or

    /// CHECK-START-ARM64: int Main.$noinline$TwoBitwiseOperations(int, int) instruction_simplifier_arm64 (after)
    /// CHECK:       BitwiseNegatedRight kind:And
    /// CHECK:       BitwiseNegatedRight kind:Or

    /// CHECK-START-{ARM,ARM64}: int Main.$noinline$TwoBitwiseOperations(int, int) disassembly (after)
    /// CHECK:       BitwiseNegatedRight kind:And
    /// CHECK:       BitwiseNegatedRight kind:Or
    private static int $noinline$TwoBitwiseOperations(int a, int n) {
        int result = 0;
        for (int i = 0; i < n; ++i) {
            if (i % 2 == 0) {
                result = (~a) & 1;
            } else {
                result = (~a) | 1;
            }
        }
        return result;
    }

    public static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new Error("Expected: " + expected + ", found: " + actual);
        }
    }
}
