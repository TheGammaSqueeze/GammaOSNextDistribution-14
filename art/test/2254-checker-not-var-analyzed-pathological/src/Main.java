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
        $noinline$assertEquals(0, $noinline$pathologicalCase());
    }

    public static void $noinline$assertEquals(int expected, int result) {
        if (expected != result) {
            throw new Error("Expected: " + expected + ", found: " + result);
        }
    }

    // Empty $noinline$ method so that it doesn't get removed.
    private static void $noinline$emptyMethod(int val) {}

    // A pathological case which has > 15 loop header phis in a row.
    /// CHECK-START: int Main.$noinline$pathologicalCase() induction_var_analysis (before)
    /// CHECK: <<Const0:i\d+>> IntConstant 0
    /// CHECK: <<Phi1:i\d+>>  Phi [<<Const0>>,<<Add1:i\d+>>]
    /// CHECK: <<Phi2:i\d+>>  Phi [<<Phi1>>,<<Add2:i\d+>>]
    /// CHECK: <<Phi3:i\d+>>  Phi [<<Phi2>>,<<Add3:i\d+>>]
    /// CHECK: <<Phi4:i\d+>>  Phi [<<Phi3>>,<<Add4:i\d+>>]
    /// CHECK: <<Phi5:i\d+>>  Phi [<<Phi4>>,<<Add5:i\d+>>]
    /// CHECK: <<Phi6:i\d+>>  Phi [<<Phi5>>,<<Add6:i\d+>>]
    /// CHECK: <<Phi7:i\d+>>  Phi [<<Phi6>>,<<Add7:i\d+>>]
    /// CHECK: <<Phi8:i\d+>>  Phi [<<Phi7>>,<<Add8:i\d+>>]
    /// CHECK: <<Phi9:i\d+>>  Phi [<<Phi8>>,<<Add9:i\d+>>]
    /// CHECK: <<Phi10:i\d+>> Phi [<<Phi9>>,<<Add10:i\d+>>]
    /// CHECK: <<Phi11:i\d+>> Phi [<<Phi10>>,<<Add11:i\d+>>]
    /// CHECK: <<Phi12:i\d+>> Phi [<<Phi11>>,<<Add12:i\d+>>]
    /// CHECK: <<Phi13:i\d+>> Phi [<<Phi12>>,<<Add13:i\d+>>]
    /// CHECK: <<Phi14:i\d+>> Phi [<<Phi13>>,<<Add14:i\d+>>]
    /// CHECK: <<Phi15:i\d+>> Phi [<<Phi14>>,<<Add15:i\d+>>]
    /// CHECK: <<Phi16:i\d+>> Phi [<<Phi15>>,<<Add16:i\d+>>]
    private static int $noinline$pathologicalCase() {
        int value = 0;
        for (; value < 3; value++) {
            $noinline$emptyMethod(value);
        }

        for (; value < 5; value++) {
            $noinline$emptyMethod(value);
        }

        for (; value < 7; value++) {
            $noinline$emptyMethod(value);
        }

        for (; value < 9; value++) {
            $noinline$emptyMethod(value);
        }

        for (; value < 11; value++) {
            $noinline$emptyMethod(value);
        }

        for (; value < 13; value++) {
            $noinline$emptyMethod(value);
        }

        for (; value < 15; value++) {
            $noinline$emptyMethod(value);
        }

        for (; value < 17; value++) {
            $noinline$emptyMethod(value);
        }

        for (; value < 19; value++) {
            $noinline$emptyMethod(value);
        }

        for (; value < 21; value++) {
            $noinline$emptyMethod(value);
        }

        for (; value < 23; value++) {
            $noinline$emptyMethod(value);
        }

        for (; value < 25; value++) {
            $noinline$emptyMethod(value);
        }

        for (; value < 27; value++) {
            $noinline$emptyMethod(value);
        }

        for (; value < 29; value++) {
            $noinline$emptyMethod(value);
        }

        for (; value < 31; value++) {
            $noinline$emptyMethod(value);
        }

        for (; value < 33; value++) {
            $noinline$emptyMethod(value);
        }

        return 0;
    }
}
