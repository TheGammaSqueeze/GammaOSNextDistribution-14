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
        assertEquals(0, $noinline$testRemoveAbsAndReturnConstant());
    }

    // After LSE we know that some values are 0, making the Abs operation redundant.

    /// CHECK-START: int Main.$noinline$testRemoveAbsAndReturnConstant() constant_folding$before_codegen (before)
    /// CHECK:     Abs

    /// CHECK-START: int Main.$noinline$testRemoveAbsAndReturnConstant() constant_folding$before_codegen (after)
    /// CHECK-NOT:  Abs

    // This enables DCE to know the return value at compile time.

    /// CHECK-START: int Main.$noinline$testRemoveAbsAndReturnConstant() dead_code_elimination$before_codegen (before)
    /// CHECK:     <<ReturnPhi:i\d+>> Phi [<<Val1:i\d+>>,<<Val2:i\d+>>]
    /// CHECK:     Return [<<ReturnPhi>>]

    /// CHECK-START: int Main.$noinline$testRemoveAbsAndReturnConstant() dead_code_elimination$before_codegen (after)
    /// CHECK:     <<Const0:i\d+>> IntConstant 0
    /// CHECK:     Return [<<Const0>>]

    private static int $noinline$testRemoveAbsAndReturnConstant() {
        final int ARRAY_SIZE = 10;
        int[] result = new int[ARRAY_SIZE];
        int[] source = new int[ARRAY_SIZE];

        int value = 0;
        for (int i = 0; i < ARRAY_SIZE; ++i) {
            value += Math.abs(source[i]);
            result[i] = value;
        }
        return value;
    }

    public static void assertEquals(int expected, int result) {
        if (expected != result) {
            throw new Error("Expected: " + expected + ", found: " + result);
        }
    }
}
