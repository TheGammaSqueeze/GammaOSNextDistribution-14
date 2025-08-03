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
  public static void main(String[] args) {
    $noinline$testSingleTryCatch();
    $noinline$testSingleTryCatchTwice();
    $noinline$testSingleTryCatchDifferentInputs();
    $noinline$testDifferentTryCatches();
    $noinline$testTryCatchFinally();
    $noinline$testTryCatchFinallyDifferentInputs();
    $noinline$testRecursiveTryCatch();
    $noinline$testDoNotInlineInsideTryInlineInsideCatch();
    $noinline$testInlineInsideNestedCatches();
    $noinline$testBeforeAfterTryCatch();
    $noinline$testDifferentTypes();
    $noinline$testRawThrow();
    $noinline$testRawThrowTwice();
    $noinline$testThrowCaughtInOuterMethod();
  }

  public static void $noinline$assertEquals(int expected, int result) {
    if (expected != result) {
      throw new Error("Expected: " + expected + ", found: " + result);
    }
  }

  // Basic try catch inline.
  private static void $noinline$testSingleTryCatch() {
    int[] numbers = {};
    $noinline$assertEquals(1, $inline$OOBTryCatch(numbers));
  }

  // Two instances of the same method with a try catch.
  private static void $noinline$testSingleTryCatchTwice() {
    int[] numbers = {};
    $noinline$assertEquals(1, $inline$OOBTryCatch(numbers));
    $noinline$assertEquals(1, $inline$OOBTryCatch(numbers));
  }

  // Triggering both normal and the exceptional flow.
  private static void $noinline$testSingleTryCatchDifferentInputs() {
    $noinline$assertEquals(1, $inline$OOBTryCatch(null));
    int[] numbers = {};
    $noinline$assertEquals(1, $inline$OOBTryCatch(numbers));
    int[] filled_numbers = {42};
    $noinline$assertEquals(42, $inline$OOBTryCatch(filled_numbers));
  }


  // Two different try catches, with the same catch's dex_pc.
  private static void $noinline$testDifferentTryCatches() {
    int[] numbers = {};
    $noinline$assertEquals(1, $inline$OOBTryCatch(numbers));
    $noinline$assertEquals(2, $inline$OtherOOBTryCatch(numbers));
  }

  // Basic try/catch/finally.
  private static void $noinline$testTryCatchFinally() {
    int[] numbers = {};
    $noinline$assertEquals(3, $inline$OOBTryCatchFinally(numbers));
  }

  // Triggering both normal and the exceptional flow.
  private static void $noinline$testTryCatchFinallyDifferentInputs() {
    $noinline$assertEquals(3, $inline$OOBTryCatchFinally(null));
    int[] numbers = {};
    $noinline$assertEquals(3, $inline$OOBTryCatchFinally(numbers));
    int[] filled_numbers = {42};
    $noinline$assertEquals(42, $inline$OOBTryCatchFinally(filled_numbers));
  }

  // Test that we can inline even when the try catch is several levels deep.
  private static void $noinline$testRecursiveTryCatch() {
    int[] numbers = {};
    $noinline$assertEquals(1, $inline$OOBTryCatchLevel4(numbers));
  }

  // Tests that we don't inline inside outer tries, but we do inline inside of catches.
  /// CHECK-START: void Main.$noinline$testDoNotInlineInsideTryInlineInsideCatch() inliner (before)
  /// CHECK:       InvokeStaticOrDirect method_name:Main.DoNotInlineOOBTryCatch
  /// CHECK:       InvokeStaticOrDirect method_name:Main.$inline$OOBTryCatch

  /// CHECK-START: void Main.$noinline$testDoNotInlineInsideTryInlineInsideCatch() inliner (after)
  /// CHECK:       InvokeStaticOrDirect method_name:Main.DoNotInlineOOBTryCatch
  private static void $noinline$testDoNotInlineInsideTryInlineInsideCatch() {
    int val = 0;
    try {
      int[] numbers = {};
      val = DoNotInlineOOBTryCatch(numbers);
    } catch (Exception ex) {
      unreachable();
      // This is unreachable but we will still compile it so it works for checking that it inlines.
      int[] numbers = {};
      $inline$OOBTryCatch(numbers);
    }
    $noinline$assertEquals(1, val);
  }

  private static void $noinline$emptyMethod() {}

  private static void $inline$testInlineInsideNestedCatches_inner() {
    try {
      $noinline$emptyMethod();
    } catch (Exception ex) {
      int[] numbers = {};
      $noinline$assertEquals(1, $inline$OOBTryCatch(numbers));
    }
  }

  private static void $noinline$testInlineInsideNestedCatches() {
    try {
      $noinline$emptyMethod();
    } catch (Exception ex) {
      $inline$testInlineInsideNestedCatches_inner();
    }
  }

  // Tests that outer tries or catches don't affect as long as we are not inlining the inner
  // try/catch inside of them.
  private static void $noinline$testBeforeAfterTryCatch() {
    int[] numbers = {};
    $noinline$assertEquals(1, $inline$OOBTryCatch(numbers));

    // Unrelated try catch does not block inlining outside of it. We fill it in to make sure it is
    // still there by the time the inliner runs.
    int val = 0;
    try {
      int[] other_array = {};
      val = other_array[0];
    } catch (Exception ex) {
      $noinline$assertEquals(0, val);
      val = 1;
    }
    $noinline$assertEquals(1, val);

    $noinline$assertEquals(1, $inline$OOBTryCatch(numbers));
  }

  // Tests different try catch types in the same outer method.
  private static void $noinline$testDifferentTypes() {
    int[] numbers = {};
    $noinline$assertEquals(1, $inline$OOBTryCatch(numbers));
    $noinline$assertEquals(2, $inline$OtherOOBTryCatch(numbers));
    $noinline$assertEquals(123, $inline$ParseIntTryCatch("123"));
    $noinline$assertEquals(-1, $inline$ParseIntTryCatch("abc"));
  }

  // Tests a raw throw (rather than an instruction that happens to throw).
  private static void $noinline$testRawThrow() {
    $noinline$assertEquals(1, $inline$rawThrowCaught());
  }

  // Tests a raw throw twice.
  private static void $noinline$testRawThrowTwice() {
    $noinline$assertEquals(1, $inline$rawThrowCaught());
    $noinline$assertEquals(1, $inline$rawThrowCaught());
  }

  // Tests that the outer method can successfully catch the throw in the inner method.
  private static void $noinline$testThrowCaughtInOuterMethod() {
    int[] numbers = {};
    $noinline$assertEquals(1, $inline$testThrowCaughtInOuterMethod_simpleTryCatch(numbers));
    $noinline$assertEquals(1, $inline$testThrowCaughtInOuterMethod_simpleTryCatch_inliningInner(numbers));
    $noinline$assertEquals(1, $inline$testThrowCaughtInOuterMethod_withFinally(numbers));
  }

  // Building blocks for the test functions.
  private static int $inline$OOBTryCatch(int[] array) {
    try {
      return array[0];
    } catch (Exception e) {
      return 1;
    }
  }

  private static int $inline$OtherOOBTryCatch(int[] array) {
    try {
      return array[0];
    } catch (Exception e) {
      return 2;
    }
  }

  private static int $inline$OOBTryCatchFinally(int[] array) {
    int val = 0;
    try {
      val = 1;
      return array[0];
    } catch (Exception e) {
      val = 2;
    } finally {
      val = 3;
    }
    return val;
  }

  // If we make the depthness a parameter, we wouldn't be able to mark as $inline$ and we would
  // need extra CHECKer statements.
  private static int $inline$OOBTryCatchLevel4(int[] array) {
    return $inline$OOBTryCatchLevel3(array);
  }

  private static int $inline$OOBTryCatchLevel3(int[] array) {
    return $inline$OOBTryCatchLevel2(array);
  }

  private static int $inline$OOBTryCatchLevel2(int[] array) {
    return $inline$OOBTryCatchLevel1(array);
  }

  private static int $inline$OOBTryCatchLevel1(int[] array) {
    return $inline$OOBTryCatch(array);
  }

  private static int DoNotInlineOOBTryCatch(int[] array) {
    try {
      return array[0];
    } catch (Exception e) {
      return 1;
    }
  }

  private static void unreachable() {
    throw new Error("Unreachable");
  }

  private static int $inline$ParseIntTryCatch(String str) {
    try {
      return Integer.parseInt(str);
    } catch (NumberFormatException ex) {
      return -1;
    }
  }

  private static int $inline$rawThrowCaught() {
    try {
      throw new Error();
    } catch (Error e) {
      return 1;
    }
  }

  private static int $inline$testThrowCaughtInOuterMethod_simpleTryCatch(int[] array) {
    int val = 0;
    try {
      $noinline$throwingMethod(array);
    } catch (Exception ex) {
      val = 1;
    }
    return val;
  }

  private static int $noinline$throwingMethod(int[] array) {
    return array[0];
  }

  private static int $inline$testThrowCaughtInOuterMethod_simpleTryCatch_inliningInner(int[] array) {
    int val = 0;
    try {
      $inline$throwingMethod(array);
    } catch (Exception ex) {
      val = 1;
    }
    return val;
  }

  private static int $inline$throwingMethod(int[] array) {
    return array[0];
  }

  private static int $inline$testThrowCaughtInOuterMethod_withFinally(int[] array) {
    int val = 0;
    try {
      $noinline$throwingMethodWithFinally(array);
    } catch (Exception ex) {
      System.out.println("Our battle it will be legendary!");
      val = 1;
    }
    return val;
  }

  private static int $noinline$throwingMethodWithFinally(int[] array) {
    try {
      return array[0];
    } finally {
      System.out.println("Finally, a worthy opponent!");
    }
  }
}
