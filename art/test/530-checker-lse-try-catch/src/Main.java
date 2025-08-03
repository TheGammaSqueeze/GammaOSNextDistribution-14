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

class Point {
  int x;
  int y;
}

public class Main {
  public static void main(String[] args) {
    final boolean boolean_throw = false;
    final boolean boolean_other_throw = false;
    assertEquals(3,
        $noinline$testDifferentFields(
            new Point(), new Point(), boolean_throw, boolean_other_throw));
    assertEquals(1, $noinline$testRedundantStore(new Point(), boolean_throw, boolean_other_throw));
    assertEquals(1, $noinline$testTryCatchBlocking(new Point(), boolean_throw));
    assertEquals(1, $noinline$testTryCatchPhi(new Point(), boolean_throw));
    assertEquals(2, $noinline$testTryCatchPhiWithTwoCatches(new Point(), new int[0]));
    assertEquals(1, $noinline$testKeepStoreInsideTry());
    assertEquals(10, $noinline$testDontKeepStoreInsideCatch(new int[]{10}));
    assertEquals(30, $noinline$testDontKeepStoreInsideCatch(new int[]{}));
    assertEquals(10, $noinline$testKeepStoreInsideCatchWithOuterTry(new int[]{10}));
    assertEquals(30, $noinline$testKeepStoreInsideCatchWithOuterTry(new int[]{}));
    assertEquals(40, $noinline$testDontKeepStoreInsideFinally(new int[]{10}));
    try {
      assertEquals(30, $noinline$testDontKeepStoreInsideFinally(new int[]{}));
      throw new Error("Unreachable");
    } catch (ArrayIndexOutOfBoundsException expected) {
    }
    assertEquals(10, $noinline$testDontKeepStoreInsideOuterCatch(new int[]{10}));
    assertEquals(100030, $noinline$testDontKeepStoreInsideOuterCatch(new int[]{}));
    assertEquals(150, $noinline$test40());
  }

  /// CHECK-START: int Main.$noinline$testDifferentFields(Point, Point, boolean, boolean) load_store_elimination (before)
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.x
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.y
  /// CHECK-DAG:     InstanceFieldGet field_name:Point.x
  /// CHECK-DAG:     InstanceFieldGet field_name:Point.y

  /// CHECK-START: int Main.$noinline$testDifferentFields(Point, Point, boolean, boolean) load_store_elimination (after)
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.x
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.y

  /// CHECK-START: int Main.$noinline$testDifferentFields(Point, Point, boolean, boolean) load_store_elimination (after)
  /// CHECK-NOT:     InstanceFieldGet field_name:Point.x
  /// CHECK-NOT:     InstanceFieldGet field_name:Point.y

  // Consistency check to make sure the try/catches weren't removed by an earlier pass.
  /// CHECK-START: int Main.$noinline$testDifferentFields(Point, Point, boolean, boolean) load_store_elimination (after)
  /// CHECK:         TryBoundary kind:entry
  /// CHECK:         TryBoundary kind:entry

  // Different fields shouldn't alias.
  private static int $noinline$testDifferentFields(
      Point obj1, Point obj2, boolean boolean_throw, boolean boolean_other_throw) {
    try {
      if (boolean_throw) {
        throw new Error();
      }
    } catch (Error e) {
      return 0;
    }
    obj1.x = 1;
    obj2.y = 2;
    int result = obj1.x + obj2.y;
    try {
      if (boolean_other_throw) {
        throw new Error();
      }
    } catch (Error e) {
      return 0;
    }
    return result;
  }

  /// CHECK-START: int Main.$noinline$testRedundantStore(Point, boolean, boolean) load_store_elimination (before)
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.y
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.y
  /// CHECK-DAG:     InstanceFieldGet field_name:Point.y

  /// CHECK-START: int Main.$noinline$testRedundantStore(Point, boolean, boolean) load_store_elimination (after)
  /// CHECK:         InstanceFieldSet field_name:Point.y
  /// CHECK-NOT:     InstanceFieldSet field_name:Point.y

  /// CHECK-START: int Main.$noinline$testRedundantStore(Point, boolean, boolean) load_store_elimination (after)
  /// CHECK-NOT:     InstanceFieldGet field_name:Point.y

