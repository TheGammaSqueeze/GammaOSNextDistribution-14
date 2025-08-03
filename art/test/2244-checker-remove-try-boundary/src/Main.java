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
    assertEquals(2, $noinline$testDivideOverTen(20));
    assertEquals(-2, $noinline$testDivideOverTen(-20));
    assertEquals(0, $noinline$testSimpleDivisionInLoop(0));
    assertEquals(1, $noinline$testSimpleDivisionInLoop(81));
    assertEquals(10, $noinline$testOptimizeSeparateBranches(60, true));
    assertEquals(10, $noinline$testOptimizeSeparateBranches(80, false));
    assertEquals(1, $noinline$testDoNotOptimizeOneBranchThrows(81, false));
    assertEquals(-1000, $noinline$testDoNotOptimizeOneBranchThrows(81, true));
    assertEquals(1, $noinline$testOptimizeAfterOneBranchDisappears(81, false));
    assertEquals(10, $noinline$testRemoveTryBoundaryNested(60));
    assertEquals(-2000, $noinline$testRemoveTryBoundaryNestedButNotCatch(60, true));
    assertEquals(30, $noinline$testRemoveTryBoundaryNestedButNotCatch(60, false));
    assertEquals(30, $noinline$testNestedTryBoundariesWithLoopAndCatchOutsideOfLoop(60, false));
  }

  public static void assertEquals(int expected, int result) {
    if (expected != result) {
      throw new Error("Expected: " + expected + ", found: " + result);
    }
  }

  // Check that this version cannot remove the TryBoundary instructions since we may throw.

  /// CHECK-START: int Main.$inline$division(int, int) register (after)
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK-NOT: TryBoundary

  /// CHECK-START: int Main.$inline$division(int, int) register (after)
  /// CHECK:     flags "catch_block"
  private static int $inline$division(int a, int b) {
    try {
      return a / b;
    } catch (Error unexpected) {
      return -1000;
    }
  }

  // Check that we can remove the TryBoundary afer inlining since we know we can't throw.

  /// CHECK-START: int Main.$noinline$testDivideOverTen(int) inliner (after)
  /// CHECK-NOT: TryBoundary

  /// CHECK-START: int Main.$noinline$testDivideOverTen(int) inliner (after)
  /// CHECK-NOT: flags "catch_block"
  private static int $noinline$testDivideOverTen(int a) {
    return $inline$division(a, 10);
  }

  /// CHECK-START: int Main.$noinline$testSimpleDivisionInLoop(int) dead_code_elimination$initial (before)
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK-NOT: TryBoundary

  /// CHECK-START: int Main.$noinline$testSimpleDivisionInLoop(int) dead_code_elimination$initial (before)
  /// CHECK:     flags "catch_block"

  /// CHECK-START: int Main.$noinline$testSimpleDivisionInLoop(int) dead_code_elimination$initial (after)
  /// CHECK-NOT: TryBoundary

  /// CHECK-START: int Main.$noinline$testSimpleDivisionInLoop(int) dead_code_elimination$initial (after)
  /// CHECK-NOT: flags "catch_block"
  private static int $noinline$testSimpleDivisionInLoop(int a) {
    try {
      for (int i = 0; i < 4; i++) {
        a /= 3;
      }
    } catch (Error unexpected) {
      return -1000;
    }
    return a;
  }

  // Even though the `TryBoundary`s are split, we can remove them as nothing in the try can throw.

  /// CHECK-START: int Main.$noinline$testOptimizeSeparateBranches(int, boolean) dead_code_elimination$initial (before)
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK-NOT: TryBoundary

  /// CHECK-START: int Main.$noinline$testOptimizeSeparateBranches(int, boolean) dead_code_elimination$initial (before)
  /// CHECK:     flags "catch_block"
  /// CHECK-NOT: flags "catch_block"

  /// CHECK-START: int Main.$noinline$testOptimizeSeparateBranches(int, boolean) dead_code_elimination$initial (after)
  /// CHECK-NOT: TryBoundary

  /// CHECK-START: int Main.$noinline$testOptimizeSeparateBranches(int, boolean) dead_code_elimination$initial (after)
  /// CHECK-NOT: flags "catch_block"
  private static int $noinline$testOptimizeSeparateBranches(int a, boolean val) {
    try {
      if (val) {
        // TryBoundary kind:entry
        a /= 3;
      } else {
        // TryBoundary kind:entry
        a /= 4;
      }
      a /= 2;
      // TryBoundary kind:exit
    } catch (Error unexpected) {
      return -1000;
    }
    return a;
  }

  // Even though the `a /= 3;` can't throw, we don't eliminate any `TryBoundary` instructions. This
  // is because we have the `throw new Error();` in the try as well. We could potentially support
  // removing some `TryBoundary` instructions and not all in the try, but this would complicate the
  // code and wouldn't bring code size reductions since we would be unable to remove the catch
  // block.

  /// CHECK-START: int Main.$noinline$testDoNotOptimizeOneBranchThrows(int, boolean) register (after)
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK-NOT: TryBoundary

  /// CHECK-START: int Main.$noinline$testDoNotOptimizeOneBranchThrows(int, boolean) register (after)
  /// CHECK:     flags "catch_block"
  public static int $noinline$testDoNotOptimizeOneBranchThrows(int a, boolean val) {
    try {
      for (int i = 0; i < 4; i++) {
        // TryBoundary kind:entry
        a /= 3;
        // TryBoundary kind:exit
      }

      if (val) {
        // TryBoundary kind:entry
        throw new Error();
        // TryBoundary kind:exit
      }
    } catch (Error e) {
      return -1000;
    }
    return a;
  }

  // The throw gets eliminated by `SimplifyIfs` in DCE, so we can detect that nothing can throw in
  // the graph and eliminate the `TryBoundary` instructions.

  /// CHECK-START: int Main.$noinline$testOptimizeAfterOneBranchDisappears(int, boolean) dead_code_elimination$initial (before)
  /// CHECK:     Throw

  /// CHECK-START: int Main.$noinline$testOptimizeAfterOneBranchDisappears(int, boolean) dead_code_elimination$initial (before)
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK-NOT: TryBoundary

  /// CHECK-START: int Main.$noinline$testOptimizeAfterOneBranchDisappears(int, boolean) dead_code_elimination$initial (before)
  /// CHECK:     flags "catch_block"
  /// CHECK-NOT: flags "catch_block"

  /// CHECK-START: int Main.$noinline$testOptimizeAfterOneBranchDisappears(int, boolean) dead_code_elimination$initial (after)
  /// CHECK-NOT: Throw

  /// CHECK-START: int Main.$noinline$testOptimizeAfterOneBranchDisappears(int, boolean) dead_code_elimination$initial (after)
  /// CHECK-NOT: TryBoundary

  /// CHECK-START: int Main.$noinline$testOptimizeAfterOneBranchDisappears(int, boolean) dead_code_elimination$initial (after)
  /// CHECK-NOT: flags "catch_block"
  public static int $noinline$testOptimizeAfterOneBranchDisappears(int a, boolean val) {
    try {
      for (int i = 0; i < 4; i++) {
        // TryBoundary kind:entry
        a /= 3;
        // TryBoundary kind:exit
      }

      if (val && !val) {
        // TryBoundary kind:entry
        throw new Error();
        // TryBoundary kind:exit
      }
    } catch (Error e) {
      return -1000;
    }
    return a;
  }

  /// CHECK-START: int Main.$noinline$testRemoveTryBoundaryNested(int) dead_code_elimination$initial (before)
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK-NOT: TryBoundary

  /// CHECK-START: int Main.$noinline$testRemoveTryBoundaryNested(int) dead_code_elimination$initial (before)
  /// CHECK:     flags "catch_block"
  /// CHECK:     flags "catch_block"
  /// CHECK-NOT: flags "catch_block"

  /// CHECK-START: int Main.$noinline$testRemoveTryBoundaryNested(int) dead_code_elimination$initial (after)
  /// CHECK-NOT: TryBoundary

  /// CHECK-START: int Main.$noinline$testRemoveTryBoundaryNested(int) dead_code_elimination$initial (after)
  /// CHECK-NOT: flags "catch_block"
  public static int $noinline$testRemoveTryBoundaryNested(int a) {
    try {
      // TryBoundary kind:entry
      a /= 2;
      // TryBoundary kind:exit
      try {
        // TryBoundary kind:entry
        a /= 3;
        // TryBoundary kind:exit
      } catch (Error e) {
        return -2000;
      }
    } catch (Exception e) {
      return -1000;
    }
    return a;
  }

  // We can remove the `TryBoundary` instructions surrounding `a /= 2;` but since the inner try can
  // throw, we must keep both the inner and outer catches as they are catch handlers of the inner
  // try.

  /// CHECK-START: int Main.$noinline$testRemoveTryBoundaryNestedButNotCatch(int, boolean) dead_code_elimination$initial (before)
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK-NOT: TryBoundary

  /// CHECK-START: int Main.$noinline$testRemoveTryBoundaryNestedButNotCatch(int, boolean) dead_code_elimination$initial (before)
  /// CHECK:     flags "catch_block"
  /// CHECK:     flags "catch_block"
  /// CHECK-NOT: flags "catch_block"

  /// CHECK-START: int Main.$noinline$testRemoveTryBoundaryNestedButNotCatch(int, boolean) dead_code_elimination$initial (after)
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK-NOT: TryBoundary

  /// CHECK-START: int Main.$noinline$testRemoveTryBoundaryNestedButNotCatch(int, boolean) dead_code_elimination$initial (after)
  /// CHECK:     flags "catch_block"
  /// CHECK:     flags "catch_block"
  /// CHECK-NOT: flags "catch_block"
  public static int $noinline$testRemoveTryBoundaryNestedButNotCatch(int a, boolean val) {
    try {
      // TryBoundary kind:entry
      a /= 2;
      // TryBoundary kind:exit
      try {
        if (val) {
          // TryBoundary kind:entry
          throw new Error();
          // TryBoundary kind:exit
        }
        // TryBoundary kind:exit
      } catch (Error e) {
        return -2000;
      }
    } catch (Exception e) {
      return -1000;
    }
    return a;
  }

  // We eliminate the return -1000 catch block which is outside of the loop in
  // dead_code_elimination$initial. We can do so since we eliminated the TryBoundary of `a /= 2;`.

  /// CHECK-START: int Main.$noinline$testNestedTryBoundariesWithLoopAndCatchOutsideOfLoop(int, boolean) dead_code_elimination$initial (before)
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK-NOT: TryBoundary

  /// CHECK-START: int Main.$noinline$testNestedTryBoundariesWithLoopAndCatchOutsideOfLoop(int, boolean) dead_code_elimination$initial (before)
  /// CHECK:     flags "catch_block"
  /// CHECK:     flags "catch_block"
  /// CHECK:     flags "catch_block"
  /// CHECK-NOT: flags "catch_block"

  /// CHECK-START: int Main.$noinline$testNestedTryBoundariesWithLoopAndCatchOutsideOfLoop(int, boolean) dead_code_elimination$initial (before)
  /// CHECK:     IntConstant -1000

  /// CHECK-START: int Main.$noinline$testNestedTryBoundariesWithLoopAndCatchOutsideOfLoop(int, boolean) dead_code_elimination$initial (after)
  /// CHECK:     TryBoundary
  /// CHECK:     TryBoundary
  /// CHECK-NOT: TryBoundary

  /// CHECK-START: int Main.$noinline$testNestedTryBoundariesWithLoopAndCatchOutsideOfLoop(int, boolean) dead_code_elimination$initial (after)
  /// CHECK:     flags "catch_block"
  /// CHECK:     flags "catch_block"
  /// CHECK-NOT: flags "catch_block"

  /// CHECK-START: int Main.$noinline$testNestedTryBoundariesWithLoopAndCatchOutsideOfLoop(int, boolean) dead_code_elimination$initial (after)
  /// CHECK-NOT: IntConstant -1000

  // When removing that block, we are removing a block outside of a loop but we still need to update
  // the loop information in the graph since we removed TryBoundary instructions inside of a loop
  // and now `a /= 2;` is not considered part of a loop (Cannot throw so it will not `continue` and
  // will always return).

  /// CHECK-START: int Main.$noinline$testNestedTryBoundariesWithLoopAndCatchOutsideOfLoop(int, boolean) dead_code_elimination$initial (before)
  /// CHECK:     Div loop:B2

  /// CHECK-START: int Main.$noinline$testNestedTryBoundariesWithLoopAndCatchOutsideOfLoop(int, boolean) dead_code_elimination$initial (after)
  /// CHECK-NOT:  Div loop:B2

  /// CHECK-START: int Main.$noinline$testNestedTryBoundariesWithLoopAndCatchOutsideOfLoop(int, boolean) dead_code_elimination$initial (after)
  /// CHECK:      Div
  /// CHECK-NOT:  Div
  public static int $noinline$testNestedTryBoundariesWithLoopAndCatchOutsideOfLoop(
          int a, boolean val) {
    try {
      for (int i = 0; i < 4; ++i) {
        try {
          try {
            if (val) {
              // TryBoundary kind:entry
              throw new Error();
              // TryBoundary kind:exit
            }
            // TryBoundary kind:exit
          } catch (Exception e) {
              continue;
          }
          // TryBoundary kind:entry
          a /= 2;
          // TryBoundary kind:exit
          return a;
        } catch (Error e) {
          continue;
        }
      }
    } catch (Exception e) {
      return -1000;
    }
    return a;
  }
}
