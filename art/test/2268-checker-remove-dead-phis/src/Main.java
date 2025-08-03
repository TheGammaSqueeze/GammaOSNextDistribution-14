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
    public static void main(String[] args) {}

    /// CHECK-START: int Main.$noinline$doNothing() dead_code_elimination$after_inlining (before)
    /// CHECK-DAG: Phi
    /// CHECK-DAG: Add
    /// CHECK-DAG: Div

    /// CHECK-START: int Main.$noinline$doNothing() dead_code_elimination$after_inlining (after)
    /// CHECK-NOT: Phi
    /// CHECK-NOT: Add
    /// CHECK-NOT: Div
    private int $noinline$doNothing() {
        int val1 = b1 ? 1 : 0;
        int val2 = b2 ? 1 : 0;

        if (condition1) {
            val1 += 25;
            val2 += 25;
        }

        if (condition2) {
            val1 /= 5;
            val2 /= 5;
        }

        if ($inline$returnFalse()) {
            return val1 + val2;
        } else {
            return 0;
        }
    }

    private boolean $inline$returnFalse() {
        return false;
    }

    boolean b1;
    boolean b2;
    boolean condition1;
    boolean condition2;
}