  // Consistency check to make sure the try/catches weren't removed by an earlier pass.
  /// CHECK-START: int Main.$noinline$testRedundantStore(Point, boolean, boolean) load_store_elimination (after)
  /// CHECK-DAG:     TryBoundary kind:entry
  /// CHECK-DAG:     TryBoundary kind:entry

  // Redundant store of the same value.
  private static int $noinline$testRedundantStore(
      Point obj, boolean boolean_throw, boolean boolean_other_throw) {
    try {
      if (boolean_throw) {
        throw new Error();
      }
    } catch (Error e) {
      return 0;
    }
    obj.y = 1;
    obj.y = 1;
    try {
      if (boolean_other_throw) {
        throw new Error();
      }
    } catch (Error e) {
      return 0;
    }
    return obj.y;
  }

  /// CHECK-START: int Main.$noinline$testTryCatchBlocking(Point, boolean) load_store_elimination (before)
  /// CHECK: InstanceFieldSet field_name:Point.y
  /// CHECK: InstanceFieldGet field_name:Point.y

  /// CHECK-START: int Main.$noinline$testTryCatchBlocking(Point, boolean) load_store_elimination (after)
  /// CHECK: InstanceFieldSet field_name:Point.y
  /// CHECK: InstanceFieldGet field_name:Point.y

  // Consistency check to make sure the try/catch wasn't removed by an earlier pass.
  /// CHECK-START: int Main.$noinline$testTryCatchBlocking(Point, boolean) load_store_elimination (after)
  /// CHECK: TryBoundary kind:entry

  // We cannot remove the Get since we might have thrown.
  private static int $noinline$testTryCatchBlocking(Point obj, boolean boolean_throw) {
    obj.y = 1;
    try {
      if (boolean_throw) {
        throw new Error();
      }
    } catch (Error e) {
    }
    return obj.y;
  }

  /// CHECK-START: int Main.$noinline$testTryCatchPhi(Point, boolean) load_store_elimination (before)
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.y
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.y
  /// CHECK-DAG:     InstanceFieldGet field_name:Point.y

  /// CHECK-START: int Main.$noinline$testTryCatchPhi(Point, boolean) load_store_elimination (after)
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.y
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.y

  /// CHECK-START: int Main.$noinline$testTryCatchPhi(Point, boolean) load_store_elimination (after)
  /// CHECK-NOT:     InstanceFieldGet field_name:Point.y

  // Consistency check to make sure the try/catch wasn't removed by an earlier pass.
  /// CHECK-START: int Main.$noinline$testTryCatchPhi(Point, boolean) load_store_elimination (after)
  /// CHECK-DAG:     TryBoundary kind:entry

  // We either threw and we set the value in the catch, or we didn't throw and we set the value
  // before the catch. We can solve that with a Phi and skip the get.
  private static int $noinline$testTryCatchPhi(Point obj, boolean boolean_throw) {
    obj.y = 1;
    try {
      if (boolean_throw) {
        throw new Error();
      }
    } catch (Error e) {
      obj.y = 2;
    }
    return obj.y;
  }


  /// CHECK-START: int Main.$noinline$testTryCatchPhiWithTwoCatches(Point, int[]) load_store_elimination (before)
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.y
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.y
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.y
  /// CHECK-DAG:     InstanceFieldGet field_name:Point.y

  /// CHECK-START: int Main.$noinline$testTryCatchPhiWithTwoCatches(Point, int[]) load_store_elimination (after)
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.y
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.y
  /// CHECK-DAG:     InstanceFieldSet field_name:Point.y

  /// CHECK-START: int Main.$noinline$testTryCatchPhiWithTwoCatches(Point, int[]) load_store_elimination (after)
  /// CHECK-NOT: InstanceFieldGet field_name:Point.y

