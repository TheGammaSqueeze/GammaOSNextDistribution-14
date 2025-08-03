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
        $noinline$assertEquals(0, $noinline$testRemCaller());
    }

    public static void $noinline$assertEquals(int expected, int result) {
        if (expected != result) {
            throw new Error("Expected: " + expected + ", found: " + result);
        }
    }

    private static int $noinline$testRemCaller() {
        return $inline$remMethod(50);
    }

    private static int $inline$remMethod(int param) {
        // We were replacing this Rem with the div below when both the dividend and the
        // divisor were the same. We shouldn't do that since we didn't find a Div(50, 50).
        int result = param % 50;
        return result / 50;
    }
}
