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
        // Switch case.
        assertEquals("First case", $noinline$testSinkReturnSwitch(1));
        assertEquals("Second case", $noinline$testSinkReturnSwitch(2));
        assertEquals("default", $noinline$testSinkReturnSwitch(3));
        $noinline$testSinkReturnVoidSwitch(1);

        // If/else if/else.
        assertEquals("First case", $noinline$testSinkReturnIfElse(1));
        assertEquals("Second case", $noinline$testSinkReturnIfElse(2));
        assertEquals("default", $noinline$testSinkReturnIfElse(3));
        $noinline$testSinkReturnVoidIfElse(1);

        // Non-trivial if cases.
        assertEquals("First case", $noinline$testSinkReturnSeparatedReturns(1));
        assertEquals("Second case", $noinline$testSinkReturnSeparatedReturns(2));
        assertEquals("default", $noinline$testSinkReturnSeparatedReturns(3));
        $noinline$testSinkReturnVoidSeparatedReturns(1);
    }

    /// CHECK-START: java.lang.String Main.$noinline$testSinkReturnSwitch(int) code_sinking (before)
    /// CHECK:     Return
    /// CHECK:     Return
    /// CHECK:     Return
    /// CHECK-NOT: Return

    /// CHECK-START: java.lang.String Main.$noinline$testSinkReturnSwitch(int) code_sinking (after)
    /// CHECK:     Return
    /// CHECK-NOT: Return
    private static String $noinline$testSinkReturnSwitch(int switch_id) {
        switch (switch_id) {
            case 1:
                return "First case";
            case 2:
                return "Second case";
            default:
                return "default";
        }
    }

    /// CHECK-START: void Main.$noinline$testSinkReturnVoidSwitch(int) code_sinking (before)
    /// CHECK:     ReturnVoid
    /// CHECK:     ReturnVoid
    /// CHECK:     ReturnVoid
    /// CHECK-NOT: ReturnVoid

    /// CHECK-START: void Main.$noinline$testSinkReturnVoidSwitch(int) code_sinking (after)
    /// CHECK:     ReturnVoid
    /// CHECK-NOT: ReturnVoid
    private static void $noinline$testSinkReturnVoidSwitch(int switch_id) {
        switch (switch_id) {
            case 1:
                $noinline$emptyMethod();
                return;
            case 2:
                $noinline$emptyMethod2();
                return;
            default:
                $noinline$emptyMethod3();
                return;
        }
    }

    /// CHECK-START: java.lang.String Main.$noinline$testSinkReturnIfElse(int) code_sinking (before)
    /// CHECK:     Return
    /// CHECK:     Return
    /// CHECK:     Return
    /// CHECK-NOT: Return

    /// CHECK-START: java.lang.String Main.$noinline$testSinkReturnIfElse(int) code_sinking (after)
    /// CHECK:     Return
    /// CHECK-NOT: Return
    private static String $noinline$testSinkReturnIfElse(int id) {
        if (id == 1) {
            return "First case";
        } else if (id == 2) {
            return "Second case";
        } else {
            return "default";
        }
    }

    /// CHECK-START: void Main.$noinline$testSinkReturnVoidIfElse(int) code_sinking (before)
    /// CHECK:     ReturnVoid
    /// CHECK:     ReturnVoid
    /// CHECK:     ReturnVoid
    /// CHECK-NOT: ReturnVoid

    /// CHECK-START: void Main.$noinline$testSinkReturnVoidIfElse(int) code_sinking (after)
    /// CHECK:     ReturnVoid
    /// CHECK-NOT: ReturnVoid
    private static void $noinline$testSinkReturnVoidIfElse(int id) {
        if (id == 1) {
            $noinline$emptyMethod();
            return;
        } else if (id == 2) {
            $noinline$emptyMethod2();
            return;
        } else {
            $noinline$emptyMethod3();
            return;
        }
    }

    /// CHECK-START: java.lang.String Main.$noinline$testSinkReturnSeparatedReturns(int) code_sinking (before)
    /// CHECK:     Return
    /// CHECK:     Return
    /// CHECK:     Return
    /// CHECK-NOT: Return

    /// CHECK-START: java.lang.String Main.$noinline$testSinkReturnSeparatedReturns(int) code_sinking (after)
    /// CHECK:     Return
    /// CHECK-NOT: Return
    private static String $noinline$testSinkReturnSeparatedReturns(int id) {
        if (id == 1) {
            return "First case";
        }
        $noinline$emptyMethod();

        if (id == 2) {
            return "Second case";
        }

        $noinline$emptyMethod2();
        return "default";
    }

    /// CHECK-START: void Main.$noinline$testSinkReturnVoidSeparatedReturns(int) code_sinking (before)
    /// CHECK:     ReturnVoid
    /// CHECK:     ReturnVoid
    /// CHECK:     ReturnVoid
    /// CHECK-NOT: ReturnVoid

    /// CHECK-START: void Main.$noinline$testSinkReturnVoidSeparatedReturns(int) code_sinking (after)
    /// CHECK:     ReturnVoid
    /// CHECK-NOT: ReturnVoid
    private static void $noinline$testSinkReturnVoidSeparatedReturns(int id) {
        if (id == 1) {
            return;
        }
        $noinline$emptyMethod();

        if (id == 2) {
            return;
        }

        $noinline$emptyMethod2();
        return;
    }

    private static void $noinline$emptyMethod() {}
    private static void $noinline$emptyMethod2() {}
    private static void $noinline$emptyMethod3() {}

    private static void assertEquals(String expected, String result) {
        if (expected != result) {
            throw new Error("Expected: " + expected + ", found: " + result);
        }
    }
}
