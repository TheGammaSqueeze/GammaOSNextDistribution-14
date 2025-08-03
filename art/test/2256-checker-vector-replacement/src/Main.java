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
        $noinline$testVectorAndNonVector();
    }

    // Before loop optimization we only had an array get. After it, we optimized to also have
    // VecLoad operations. This happens consistently only for Arm64. Arm32 vectorizes consistently
    // but it also removes the ArrayGet. X86/X86_64 doesn't vectorize consistently (other
    // vectorization tests also ignore x86/x86_64).

    /// CHECK-START-ARM64: void Main.$noinline$testVectorAndNonVector() loop_optimization (before)
    /// CHECK:     ArrayGet

    /// CHECK-START-ARM64: void Main.$noinline$testVectorAndNonVector() loop_optimization (after)
    /// CHECK:     ArrayGet

    /// CHECK-START-ARM64: void Main.$noinline$testVectorAndNonVector() loop_optimization (before)
    /// CHECK-NOT: VecLoad

    /// CHECK-START-ARM64: void Main.$noinline$testVectorAndNonVector() loop_optimization (after)
    /// CHECK:     VecLoad

    // In LoadStoreElimination both ArrayGet and VecLoad have the same heap location. We will try to
    // replace the ArrayGet with the constant 0. The crash happens when we want to do the same with
    // the vector operation, changing the vector operation to a scalar.

    /// CHECK-START-ARM64: void Main.$noinline$testVectorAndNonVector() load_store_elimination (before)
    /// CHECK-DAG:     VecLoad outer_loop:<<VecBlock:B\d+>>
    /// CHECK-DAG:     ArrayGet outer_loop:<<ScalarBlock:B\d+>>
    /// CHECK-EVAL:    "<<VecBlock>>" == "<<ScalarBlock>>"

    private static void $noinline$testVectorAndNonVector() {
        int[] result = new int[2];
        int[] source = new int[12];

        // This will get vectorized.
        for (int i = 0; i < result.length; ++i) {
            int value = 0;
            // Always true but needed to repro a crash since we need Phis.
            if (i + 10 < source.length) {
                for (int j = 0; j < 10; j++) {
                    value += Math.abs(source[i + j]);
                }
            }
            result[i] = value;
        }
    }
}
