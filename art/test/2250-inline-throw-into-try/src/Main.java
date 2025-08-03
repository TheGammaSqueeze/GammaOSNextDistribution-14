/*
 * Copyright (C) 2022 The Android Open Source Project
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
        // Inline methods that sometimes throw.
        $noinline$assertEquals(-1000, $noinline$testThrowsWithZero(0));
        $noinline$assertEquals(1, $noinline$testThrowsWithZero(1));

        // Tests that we can correctly inline even when the throw is not caught.
        try {
            $noinline$testThrowNotCaught(0);
            unreachable();
        } catch (Error expected) {
        }
    }

    public static void $noinline$assertEquals(int expected, int result) {
        if (expected != result) {
            throw new Error("Expected: " + expected + ", found: " + result);
        }
    }

    private static int $noinline$testThrowsWithZero(int value) {
        try {
            return $inline$throwsWithZeroOrReturns(value);
        } catch (Error e) {
            return -1000;
        }
    }

    private static int $inline$throwsWithZeroOrReturns(int value) {
        if (value == 0) {
            throw new Error("Zero!");
        } else {
            return value;
        }
    }

    private static int $noinline$testThrowNotCaught(int value) {
        try {
            return $inline$throwsWithZeroOrReturns(value);
        } catch (Exception e) {
            return -1000;
        }
    }

    private static void unreachable() throws Exception{
        throw new Exception("Unreachable");
    }
}
