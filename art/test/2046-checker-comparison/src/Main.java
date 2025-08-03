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
        assertEquals(1, $noinline$testEqualBool(true));
        assertEquals(0, $noinline$testNotEqualBool(true));
        // Other comparisons e.g. `<` don't exists for boolean values.

        assertEquals(1, $noinline$testEqualInt(0));
        assertEquals(0, $noinline$testNotEqualInt(0));
        assertEquals(0, $noinline$testGreaterThanInt(0));
        assertEquals(1, $noinline$testGreaterThanOrEqualInt(0));
        assertEquals(0, $noinline$testLessThanInt(0));
        assertEquals(1, $noinline$testLessThanOrEqualInt(0));

        assertEquals(1, $noinline$testEqualLong(0L));
        assertEquals(0, $noinline$testNotEqualLong(0L));
        assertEquals(0, $noinline$testGreaterThanLong(0L));
        assertEquals(1, $noinline$testGreaterThanOrEqualLong(0L));
        assertEquals(0, $noinline$testLessThanLong(0L));
        assertEquals(1, $noinline$testLessThanOrEqualLong(0L));

        // We cannot perform the optimization on unknown float/doubles since equality for NaN
        // returns the opposite as for normal numbers.
        assertEquals(1, $noinline$testEqualFloat(0f));
        assertEquals(0, $noinline$testEqualFloat(Float.NaN));
        assertEquals(1, $noinline$testEqualFloat(Float.NEGATIVE_INFINITY));
        assertEquals(1, $noinline$testEqualFloat(Float.POSITIVE_INFINITY));
        assertEquals(0, $noinline$testNotEqualFloat(0f));
        assertEquals(1, $noinline$testNotEqualFloat(Float.NaN));
        assertEquals(0, $noinline$testNotEqualFloat(Float.NEGATIVE_INFINITY));
        assertEquals(0, $noinline$testNotEqualFloat(Float.POSITIVE_INFINITY));
        assertEquals(0, $noinline$testGreaterThanFloat(0f));
        assertEquals(0, $noinline$testGreaterThanFloat(Float.NaN));
        assertEquals(0, $noinline$testGreaterThanFloat(Float.NEGATIVE_INFINITY));
        assertEquals(0, $noinline$testGreaterThanFloat(Float.POSITIVE_INFINITY));
        assertEquals(1, $noinline$testGreaterThanOrEqualFloat(0f));
        assertEquals(0, $noinline$testGreaterThanOrEqualFloat(Float.NaN));
        assertEquals(1, $noinline$testGreaterThanOrEqualFloat(Float.NEGATIVE_INFINITY));
        assertEquals(1, $noinline$testGreaterThanOrEqualFloat(Float.POSITIVE_INFINITY));
        assertEquals(0, $noinline$testLessThanFloat(0f));
        assertEquals(0, $noinline$testLessThanFloat(Float.NaN));
        assertEquals(0, $noinline$testLessThanFloat(Float.NEGATIVE_INFINITY));
        assertEquals(0, $noinline$testLessThanFloat(Float.POSITIVE_INFINITY));
        assertEquals(1, $noinline$testLessThanOrEqualFloat(0f));
        assertEquals(0, $noinline$testLessThanOrEqualFloat(Float.NaN));
        assertEquals(1, $noinline$testLessThanOrEqualFloat(Float.NEGATIVE_INFINITY));
        assertEquals(1, $noinline$testLessThanOrEqualFloat(Float.POSITIVE_INFINITY));

        assertEquals(1, $noinline$testEqualDouble(0d));
        assertEquals(0, $noinline$testEqualDouble(Double.NaN));
        assertEquals(1, $noinline$testEqualDouble(Double.NEGATIVE_INFINITY));
        assertEquals(1, $noinline$testEqualDouble(Double.POSITIVE_INFINITY));
        assertEquals(0, $noinline$testNotEqualDouble(0d));
        assertEquals(1, $noinline$testNotEqualDouble(Double.NaN));
        assertEquals(0, $noinline$testNotEqualDouble(Double.NEGATIVE_INFINITY));
        assertEquals(0, $noinline$testNotEqualDouble(Double.POSITIVE_INFINITY));
        assertEquals(0, $noinline$testGreaterThanDouble(0d));
        assertEquals(0, $noinline$testGreaterThanDouble(Double.NaN));
        assertEquals(0, $noinline$testGreaterThanDouble(Double.NEGATIVE_INFINITY));
        assertEquals(0, $noinline$testGreaterThanDouble(Double.POSITIVE_INFINITY));
        assertEquals(1, $noinline$testGreaterThanOrEqualDouble(0d));
        assertEquals(0, $noinline$testGreaterThanOrEqualDouble(Double.NaN));
        assertEquals(1, $noinline$testGreaterThanOrEqualDouble(Double.NEGATIVE_INFINITY));
        assertEquals(1, $noinline$testGreaterThanOrEqualDouble(Double.POSITIVE_INFINITY));
        assertEquals(0, $noinline$testLessThanDouble(0d));
        assertEquals(0, $noinline$testLessThanDouble(Double.NaN));
        assertEquals(0, $noinline$testLessThanDouble(Double.NEGATIVE_INFINITY));
        assertEquals(0, $noinline$testLessThanDouble(Double.POSITIVE_INFINITY));
        assertEquals(1, $noinline$testLessThanOrEqualDouble(0d));
        assertEquals(0, $noinline$testLessThanOrEqualDouble(Double.NaN));
        assertEquals(1, $noinline$testLessThanOrEqualDouble(Double.NEGATIVE_INFINITY));
        assertEquals(1, $noinline$testLessThanOrEqualDouble(Double.POSITIVE_INFINITY));

        assertEquals(1, $noinline$testEqualObject(null));
        assertEquals(1, $noinline$testEqualObject(new Object()));
        assertEquals(0, $noinline$testNotEqualObject(null));
        assertEquals(0, $noinline$testNotEqualObject(new Object()));
        // Other comparisons e.g. `<` don't exists for references.
    }

    /// CHECK-START: int Main.$noinline$testEqualBool(boolean) register (after)
    /// CHECK: <<Const1:i\d+>> IntConstant 1
    /// CHECK:                 Return [<<Const1>>]
    private static int $noinline$testEqualBool(boolean a) {
        if (a == $inline$returnValueBool(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testNotEqualBool(boolean) register (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]
    private static int $noinline$testNotEqualBool(boolean a) {
        if (a != $inline$returnValueBool(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    private static boolean $inline$returnValueBool(boolean a) {
        return a;
    }

    /// CHECK-START: int Main.$noinline$testEqualInt(int) register (after)
    /// CHECK: <<Const1:i\d+>> IntConstant 1
    /// CHECK:                 Return [<<Const1>>]
    private static int $noinline$testEqualInt(int a) {
        if (a == $inline$returnValueInt(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testNotEqualInt(int) register (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]
    private static int $noinline$testNotEqualInt(int a) {
        if (a != $inline$returnValueInt(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testGreaterThanInt(int) register (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]
    private static int $noinline$testGreaterThanInt(int a) {
        if (a > $inline$returnValueInt(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testGreaterThanOrEqualInt(int) register (after)
    /// CHECK: <<Const1:i\d+>> IntConstant 1
    /// CHECK:                 Return [<<Const1>>]
    private static int $noinline$testGreaterThanOrEqualInt(int a) {
        if (a >= $inline$returnValueInt(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testLessThanInt(int) register (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]
    private static int $noinline$testLessThanInt(int a) {
        if (a < $inline$returnValueInt(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testLessThanOrEqualInt(int) register (after)
    /// CHECK: <<Const1:i\d+>> IntConstant 1
    /// CHECK:                 Return [<<Const1>>]
    private static int $noinline$testLessThanOrEqualInt(int a) {
        if (a <= $inline$returnValueInt(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    private static int $inline$returnValueInt(int a) {
        return a;
    }

    /// CHECK-START: int Main.$noinline$testEqualLong(long) register (after)
    /// CHECK: <<Const1:i\d+>> IntConstant 1
    /// CHECK:                 Return [<<Const1>>]
    private static int $noinline$testEqualLong(long a) {
        if (a == $inline$returnValueLong(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testNotEqualLong(long) register (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]
    private static int $noinline$testNotEqualLong(long a) {
        if (a != $inline$returnValueLong(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testGreaterThanLong(long) register (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]
    private static int $noinline$testGreaterThanLong(long a) {
        if (a > $inline$returnValueLong(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testGreaterThanOrEqualLong(long) register (after)
    /// CHECK: <<Const1:i\d+>> IntConstant 1
    /// CHECK:                 Return [<<Const1>>]
    private static int $noinline$testGreaterThanOrEqualLong(long a) {
        if (a >= $inline$returnValueLong(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testLessThanLong(long) register (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]
    private static int $noinline$testLessThanLong(long a) {
        if (a < $inline$returnValueLong(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testLessThanOrEqualLong(long) register (after)
    /// CHECK: <<Const1:i\d+>> IntConstant 1
    /// CHECK:                 Return [<<Const1>>]
    private static int $noinline$testLessThanOrEqualLong(long a) {
        if (a <= $inline$returnValueLong(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    private static long $inline$returnValueLong(long a) {
        return a;
    }

    /// CHECK-START: int Main.$noinline$testEqualFloat(float) register (after)
    /// CHECK: <<NotEqual:z\d+>> NotEqual
    /// CHECK: <<BNot:z\d+>>     BooleanNot [<<NotEqual>>]
    /// CHECK:                   Return [<<BNot>>]
    private static int $noinline$testEqualFloat(float a) {
        if (a == $inline$returnValueFloat(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testNotEqualFloat(float) register (after)
    /// CHECK: <<Equal:z\d+>>    Equal
    /// CHECK: <<BNot:z\d+>>     BooleanNot [<<Equal>>]
    /// CHECK:                   Return [<<BNot>>]
    private static int $noinline$testNotEqualFloat(float a) {
        if (a != $inline$returnValueFloat(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testGreaterThanFloat(float) register (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]
    private static int $noinline$testGreaterThanFloat(float a) {
        if (a > $inline$returnValueFloat(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testGreaterThanOrEqualFloat(float) register (after)
    /// CHECK: <<LessThan:z\d+>> LessThan
    /// CHECK: <<BNot:z\d+>>     BooleanNot [<<LessThan>>]
    /// CHECK:                   Return [<<BNot>>]
    private static int $noinline$testGreaterThanOrEqualFloat(float a) {
        if (a >= $inline$returnValueFloat(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testLessThanFloat(float) register (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]
    private static int $noinline$testLessThanFloat(float a) {
        if (a < $inline$returnValueFloat(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testLessThanOrEqualFloat(float) register (after)
    /// CHECK: <<GreaterThan:z\d+>> GreaterThan
    /// CHECK: <<BNot:z\d+>>        BooleanNot [<<GreaterThan>>]
    /// CHECK:                      Return [<<BNot>>]
    private static int $noinline$testLessThanOrEqualFloat(float a) {
        if (a <= $inline$returnValueFloat(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    private static float $inline$returnValueFloat(float a) {
        return a;
    }

    /// CHECK-START: int Main.$noinline$testEqualDouble(double) register (after)
    /// CHECK: <<NotEqual:z\d+>> NotEqual
    /// CHECK: <<BNot:z\d+>>     BooleanNot [<<NotEqual>>]
    /// CHECK:                   Return [<<BNot>>]
    private static int $noinline$testEqualDouble(double a) {
        if (a == $inline$returnValueDouble(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testNotEqualDouble(double) register (after)
    /// CHECK: <<Equal:z\d+>>    Equal
    /// CHECK: <<BNot:z\d+>>     BooleanNot [<<Equal>>]
    /// CHECK:                   Return [<<BNot>>]
    private static int $noinline$testNotEqualDouble(double a) {
        if (a != $inline$returnValueDouble(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testGreaterThanDouble(double) register (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]
    private static int $noinline$testGreaterThanDouble(double a) {
        if (a > $inline$returnValueDouble(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testGreaterThanOrEqualDouble(double) register (after)
    /// CHECK: <<LessThan:z\d+>> LessThan
    /// CHECK: <<BNot:z\d+>>     BooleanNot [<<LessThan>>]
    /// CHECK:                   Return [<<BNot>>]
    private static int $noinline$testGreaterThanOrEqualDouble(double a) {
        if (a >= $inline$returnValueDouble(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testLessThanDouble(double) register (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]
    private static int $noinline$testLessThanDouble(double a) {
        if (a < $inline$returnValueDouble(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testLessThanOrEqualDouble(double) register (after)
    /// CHECK: <<GreaterThan:z\d+>> GreaterThan
    /// CHECK: <<BNot:z\d+>>        BooleanNot [<<GreaterThan>>]
    /// CHECK:                      Return [<<BNot>>]
    private static int $noinline$testLessThanOrEqualDouble(double a) {
        if (a <= $inline$returnValueDouble(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    private static double $inline$returnValueDouble(double a) {
        return a;
    }

    /// CHECK-START: int Main.$noinline$testEqualObject(java.lang.Object) register (after)
    /// CHECK: <<Const1:i\d+>> IntConstant 1
    /// CHECK:                 Return [<<Const1>>]
    private static int $noinline$testEqualObject(Object a) {
        if (a == $inline$returnValueObject(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// CHECK-START: int Main.$noinline$testNotEqualObject(java.lang.Object) register (after)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK:                 Return [<<Const0>>]
    private static int $noinline$testNotEqualObject(Object a) {
        if (a != $inline$returnValueObject(a)) {
            return 1;
        } else {
            return 0;
        }
    }

    private static Object $inline$returnValueObject(Object a) {
        return a;
    }

    static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + " got " + actual);
        }
    }
}
