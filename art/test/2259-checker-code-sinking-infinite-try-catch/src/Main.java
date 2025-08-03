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
    public static void main(String[] args) throws Exception {
        try {
            $noinline$testInfiniteLoopUnlessItThrows();
            throw new Exception("Unreachable");
        } catch (Error expected) {
        }
    }

    /// CHECK-START: int Main.$noinline$testInfiniteLoopUnlessItThrows() code_sinking (before)
    /// CHECK-NOT: Add loop:none

    /// CHECK-START: int Main.$noinline$testInfiniteLoopUnlessItThrows() code_sinking (before)
    /// CHECK-DAG:   <<Const0:i\d+>> IntConstant 0
    /// CHECK-DAG:   <<Add:i\d+>> Add loop:<<Loop:B\d+>> outer_loop:none
    /// CHECK-DAG:   Phi [<<Const0>>,<<Add>>] loop:<<Loop>> outer_loop:none

    /// CHECK-START: int Main.$noinline$testInfiniteLoopUnlessItThrows() code_sinking (after)
    /// CHECK-NOT: Add loop:none

    /// CHECK-START: int Main.$noinline$testInfiniteLoopUnlessItThrows() code_sinking (after)
    /// CHECK-DAG:   <<Const0:i\d+>> IntConstant 0
    /// CHECK-DAG:   <<Add:i\d+>> Add loop:<<Loop:B\d+>> outer_loop:none
    /// CHECK-DAG:   Phi [<<Const0>>,<<Add>>] loop:<<Loop>> outer_loop:none
    private static int $noinline$testInfiniteLoopUnlessItThrows() {
        int a = 0;
        while (true) {
            try {
                $noinline$throwOrReturn(a);
                throw new Exception();
            } catch (Exception e) {
                a++;
            }
        }
    }

    // Throws Error if `input` is 10. Otherwise it returns `input`.
    private static int $noinline$throwOrReturn(int input) throws Error {
        if (input == 10) {
            throw new Error();
        }
        return input;
    }
}
