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
    // Basic test
    assertEquals(0, $noinline$testSimplifyThrow(1));

    // Basic test for non-trivial blocks (i.e. not just an invoke and a Goto)
    assertEquals(0, $noinline$testSimplifyThrowAndPrint(1));
    assertEquals(0, $noinline$testSimplifyTwoThrows(1));
    assertEquals(0, $noinline$testSimplifyWithArgument(1));

    // Try catch tests
    assertEquals(0, $noinline$testDoNotSimplifyInTry(1));
    assertEquals(0, $noinline$testSimplifyInCatch(1));
    assertEquals(0, $noinline$testDoNotSimplifyInCatchInOuterTry(1, 1));

    // Test that we update the phis correctly after simplifying an always throwing method, and
    // recomputing dominance.
    assertEquals(0, $noinline$testUpdatePhisCorrectly(1));
    assertEquals(0, $noinline$testDeleteAllUsesBeforeDeletingInstruction(1));

    // SimplifyAlwaysThrows for blocks without a goto at the end
    assertEquals(0, $noinline$testEndsWithIf(1, 1));
    assertEquals(0, $noinline$testEndsWithReturn(1));
    // Since we cannot use `assertEquals`, not throwing would be the success.
    $noinline$testEndsWithReturnVoid(1);
    assertEquals(0, $noinline$testEndsWithSwitch(1, 1));
    assertEquals(0, $noinline$testEndsWithThrow(1));
    assertEquals(0, $noinline$testEndsWithTryBoundary(1));

    // SimplifyAlwaysThrows for invokes in catch blocks
    assertEquals(0, $noinline$testInsideCatch(1));
  }

  private static void alwaysThrows() throws Error {
    throw new Error("");
  }

  /// CHECK-START: int Main.$noinline$testSimplifyThrow(int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<TargetBlock:B\d+>>
  /// CHECK-EVAL:  "<<ExitBlock>>" != "<<TargetBlock>>"

  /// CHECK-START: int Main.$noinline$testSimplifyThrow(int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<ExitBlock>>

  // Tests that we simplify the always throwing branch directly to the exit.
  private static int $noinline$testSimplifyThrow(int num) {
    if (num == 0) {
      alwaysThrows();
    }
    return 0;
  }

  /// CHECK-START: int Main.$noinline$testSimplifyThrowAndPrint(int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   InvokeVirtual method_name:java.io.PrintStream.println
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<TargetBlock:B\d+>>
  /// CHECK-EVAL:  "<<ExitBlock>>" != "<<TargetBlock>>"

  /// CHECK-START: int Main.$noinline$testSimplifyThrowAndPrint(int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<ExitBlock>>

  /// CHECK-START: int Main.$noinline$testSimplifyThrowAndPrint(int) dead_code_elimination$after_inlining (after)
  /// CHECK-NOT:   InvokeVirtual method_name:java.io.PrintStream.println
  private static int $noinline$testSimplifyThrowAndPrint(int num) {
    if (num == 0) {
      alwaysThrows();
      System.out.println("I am unrechable!");
    }
    return 0;
  }

  /// CHECK-START: int Main.$noinline$testSimplifyTwoThrows(int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<TargetBlock:B\d+>>
  /// CHECK-EVAL:  "<<ExitBlock>>" != "<<TargetBlock>>"

  /// CHECK-START: int Main.$noinline$testSimplifyTwoThrows(int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<ExitBlock>>

  // Check that the second `alwaysThrows` gets removed.
  /// CHECK-START: int Main.$noinline$testSimplifyTwoThrows(int) dead_code_elimination$after_inlining (after)
  /// CHECK:       InvokeStaticOrDirect method_name:Main.alwaysThrows always_throws:true
  /// CHECK-NOT:   InvokeStaticOrDirect method_name:Main.alwaysThrows always_throws:true

  // Tests that we simplify the always throwing branch directly to the exit, even with blocks that
  // are not just the throwing instruction and a Goto.
  private static int $noinline$testSimplifyTwoThrows(int num) {
    if (num == 0) {
      alwaysThrows();
      alwaysThrows();
    }
    return 0;
  }

  private static int throwIfZero(int num) {
    if (num == 0) {
      throw new Error("num is 0!");
    }
    return num / num;
  }

  /// CHECK-START: int Main.$noinline$testSimplifyWithArgument(int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.throwIfZero always_throws:true
  /// CHECK-DAG:   InvokeVirtual method_name:java.io.PrintStream.println
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<TargetBlock:B\d+>>
  /// CHECK-EVAL:  "<<ExitBlock>>" != "<<TargetBlock>>"

  /// CHECK-START: int Main.$noinline$testSimplifyWithArgument(int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.throwIfZero always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<ExitBlock>>

  /// CHECK-START: int Main.$noinline$testSimplifyWithArgument(int) dead_code_elimination$after_inlining (after)
  /// CHECK-NOT:   InvokeVirtual method_name:java.io.PrintStream.println
  private static int $noinline$testSimplifyWithArgument(int num) {
    if (num == 0) {
      throwIfZero(0);
      System.out.println("I am unrechable!");
    }
    return 0;
  }

  /// CHECK-START: int Main.$noinline$testSimplifyThrowWithTryCatch(int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<TargetBlock:B\d+>>
  /// CHECK-EVAL:  "<<ExitBlock>>" != "<<TargetBlock>>"

  /// CHECK-START: int Main.$noinline$testSimplifyThrowWithTryCatch(int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<ExitBlock>>

  // Consistency check to make sure we have the try catches in the graph at this stage.
  /// CHECK-START: int Main.$noinline$testSimplifyThrowWithTryCatch(int) dead_code_elimination$after_inlining (before)
  /// CHECK:       TryBoundary kind:entry
  /// CHECK:       TryBoundary kind:entry

  // Tests that we simplify the always throwing branch directly to the exit, with non-blocking try
  // catches in the graph.
  private static int $noinline$testSimplifyThrowWithTryCatch(int num) {
    try {
      if (num == 123) {
        throw new Error();
      }
    } catch (Error e) {
      return 123;
    }

    if (num == 0) {
      alwaysThrows();
    }

    try {
      if (num == 456) {
        throw new Error();
      }
    } catch (Error e) {
      return 456;
    }
    return 0;
  }

  private static void $inline$testDoNotSimplifyInner(int num) {
    alwaysThrows();
    while (num == 0) {
      // We should never hit this since we are always throwing.
      System.out.println(num);
    }
  }

  /// CHECK-START: int Main.$noinline$testDoNotSimplifyInTry(int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<TargetBlock:B\d+>>
  /// CHECK-EVAL:  "<<ExitBlock>>" != "<<TargetBlock>>"

  /// CHECK-START: int Main.$noinline$testDoNotSimplifyInTry(int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<TargetBlock:B\d+>>
  /// CHECK-EVAL:  "<<ExitBlock>>" != "<<TargetBlock>>"

  // Consistency check to make sure we have the try catch in the graph at this stage.
  /// CHECK-START: int Main.$noinline$testDoNotSimplifyInTry(int) dead_code_elimination$after_inlining (before)
  /// CHECK:       TryBoundary kind:entry

  // Consistency check to that we do not simplify it by the last DCE pass either
  /// CHECK-START: int Main.$noinline$testDoNotSimplifyInTry(int) dead_code_elimination$before_codegen (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<TargetBlock:B\d+>>
  /// CHECK-EVAL:  "<<ExitBlock>>" != "<<TargetBlock>>"

  // Tests that we have the necessary conditions for us to simplify the always throwing instruction
  // (e.g. InvokeStaticOrDirect followed by a Goto) but we are blocking this due to being in a try.
  // Changing the Goto here for the exit would be wrong since we want to flow to the catch rather
  // than the Exit. The preconditions are tricky to do with just one function (since we will have an
  // invoke followed by a TryBoundary rather than a Goto) but we can do so with the help of the
  // inliner.
  private static int $noinline$testDoNotSimplifyInTry(int num) {
    try {
      $inline$testDoNotSimplifyInner(num);
    } catch (Error e) {
      return 0;
    }
    return 123;
  }

  /// CHECK-START: int Main.$noinline$testSimplifyInCatch(int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<TargetBlock:B\d+>>
  /// CHECK-EVAL:  "<<ExitBlock>>" != "<<TargetBlock>>"

  /// CHECK-START: int Main.$noinline$testSimplifyInCatch(int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<ExitBlock>>

  // Consistency check to make sure we have the try catch in the graph at this stage.
  /// CHECK-START: int Main.$noinline$testSimplifyInCatch(int) dead_code_elimination$after_inlining (before)
  /// CHECK:       TryBoundary kind:entry

  // We are able to simplify the `alwaysThrows` even though we are inside of the catch { ... } since
  // the if makes it so that we are not the first block of the catch and therefore not in the
  // "catch_block".
  private static int $noinline$testSimplifyInCatch(int num) {
    try {
      throw new Error();
    } catch (Error e) {
      if (num == 0) {
        alwaysThrows();
      }
      return 0;
    }
  }

  /// CHECK-START: int Main.$noinline$testDoNotSimplifyInCatchInOuterTry(int, int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<TargetBlock:B\d+>>
  /// CHECK-EVAL:  "<<ExitBlock>>" != "<<TargetBlock>>"

  /// CHECK-START: int Main.$noinline$testDoNotSimplifyInCatchInOuterTry(int, int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<TargetBlock:B\d+>>
  /// CHECK-EVAL:  "<<ExitBlock>>" != "<<TargetBlock>>"

  // Consistency check to make sure we have the try catches in the graph at this stage.
  /// CHECK-START: int Main.$noinline$testDoNotSimplifyInCatchInOuterTry(int, int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   TryBoundary kind:entry
  /// CHECK-DAG:   TryBoundary kind:entry

  // Consistency check to that we do not simplify it by the last DCE pass either
  /// CHECK-START: int Main.$noinline$testDoNotSimplifyInCatchInOuterTry(int, int) dead_code_elimination$before_codegen (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<TargetBlock:B\d+>>
  /// CHECK-EVAL:  "<<ExitBlock>>" != "<<TargetBlock>>"

  // Similar to testSimplifyInCatch, but now the throw is in an outer try and we shouldn't simplify
  // it. Like in testDoNotSimplifyInTry, we need the help of the inliner to have an invoke followed
  // by a Goto.
  private static int $noinline$testDoNotSimplifyInCatchInOuterTry(int num, int other_num) {
    try {
      try {
        throw new Error();
      } catch (Error e) {
        if (num == 0) {
          // We use `other_num` here because otherwise we propagate the knowledge that `num` equals
          // zero.
          $inline$testDoNotSimplifyInner(other_num);
        }
        return 0;
      }
    } catch (Error e) {
      return 123;
    }
  }

  // Check that when we perform SimplifyAlwaysThrows, that the phi for `phi_value` exists, and that
  // we correctly update it after running DCE.

  /// CHECK-START: int Main.$noinline$testUpdatePhisCorrectly(int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   <<Const0:i\d+>> IntConstant 0
  /// CHECK-DAG:   <<Const5:i\d+>> IntConstant 5
  /// CHECK-DAG:   <<ReturnValue:i\d+>> Phi [<<Const0>>,<<Const5>>]
  /// CHECK-DAG:   Return [<<ReturnValue>>]
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<TargetBlock:B\d+>>
  /// CHECK-EVAL:  "<<ExitBlock>>" != "<<TargetBlock>>"

  /// CHECK-START: int Main.$noinline$testUpdatePhisCorrectly(int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   <<Const0:i\d+>> IntConstant 0
  /// CHECK-DAG:   Return [<<Const0>>]
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<ExitBlock>>

  /// CHECK-START: int Main.$noinline$testUpdatePhisCorrectly(int) dead_code_elimination$after_inlining (after)
  /// CHECK-NOT:   Phi
  private static int $noinline$testUpdatePhisCorrectly(int num) {
    int phi_value = 0;
    if (num == 0) {
      alwaysThrows();
      phi_value = 5;
    }
    return phi_value;
  }

  // Test to check that we delete all uses before the instruction.
  private static int $noinline$foo(int num) {
    return num;
  }

  /// CHECK-START: int Main.$noinline$testDeleteAllUsesBeforeDeletingInstruction(int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   <<Const0:i\d+>> IntConstant 0
  /// CHECK-DAG:   <<Invoke:i\d+>> InvokeStaticOrDirect method_name:Main.$noinline$foo
  /// CHECK-DAG:   <<ReturnValue:i\d+>> Phi [<<Const0>>,<<Invoke>>]
  /// CHECK-DAG:   Return [<<ReturnValue>>]
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   TryBoundary block:<<InvokeBlock>>
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>

  /// CHECK-START: int Main.$noinline$testDeleteAllUsesBeforeDeletingInstruction(int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   <<Const0:i\d+>> IntConstant 0
  /// CHECK-DAG:   Return [<<Const0>>]
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<ExitBlock>>

  /// CHECK-START: int Main.$noinline$testDeleteAllUsesBeforeDeletingInstruction(int) dead_code_elimination$after_inlining (after)
  /// CHECK-NOT:   Phi
  private static int $noinline$testDeleteAllUsesBeforeDeletingInstruction(int num) {
    int phi_value = 0;
    if (num == 0) {
      alwaysThrows();
      try {
        phi_value = $noinline$foo(2);
      } catch (Error e) {
        throw new Error("We shouldn't hit this");
      }
    }
    return phi_value;
  }

  /// CHECK-START: int Main.$noinline$testEndsWithIf(int, int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   If block:<<InvokeBlock>>
  /// CHECK-DAG:   InvokeVirtual method_name:java.io.PrintStream.println
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>

  /// CHECK-START: int Main.$noinline$testEndsWithIf(int, int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<ExitBlock>>

  /// CHECK-START: int Main.$noinline$testEndsWithIf(int, int) dead_code_elimination$after_inlining (after)
  /// CHECK-NOT:   InvokeVirtual method_name:java.io.PrintStream.println
  private static int $noinline$testEndsWithIf(int num, int other_num) {
    if (num == 0) {
      alwaysThrows();
      if (other_num == 0) {
        System.out.println("I am unrechable!");
      }
    }
    return 0;
  }

  /// CHECK-START: int Main.$noinline$testEndsWithReturn(int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Return block:<<InvokeBlock>>
  /// CHECK-DAG:   InvokeVirtual method_name:java.io.PrintStream.println
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>

  /// CHECK-START: int Main.$noinline$testEndsWithReturn(int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<ExitBlock>>

  /// CHECK-START: int Main.$noinline$testEndsWithReturn(int) dead_code_elimination$after_inlining (after)
  /// CHECK-NOT:   InvokeVirtual method_name:java.io.PrintStream.println
  private static int $noinline$testEndsWithReturn(int num) {
    if (num == 0) {
      alwaysThrows();
      System.out.println("I am unrechable!");
      return 1;
    }
    return 0;
  }

  /// CHECK-START: void Main.$noinline$testEndsWithReturnVoid(int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   ReturnVoid block:<<InvokeBlock>>
  /// CHECK-DAG:   InvokeVirtual method_name:java.io.PrintStream.println
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>

  /// CHECK-START: void Main.$noinline$testEndsWithReturnVoid(int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<ExitBlock>>

  /// CHECK-START: void Main.$noinline$testEndsWithReturnVoid(int) dead_code_elimination$after_inlining (after)
  /// CHECK-NOT:   InvokeVirtual method_name:java.io.PrintStream.println
  private static void $noinline$testEndsWithReturnVoid(int num) {
    if (num == 0) {
      alwaysThrows();
      System.out.println("I am unrechable!");
      return;
    }
    return;
  }

  /// CHECK-START: int Main.$noinline$testEndsWithSwitch(int, int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   PackedSwitch block:<<InvokeBlock>>
  /// CHECK-DAG:   InvokeVirtual method_name:java.io.PrintStream.println
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>

  /// CHECK-START: int Main.$noinline$testEndsWithSwitch(int, int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<ExitBlock>>

  /// CHECK-START: int Main.$noinline$testEndsWithSwitch(int, int) dead_code_elimination$after_inlining (after)
  /// CHECK-NOT:   InvokeVirtual method_name:java.io.PrintStream.println
  private static int $noinline$testEndsWithSwitch(int num, int other_num) {
    if (num == 0) {
      alwaysThrows();
      System.out.println("I am unrechable!");
      int result = 10;
      switch (other_num) {
        case 1:
          result = 100;
          break;
        case 2:
          result = 300;
          break;
        case 3:
          result = 500;
          break;
        case 4:
          result = 700;
          break;
      }
      return result;
    }
    return 0;
  }

  /// CHECK-START: int Main.$noinline$testEndsWithThrow(int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Throw block:<<InvokeBlock>>
  /// CHECK-DAG:   InvokeVirtual method_name:java.io.PrintStream.println
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>

  /// CHECK-START: int Main.$noinline$testEndsWithThrow(int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<ExitBlock>>

  /// CHECK-START: int Main.$noinline$testEndsWithThrow(int) dead_code_elimination$after_inlining (after)
  /// CHECK-NOT:   InvokeVirtual method_name:java.io.PrintStream.println
  private static int $noinline$testEndsWithThrow(int num) {
    if (num == 0) {
      alwaysThrows();
      System.out.println("I am unrechable!");
      throw new Error("Other error");
    }
    return 0;
  }

  /// CHECK-START: int Main.$noinline$testEndsWithTryBoundary(int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   TryBoundary block:<<InvokeBlock>>
  /// CHECK-DAG:   InvokeVirtual method_name:java.io.PrintStream.println
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>

  /// CHECK-START: int Main.$noinline$testEndsWithTryBoundary(int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<ExitBlock>>

  /// CHECK-START: int Main.$noinline$testEndsWithTryBoundary(int) dead_code_elimination$after_inlining (after)
  /// CHECK-NOT:   InvokeVirtual method_name:java.io.PrintStream.println
  private static int $noinline$testEndsWithTryBoundary(int num) {
    if (num == 0) {
      alwaysThrows();
      System.out.println("I am unrechable!");
      try {
        alwaysThrows();
      } catch (Error e) {
        return 1;
      }
    }
    return 0;
  }

  // Empty method to forbid the try from disappearing
  private static void $noinline$emptyMethod() {}

  /// CHECK-START: int Main.$noinline$testInsideCatch(int) dead_code_elimination$after_inlining (before)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Return block:<<InvokeBlock>>
  /// CHECK-DAG:   InvokeVirtual method_name:java.io.PrintStream.println
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>

  /// CHECK-START: int Main.$noinline$testInsideCatch(int) dead_code_elimination$after_inlining (after)
  /// CHECK-DAG:   InvokeStaticOrDirect block:<<InvokeBlock:B\d+>> method_name:Main.alwaysThrows always_throws:true
  /// CHECK-DAG:   Exit block:<<ExitBlock:B\d+>>
  /// CHECK-DAG:   Goto block:<<InvokeBlock>> target:<<ExitBlock>>

  /// CHECK-START: int Main.$noinline$testInsideCatch(int) dead_code_elimination$after_inlining (after)
  /// CHECK-NOT:   InvokeVirtual method_name:java.io.PrintStream.println
  private static int $noinline$testInsideCatch(int num) {
    if (num == 0) {
      try {
        $noinline$emptyMethod();
      } catch (Error e) {
        alwaysThrows();
        System.out.println("I am unrechable!");
        return 1;
      }
    }
    return 0;
  }

  static void assertEquals(int expected, int actual) {
    if (expected != actual) {
      throw new AssertionError("Expected " + expected + " got " + actual);
    }
  }
}