  // Consistency check to make sure the try/catch wasn't removed by an earlier pass.
  /// CHECK-START: int Main.$noinline$testTryCatchPhiWithTwoCatches(Point, int[]) load_store_elimination (after)
  /// CHECK-DAG: TryBoundary kind:entry
  private static int $noinline$testTryCatchPhiWithTwoCatches(Point obj, int[] numbers) {
    obj.y = 1;
    try {
      if (numbers[0] == 1) {
        throw new Error();
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      obj.y = 2;
    } catch (Error e) {
      obj.y = 3;
    }
    return obj.y;
  }

  // Check that we don't eliminate the first store to `main.sumForKeepStoreInsideTryCatch` since it
  // is observable.

  // Consistency check to make sure the try/catch wasn't removed by an earlier pass.
  /// CHECK-START: int Main.$noinline$testKeepStoreInsideTry() load_store_elimination (after)
  /// CHECK-DAG: TryBoundary kind:entry

  /// CHECK-START: int Main.$noinline$testKeepStoreInsideTry() load_store_elimination (before)
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch

  /// CHECK-START: int Main.$noinline$testKeepStoreInsideTry() load_store_elimination (after)
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  private static int $noinline$testKeepStoreInsideTry() {
    Main main = new Main();
    main.sumForKeepStoreInsideTryCatch = 0;
    try {
      int[] array = {1};
      main.sumForKeepStoreInsideTryCatch += array[0];
      main.sumForKeepStoreInsideTryCatch += array[1];
      throw new RuntimeException("Unreachable");
    } catch (ArrayIndexOutOfBoundsException e) {
      return main.sumForKeepStoreInsideTryCatch;
    }
  }

  private static int $noinline$returnValue(int value) {
    return value;
  }

  /// CHECK-START: int Main.$noinline$testDontKeepStoreInsideCatch(int[]) load_store_elimination (before)
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  /// CHECK-NOT: InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch

  /// CHECK-START: int Main.$noinline$testDontKeepStoreInsideCatch(int[]) load_store_elimination (after)
  /// CHECK-NOT: InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  private static int $noinline$testDontKeepStoreInsideCatch(int[] array) {
    Main main = new Main();
    int value = 0;
    try {
      value = array[0];
    } catch (Exception e) {
      // These sets can be eliminated even though we have invokes since this catch is not part of an
      // outer try.
      main.sumForKeepStoreInsideTryCatch += $noinline$returnValue(10);
      main.sumForKeepStoreInsideTryCatch += $noinline$returnValue(20);
    }
    return main.sumForKeepStoreInsideTryCatch + value;
  }

  /// CHECK-START: int Main.$noinline$testKeepStoreInsideCatchWithOuterTry(int[]) load_store_elimination (before)
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  /// CHECK-NOT: InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch

  /// CHECK-START: int Main.$noinline$testKeepStoreInsideCatchWithOuterTry(int[]) load_store_elimination (after)
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  /// CHECK-NOT: InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  private static int $noinline$testKeepStoreInsideCatchWithOuterTry(int[] array) {
    Main main = new Main();
    int value = 0;
    try {
      try {
        value = array[0];
      } catch (Exception e) {
        // These sets can't be eliminated since this catch is part of a outer try.
        main.sumForKeepStoreInsideTryCatch += $noinline$returnValue(10);
        main.sumForKeepStoreInsideTryCatch += $noinline$returnValue(20);
      }
    } catch (Exception e) {
      value = 100000;
    }

