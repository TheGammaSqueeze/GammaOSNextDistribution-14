/*
 * Copyright (C) 2016 The Android Open Source Project
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

  static long longValue;

  public static void assertEquals(float expected, float result) {
    if (expected != result) {
      throw new Error("Expected: " + expected + ", found: " + result);
    }
  }

  public static void main(String[] args) {
    assertEquals(1.0F, $noinline$longToFloat(true));
    assertEquals(2.0F, $noinline$longToFloat(false));
  }

  /// CHECK-START: float Main.$noinline$longToFloat(boolean) register (after)
  /// CHECK:     <<Get:j\d+>>      StaticFieldGet field_name:Main.longValue
  /// CHECK:     <<Convert:f\d+>>  TypeConversion [<<Get>>]
  /// CHECK:                       Return [<<Convert>>]

  static float $noinline$longToFloat(boolean param) {
    // This if else is to avoid constant folding the long constant into a float constant.
    if (param) {
      longValue = $inline$returnConstOne();
    } else {
      longValue = $inline$returnConstTwo();
    }
    // This call prevents D8 from replacing the result of the sget instruction
    // in the return below by the result of the call to $inline$returnConstOne/Two() above.
    $inline$preventRedundantFieldLoadEliminationInD8();
    return (float) longValue;
  }

  static long $inline$returnConstOne() {
    return 1L;
  }

  static long $inline$returnConstTwo() {
    return 2L;
  }

  static void $inline$preventRedundantFieldLoadEliminationInD8() {}
}