    return main.sumForKeepStoreInsideTryCatch + value;
  }

  // Note that there are four `InstanceFieldSet` instead of two since we split the `finally` block
  // into the normal path, and the exceptional path.

  /// CHECK-START: int Main.$noinline$testDontKeepStoreInsideFinally(int[]) load_store_elimination (before)
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  /// CHECK-NOT: InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch

  /// CHECK-START: int Main.$noinline$testDontKeepStoreInsideFinally(int[]) load_store_elimination (after)
  /// CHECK-NOT: InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  private static int $noinline$testDontKeepStoreInsideFinally(int[] array) {
    Main main = new Main();
    int value = 0;
    try {
      value = array[0];
    } finally {
      // These sets can be eliminated even though we have invokes since this catch is not part of an
      // outer try.
      main.sumForKeepStoreInsideTryCatch += $noinline$returnValue(10);
      main.sumForKeepStoreInsideTryCatch += $noinline$returnValue(20);
    }
    return main.sumForKeepStoreInsideTryCatch + value;
  }

  // Checks that we are able to do LSE inside of catches which are outside of try blocks.

  /// CHECK-START: int Main.$noinline$testDontKeepStoreInsideOuterCatch(int[]) load_store_elimination (before)
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  /// CHECK-NOT: InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch

  // This store potentially can be eliminated too, but our phi creation logic doesn't realize it can
  // create a Phi for `main.sumForKeepStoreInsideTryCatch` and skip a store+load.

  /// CHECK-START: int Main.$noinline$testDontKeepStoreInsideOuterCatch(int[]) load_store_elimination (after)
  /// CHECK:     InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch
  /// CHECK-NOT: InstanceFieldSet field_name:Main.sumForKeepStoreInsideTryCatch

  private static int $noinline$testDontKeepStoreInsideOuterCatch(int[] array) {
    Main main = new Main();
    int value = 0;
    try {
      value = array[0];
    } catch (ArrayIndexOutOfBoundsException expected) {
      // These sets and gets are not considered to be part of a try so they are free to be
      // eliminated.
      main.sumForKeepStoreInsideTryCatch += $noinline$returnValue(10);
      main.sumForKeepStoreInsideTryCatch += $noinline$returnValue(20);
      try {
        value = array[0];
      } catch (ArrayIndexOutOfBoundsException expectedToo) {
        value = 100000;
      }
    }

    return main.sumForKeepStoreInsideTryCatch + value;
  }

  /// CHECK-START: int Main.$noinline$test40() load_store_elimination (before)
  /// CHECK:                     ArraySet
  /// CHECK:                     DivZeroCheck
  /// CHECK:                     ArraySet
  /// CHECK:                     ArraySet
  //
  /// CHECK:                     ArraySet
  /// CHECK:                     ArraySet
  /// CHECK:                     DivZeroCheck
  /// CHECK:                     ArraySet
  //
  /// CHECK:                     ArraySet
  /// CHECK:                     ArraySet
  /// CHECK:                     ArraySet
  /// CHECK-NOT:                 ArraySet

  /// CHECK-START: int Main.$noinline$test40() load_store_elimination (after)
  /// CHECK:                     ArraySet
  /// CHECK:                     DivZeroCheck
  /// CHECK:                     ArraySet
  //
  /// CHECK:                     ArraySet
  /// CHECK:                     DivZeroCheck
  /// CHECK:                     ArraySet
  //
  /// CHECK-NOT:                 ArraySet

  // Like `test40` from 530-checker-lse but with $inline$ for the inner method so we check that we
  // have the array set inside try catches too.
  // Since we are inlining, we know the parameters and are able to elimnate (some) of the
  // `ArraySet`s.
  private static int $noinline$test40() {
    int[] array = new int[1];
    try {
      $inline$fillArrayTest40(array, 100, 0);
      System.out.println("UNREACHABLE");
    } catch (Throwable expected) {
    }
    assertEquals(1, array[0]);
    try {
      $inline$fillArrayTest40(array, 100, 1);
      System.out.println("UNREACHABLE");
    } catch (Throwable expected) {
    }
    assertEquals(2, array[0]);
    $inline$fillArrayTest40(array, 100, 2);
    assertEquals(150, array[0]);
    return array[0];
  }

  /// CHECK-START: void Main.$inline$fillArrayTest40(int[], int, int) load_store_elimination (before)
  /// CHECK:                     ArraySet
  /// CHECK:                     DivZeroCheck
  /// CHECK:                     ArraySet
  /// CHECK:                     DivZeroCheck
  /// CHECK:                     ArraySet
  /// CHECK-NOT:                 ArraySet

  /// CHECK-START: void Main.$inline$fillArrayTest40(int[], int, int) load_store_elimination (after)
  /// CHECK:                     ArraySet
  /// CHECK:                     DivZeroCheck
  /// CHECK:                     ArraySet
  /// CHECK:                     DivZeroCheck
  /// CHECK:                     ArraySet
  /// CHECK-NOT:                 ArraySet

  // Check that the stores to array[0] are not eliminated since we can throw in between the stores.
  private static void $inline$fillArrayTest40(int[] array, int a, int b) {
    array[0] = 1;
    int x = a / b;
    array[0] = 2;
    int y = a / (b - 1);
    array[0] = x + y;
  }

  private static void assertEquals(int expected, int actual) {
    if (expected != actual) {
      throw new AssertionError("Expected: " + expected + ", Actual: " + actual);
    }
  }

  int sumForKeepStoreInsideTryCatch;
}
