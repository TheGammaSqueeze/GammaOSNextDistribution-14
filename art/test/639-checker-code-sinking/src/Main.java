/*
 * Copyright (C) 2017 The Android Open Source Project
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
  static class ValueHolder {
    int getValue() {
      // Prevent inliner from matching the code pattern when calling this method to test
      // the normal inlining path that does not inline in blocks that end with a `throw`.
      $inline$nop();

      return 1;
    }

    private void $inline$nop() {}
  }

  public static void main(String[] args) throws Exception {
    testSimpleUse();
    testTwoUses();
    testFieldStores(doThrow);
    testFieldStoreCycle();
    testArrayStores();
    testOnlyStoreUses();
    testNoUse();
    testPhiInput();
    testVolatileStore();
    testCatchBlock();
    $noinline$testTwoThrowingPathsAndStringBuilderAppend();
    try {
      $noinline$testSinkNewInstanceWithClinitCheck();
      throw new Exception("Unreachable");
    } catch (Error e) {
      // expected
    }
    $noinline$testMethodEndsWithTryBoundary();
    doThrow = true;
    try {
      testInstanceSideEffects();
    } catch (Error e) {
      // expected
      System.out.println(e.getMessage());
    }
    try {
      testStaticSideEffects();
    } catch (Error e) {
      // expected
      System.out.println(e.getMessage());
    }

    try {
      testStoreStore(doThrow);
    } catch (Error e) {
      // expected
      System.out.println(e.getMessage());
    }
  }

  /// CHECK-START: void Main.testSimpleUse() code_sinking (before)
  /// CHECK: <<LoadClass:l\d+>> LoadClass class_name:java.lang.Object
  /// CHECK: <<New:l\d+>>       NewInstance [<<LoadClass>>]
  /// CHECK:                    ConstructorFence [<<New>>]
  /// CHECK:                    If
  /// CHECK:                    begin_block
  /// CHECK:                    Throw

  /// CHECK-START: void Main.testSimpleUse() code_sinking (after)
  /// CHECK-NOT:                NewInstance
  /// CHECK:                    If
  /// CHECK:                    begin_block
  /// CHECK: <<Error:l\d+>>     LoadClass class_name:java.lang.Error
  /// CHECK: <<LoadClass:l\d+>> LoadClass class_name:java.lang.Object
  /// CHECK-NOT:                begin_block
  /// CHECK: <<New:l\d+>>       NewInstance [<<LoadClass>>]
  /// CHECK:                    ConstructorFence [<<New>>]
  /// CHECK-NOT:                begin_block
  /// CHECK:                    NewInstance [<<Error>>]
  /// CHECK:                    Throw
  public static void testSimpleUse() {
    Object o = new Object();
    if (doThrow) {
      throw new Error(o.toString());
    }
  }

  /// CHECK-START: void Main.testTwoUses() code_sinking (before)
  /// CHECK: <<LoadClass:l\d+>> LoadClass class_name:java.lang.Object
  /// CHECK:                    NewInstance [<<LoadClass>>]
  /// CHECK:                    If
  /// CHECK:                    begin_block
  /// CHECK:                    Throw

  /// CHECK-START: void Main.testTwoUses() code_sinking (after)
  /// CHECK-NOT:                NewInstance
  /// CHECK:                    If
  /// CHECK:                    begin_block
  /// CHECK: <<Error:l\d+>>     LoadClass class_name:java.lang.Error
  /// CHECK: <<LoadClass:l\d+>> LoadClass class_name:java.lang.Object
  /// CHECK-NOT:                begin_block
  /// CHECK:                    NewInstance [<<LoadClass>>]
  /// CHECK-NOT:                begin_block
  /// CHECK:                    NewInstance [<<Error>>]
  /// CHECK:                    Throw
  public static void testTwoUses() {
    Object o = new Object();
    if (doThrow) {
      throw new Error(o.toString() + o.toString());
    }
  }

  // NB It might seem that we'd move the allocation and ifield-set but those are
  // already moved into the throw block by a combo of partial-LSE and DCE.
  // Instead all that is actually moved is the LoadClass. Also note the
  // LoadClass can only be moved since it refers to the 'Main' class itself,
  // meaning there's no need for any clinit/actual loading.
  //
  /// CHECK-START: void Main.testFieldStores(boolean) code_sinking (before)
  /// CHECK: <<Int42:i\d+>>       IntConstant 42
  /// CHECK:                      begin_block
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:Main
  /// CHECK: <<NewInstance:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK:                      InstanceFieldSet [<<NewInstance>>,<<Int42>>]
  /// CHECK:                      Throw

  /// CHECK-START: void Main.testFieldStores(boolean) code_sinking (after)
  /// CHECK: <<Int42:i\d+>>       IntConstant 42
  /// CHECK-NOT:                  NewInstance
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK: <<Error:l\d+>>       LoadClass class_name:java.lang.Error
  /// CHECK-NOT:                  begin_block
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:Main
  /// CHECK-NOT:                  begin_block
  /// CHECK: <<NewInstance:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK-NOT:                  begin_block
  /// CHECK:                      InstanceFieldSet [<<NewInstance>>,<<Int42>>]
  /// CHECK-NOT:                  begin_block
  /// CHECK: <<Throw:l\d+>>       NewInstance [<<Error>>]
  /// CHECK-NOT:                  begin_block
  /// CHECK:                      Throw [<<Throw>>]
  public static void testFieldStores(boolean doThrow) {
    Main m = new Main();
    m.intField = 42;
    if (doThrow) {
      throw new Error(m.toString());
    }
  }

  /// CHECK-START: void Main.testFieldStoreCycle() code_sinking (before)
  /// CHECK: <<LoadClass:l\d+>>    LoadClass class_name:Main
  /// CHECK: <<NewInstance1:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK: <<NewInstance2:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK:                       InstanceFieldSet [<<NewInstance1>>,<<NewInstance2>>]
  /// CHECK:                       InstanceFieldSet [<<NewInstance2>>,<<NewInstance1>>]
  /// CHECK:                       If
  /// CHECK:                       begin_block
  /// CHECK:                       Throw

  // TODO(ngeoffray): Handle allocation/store cycles.
  /// CHECK-START: void Main.testFieldStoreCycle() code_sinking (after)
  /// CHECK: begin_block
  /// CHECK: <<LoadClass:l\d+>>    LoadClass class_name:Main
  /// CHECK: <<NewInstance1:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK: <<NewInstance2:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK:                       InstanceFieldSet [<<NewInstance1>>,<<NewInstance2>>]
  /// CHECK:                       InstanceFieldSet [<<NewInstance2>>,<<NewInstance1>>]
  /// CHECK:                       If
  /// CHECK:                       begin_block
  /// CHECK:                       Throw
  public static void testFieldStoreCycle() {
    Main m1 = new Main();
    Main m2 = new Main();
    m1.objectField = m2;
    m2.objectField = m1;
    if (doThrow) {
      throw new Error(m1.toString() + m2.toString());
    }
  }

  /// CHECK-START: void Main.testArrayStores() code_sinking (before)
  /// CHECK: <<Int1:i\d+>>        IntConstant 1
  /// CHECK: <<Int0:i\d+>>        IntConstant 0
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:java.lang.Object[]
  /// CHECK: <<NewArray:l\d+>>    NewArray [<<LoadClass>>,<<Int1>>]
  /// CHECK:                      ArraySet [<<NewArray>>,<<Int0>>,<<NewArray>>]
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK:                      Throw

  /// CHECK-START: void Main.testArrayStores() code_sinking (after)
  /// CHECK: <<Int1:i\d+>>        IntConstant 1
  /// CHECK: <<Int0:i\d+>>        IntConstant 0
  /// CHECK-NOT:                  NewArray
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK: <<Error:l\d+>>       LoadClass class_name:java.lang.Error
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:java.lang.Object[]
  /// CHECK-NOT:                  begin_block
  /// CHECK: <<NewArray:l\d+>>    NewArray [<<LoadClass>>,<<Int1>>]
  /// CHECK-NOT:                  begin_block
  /// CHECK:                      ArraySet [<<NewArray>>,<<Int0>>,<<NewArray>>]
  /// CHECK-NOT:                  begin_block
  /// CHECK:                      NewInstance [<<Error>>]
  /// CHECK:                      Throw
  public static void testArrayStores() {
    Object[] o = new Object[1];
    o[0] = o;
    if (doThrow) {
      throw new Error(o.toString());
    }
  }

  // Make sure code sinking does not crash on dead allocations.
  public static void testOnlyStoreUses() {
    Main m = new Main();
    Object[] o = new Object[1];  // dead allocation, should eventually be removed b/35634932.
    o[0] = m;
    o = null;  // Avoid environment uses for the array allocation.
    if (doThrow) {
      throw new Error(m.toString());
    }
  }

  // Make sure code sinking does not crash on dead code.
  public static void testNoUse() {
    Main m = new Main();
    boolean load = Main.doLoop;  // dead code, not removed because of environment use.
    // Ensure one environment use for the static field
    $opt$noinline$foo();
    load = false;
    if (doThrow) {
      throw new Error(m.toString());
    }
  }

  // Make sure we can move code only used by a phi.
  /// CHECK-START: void Main.testPhiInput() code_sinking (before)
  /// CHECK: <<Null:l\d+>>        NullConstant
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK: <<NewInstance:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK:                      Phi [<<Null>>,<<NewInstance>>]
  /// CHECK:                      Throw

  /// CHECK-START: void Main.testPhiInput() code_sinking (after)
  /// CHECK: <<Null:l\d+>>        NullConstant
  /// CHECK-NOT:                  NewInstance
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK: <<NewInstance:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK:                      begin_block
  /// CHECK:                      Phi [<<Null>>,<<NewInstance>>]
  /// CHECK: <<Error:l\d+>>       LoadClass class_name:java.lang.Error
  /// CHECK:                      NewInstance [<<Error>>]
  /// CHECK:                      Throw
  public static void testPhiInput() {
    Object f = new Object();
    if (doThrow) {
      Object o = null;
      int i = 2;
      if (doLoop) {
        o = f;
        i = 42;
      }
      throw new Error(o.toString() + i);
    }
  }

  static void $opt$noinline$foo() {}

  // Check that we do not move volatile stores.
  /// CHECK-START: void Main.testVolatileStore() code_sinking (before)
  /// CHECK: <<Int42:i\d+>>        IntConstant 42
  /// CHECK: <<LoadClass:l\d+>>    LoadClass class_name:Main
  /// CHECK: <<NewInstance:l\d+>>  NewInstance [<<LoadClass>>]
  /// CHECK:                       InstanceFieldSet [<<NewInstance>>,<<Int42>>]
  /// CHECK:                       If
  /// CHECK:                       begin_block
  /// CHECK:                       Throw

  /// CHECK-START: void Main.testVolatileStore() code_sinking (after)
  /// CHECK: <<Int42:i\d+>>        IntConstant 42
  /// CHECK: <<LoadClass:l\d+>>    LoadClass class_name:Main
  /// CHECK: <<NewInstance:l\d+>>  NewInstance [<<LoadClass>>]
  /// CHECK:                       InstanceFieldSet [<<NewInstance>>,<<Int42>>]
  /// CHECK:                       If
  /// CHECK:                       begin_block
  /// CHECK:                       Throw
  public static void testVolatileStore() {
    Main m = new Main();
    m.volatileField = 42;
    if (doThrow) {
      throw new Error(m.toString());
    }
  }

  private static void $noinline$testMethodEndsWithTryBoundary() throws Exception {
    assertEquals(0, $noinline$testDontSinkToReturnBranch(0, 0, false, new Object()));
    assertEquals(1, $noinline$testSinkToThrowBranch(0, 0, true, new Object()));
    try {
      $noinline$testSinkToThrowBranch(0, 0, false, new Object());
      throw new Exception("Unreachable");
    } catch (Error expected) {
    }
  }

  // Consistency check: only one add
  /// CHECK-START: int Main.$noinline$testDontSinkToReturnBranch(int, int, boolean, java.lang.Object) code_sinking (before)
  /// CHECK:     Add
  /// CHECK-NOT: Add

  /// CHECK-START: int Main.$noinline$testDontSinkToReturnBranch(int, int, boolean, java.lang.Object) code_sinking (before)
  /// CHECK:      Add
  /// CHECK-NEXT: If

  /// CHECK-START: int Main.$noinline$testDontSinkToReturnBranch(int, int, boolean, java.lang.Object) code_sinking (after)
  /// CHECK:      Add
  /// CHECK-NEXT: If
  private static int $noinline$testDontSinkToReturnBranch(int a, int b, boolean flag, Object obj) {
    int c = a + b;
    if (flag) {
      return 1;
    }

    synchronized (obj) {
      return $noinline$returnSameValue(c);
    }
  }

  private static int $noinline$returnSameValue(int value) {
    return value;
  }

  // Consistency check: only one add
  /// CHECK-START: int Main.$noinline$testSinkToThrowBranch(int, int, boolean, java.lang.Object) code_sinking (before)
  /// CHECK:     Add
  /// CHECK-NOT: Add

  /// CHECK-START: int Main.$noinline$testSinkToThrowBranch(int, int, boolean, java.lang.Object) code_sinking (before)
  /// CHECK:      Add
  /// CHECK:      If

  /// CHECK-START: int Main.$noinline$testSinkToThrowBranch(int, int, boolean, java.lang.Object) code_sinking (after)
  /// CHECK:      If
  /// CHECK:      Add
  private static int $noinline$testSinkToThrowBranch(int a, int b, boolean flag, Object obj) {
    int c = a + b;
    if (flag) {
      return 1;
    }

    synchronized (obj) {
      throw new Error(Integer.toString(c));
    }
  }

  public static void testInstanceSideEffects() {
    int a = mainField.intField;
    $noinline$changeIntField();
    if (doThrow) {
      throw new Error("" + a);
    }
  }

  static void $noinline$changeIntField() {
    mainField.intField = 42;
  }

  public static void testStaticSideEffects() {
    Object o = obj;
    $noinline$changeStaticObjectField();
    if (doThrow) {
      throw new Error(o.getClass().toString());
    }
  }

  static void $noinline$changeStaticObjectField() {
    obj = new Main();
  }

  // Test that we preserve the order of stores.
  // NB It might seem that we'd move the allocation and ifield-set but those are
  // already moved into the throw block by a combo of partial-LSE and DCE.
  // Instead all that is actually moved is the LoadClass. Also note the
  // LoadClass can only be moved since it refers to the 'Main' class itself,
  // meaning there's no need for any clinit/actual loading.
  //
  /// CHECK-START: void Main.testStoreStore(boolean) code_sinking (before)
  /// CHECK: <<Int42:i\d+>>       IntConstant 42
  /// CHECK: <<Int43:i\d+>>       IntConstant 43
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:Main
  /// CHECK: <<NewInstance:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK-DAG:                  InstanceFieldSet [<<NewInstance>>,<<Int42>>]
  /// CHECK-DAG:                  InstanceFieldSet [<<NewInstance>>,<<Int43>>]
  /// CHECK:                      Throw
  /// CHECK-NOT:                  InstanceFieldSet

  /// CHECK-START: void Main.testStoreStore(boolean) code_sinking (after)
  /// CHECK: <<Int42:i\d+>>       IntConstant 42
  /// CHECK: <<Int43:i\d+>>       IntConstant 43
  /// CHECK-NOT:                  NewInstance
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK: <<Error:l\d+>>       LoadClass class_name:java.lang.Error
  /// CHECK-NOT:                  begin_block
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:Main
  /// CHECK: <<NewInstance:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK-NOT:                  begin_block
  /// CHECK-DAG:                  InstanceFieldSet [<<NewInstance>>,<<Int42>>]
  /// CHECK-DAG:                  InstanceFieldSet [<<NewInstance>>,<<Int43>>]
  /// CHECK-NOT:                  begin_block
  /// CHECK:                      NewInstance [<<Error>>]
  /// CHECK:                      Throw
  /// CHECK-NOT:                  InstanceFieldSet
  public static void testStoreStore(boolean doThrow) {
    Main m = new Main();
    m.intField = 42;
    m.intField2 = 43;
    if (doThrow) {
      throw new Error(m.$opt$noinline$toString());
    }
  }

  static native void doStaticNativeCallLiveVreg();

  //  Test ensures that 'o' has been moved into the if despite the InvokeStaticOrDirect.
  //
  /// CHECK-START: void Main.testSinkingOverInvoke() code_sinking (before)
  /// CHECK: <<Int1:i\d+>>        IntConstant 1
  /// CHECK: <<Int0:i\d+>>        IntConstant 0
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:java.lang.Object[]
  /// CHECK-NOT:                  begin_block
  /// CHECK:                      NewArray [<<LoadClass>>,<<Int1>>]
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK:                      Throw

  /// CHECK-START: void Main.testSinkingOverInvoke() code_sinking (after)
  /// CHECK: <<Int1:i\d+>>        IntConstant 1
  /// CHECK: <<Int0:i\d+>>        IntConstant 0
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:java.lang.Object[]
  /// CHECK:                      NewArray [<<LoadClass>>,<<Int1>>]
  /// CHECK:                      Throw
  static void testSinkingOverInvoke() {
    Object[] o = new Object[1];
    o[0] = o;
    doStaticNativeCallLiveVreg();
    if (doThrow) {
      throw new Error(o.toString());
    }
  }

  public String $opt$noinline$toString() {
    return "" + intField;
  }

  private static void testCatchBlock() {
    assertEquals(456, testDoNotSinkToTry());
    assertEquals(456, testSinkWithinTryBlock());
    assertEquals(456, testSinkRightBeforeTryBlock());
    assertEquals(456, testDoNotSinkToCatchInsideTryWithMoreThings(false, false));
    assertEquals(456, DoNotSinkWithOOMThrow());
  }

  /// CHECK-START: int Main.testDoNotSinkToTry() code_sinking (before)
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  /// CHECK:                         TryBoundary kind:entry

  /// CHECK-START: int Main.testDoNotSinkToTry() code_sinking (after)
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  /// CHECK:                         TryBoundary kind:entry

  // Consistency check to make sure there's only one entry TryBoundary.
  /// CHECK-START: int Main.testDoNotSinkToTry() code_sinking (after)
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK-NOT:                     TryBoundary kind:entry

  // Tests that we don't sink the Object creation into the try.
  private static int testDoNotSinkToTry() {
    Object o = new Object();
    try {
      if (doEarlyReturn) {
        throw new Error(o.toString());
      }
    } catch (Error e) {
      throw new Error();
    }
    return 456;
  }

  /// CHECK-START: int Main.testSinkWithinTryBlock() code_sinking (before)
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  /// CHECK:                         If

  /// CHECK-START: int Main.testSinkWithinTryBlock() code_sinking (after)
  /// CHECK:                         If
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  private static int testSinkWithinTryBlock() {
    try {
      Object o = new Object();
      if (doEarlyReturn) {
        throw new Error(o.toString());
      }
    } catch (Error e) {
      return 123;
    }
    return 456;
  }

  /// CHECK-START: int Main.testSinkRightBeforeTryBlock() code_sinking (before)
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  /// CHECK:                         If
  /// CHECK:                         TryBoundary kind:entry

  /// CHECK-START: int Main.testSinkRightBeforeTryBlock() code_sinking (after)
  /// CHECK:                         If
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  /// CHECK:                         TryBoundary kind:entry
  private static int testSinkRightBeforeTryBlock() {
    Object o = new Object();
    if (doEarlyReturn) {
      try {
        throw new Error(o.toString());
      } catch (Error e) {
        return 123;
      }
    }
    return 456;
  }

  /// CHECK-START: int Main.testDoNotSinkToCatchInsideTryWithMoreThings(boolean, boolean) code_sinking (before)
  /// CHECK-NOT:                     TryBoundary kind:entry
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]

  /// CHECK-START: int Main.testDoNotSinkToCatchInsideTryWithMoreThings(boolean, boolean) code_sinking (after)
  /// CHECK-NOT:                     TryBoundary kind:entry
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]

  // Tests that we don't sink the Object creation into a catch handler surrounded by try/catch, even
  // when that inner catch is not at the boundary of the outer try catch.
  private static int testDoNotSinkToCatchInsideTryWithMoreThings(boolean a, boolean b) {
    Object o = new Object();
    try {
      if (a) {
        System.out.println(a);
      }
      try {
        if (doEarlyReturn) {
          return 123;
        }
      } catch (Error e) {
        throw new Error(o.toString());
      }
      if (b) {
        System.out.println(b);
      }
    } catch (Error e) {
      throw new Error();
    }
    return 456;
  }

  private static class ObjectWithInt {
    int x;
  }

  /// CHECK-START: int Main.DoNotSinkWithOOMThrow() code_sinking (before)
  /// CHECK: <<LoadClass:l\d+>>      LoadClass class_name:Main$ObjectWithInt
  /// CHECK: <<Clinit:l\d+>>         ClinitCheck [<<LoadClass>>]
  /// CHECK:                         NewInstance [<<Clinit>>]
  /// CHECK:                         TryBoundary kind:entry

  /// CHECK-START: int Main.DoNotSinkWithOOMThrow() code_sinking (after)
  /// CHECK: <<LoadClass:l\d+>>      LoadClass class_name:Main$ObjectWithInt
  /// CHECK: <<Clinit:l\d+>>         ClinitCheck [<<LoadClass>>]
  /// CHECK:                         NewInstance [<<Clinit>>]
  /// CHECK:                         TryBoundary kind:entry

  // Consistency check to make sure there's only one entry TryBoundary.
  /// CHECK-START: int Main.DoNotSinkWithOOMThrow() code_sinking (after)
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK-NOT:                     TryBoundary kind:entry
  private static int DoNotSinkWithOOMThrow() throws OutOfMemoryError {
    int x = 0;
    ObjectWithInt obj = new ObjectWithInt();
    try {
      // We want an if/else here so that the catch block will have a catch phi.
      if (doThrow) {
        x = 1;
        // Doesn't really matter what we throw we just want it to not be caught by the
        // NullPointerException below.
        throw new OutOfMemoryError(Integer.toString(obj.x));
      } else {
        x = 456;
      }
    } catch (NullPointerException e) {
    }

    // We want to use obj over here so that it doesn't get deleted by LSE.
    if (obj.x == 123) {
      return 123;
    }
    return x;
  }

  private static void $noinline$testTwoThrowingPathsAndStringBuilderAppend() {
    try {
      $noinline$twoThrowingPathsAndStringBuilderAppend(null);
      throw new Error("Unreachable");
    } catch (Error expected) {
      assertEquals("Object is null", expected.getMessage());
    }
    try {
      $noinline$twoThrowingPathsAndStringBuilderAppend(new Object());
      throw new Error("Unreachable");
    } catch (Error expected) {
      assertEquals("s1s2", expected.getMessage());
    }
  }

  // Consistency check: only one ClinitCheck
  /// CHECK-START: void Main.$noinline$testSinkNewInstanceWithClinitCheck() code_sinking (before)
  /// CHECK:                ClinitCheck
  /// CHECK-NOT:            ClinitCheck

  /// CHECK-START: void Main.$noinline$testSinkNewInstanceWithClinitCheck() code_sinking (before)
  /// CHECK: <<Check:l\d+>> ClinitCheck
  /// CHECK:                NewInstance [<<Check>>]
  /// CHECK:                NewInstance
  /// CHECK:                If

  /// CHECK-START: void Main.$noinline$testSinkNewInstanceWithClinitCheck() code_sinking (after)
  /// CHECK: <<Check:l\d+>> ClinitCheck
  /// CHECK:                If
  /// CHECK:                NewInstance
  /// CHECK:                NewInstance [<<Check>>]

  /// CHECK-START: void Main.$noinline$testSinkNewInstanceWithClinitCheck() prepare_for_register_allocation (before)
  /// CHECK-NOT:            If

  // We have an instruction that can throw between the ClinitCheck and its NewInstance.

  /// CHECK-START: void Main.$noinline$testSinkNewInstanceWithClinitCheck() prepare_for_register_allocation (before)
  /// CHECK: <<Check:l\d+>> ClinitCheck
  /// CHECK:                NewInstance
  /// CHECK:                NewInstance [<<Check>>]

  // We can remove the ClinitCheck by merging it with the LoadClass right before.

  /// CHECK-START: void Main.$noinline$testSinkNewInstanceWithClinitCheck() prepare_for_register_allocation (after)
  /// CHECK-NOT: ClinitCheck
  private static void $noinline$testSinkNewInstanceWithClinitCheck() {
    ValueHolder vh = new ValueHolder();
    Object o = new Object();

    // The if will always be true but we don't know this after LSE. Code sinking will sink code
    // since this is an uncommon branch, but then we will have everything in one block before
    // prepare_for_register_allocation for the crash to appear.
    staticIntField = 1;
    int value = staticIntField;
    if (value == 1) {
      throw new Error(Integer.toString(vh.getValue()) + o.toString());
    }
  }

  // We currently do not inline the `StringBuilder` constructor.
  // When we did, the `StringBuilderAppend` pattern recognition was looking for
  // the inlined `NewArray` (and its associated `LoadClass`) and checked in
  // debug build that the `StringBuilder` has an environment use from this
  // `NewArray` (and maybe from `LoadClass`). However, code sinking was pruning
  // the environment of the `NewArray`, leading to a crash when compiling the
  // code below on the device (we do not inline `core-oj` on host). b/252799691

  // We currently have a heuristic that disallows inlining methods if their basic blocks end with a
  // throw. We could add code so that `requireNonNull`'s block doesn't end with a throw but that
  // would mean that the string builder optimization wouldn't fire as it requires all uses to be in
  // the same block. If `requireNonNull` is inlined at some point, we need to re-mark it as $inline$
  // so that the test is operational again.

  /// CHECK-START: void Main.$noinline$twoThrowingPathsAndStringBuilderAppend(java.lang.Object) inliner (before)
  /// CHECK: InvokeStaticOrDirect method_name:Main.requireNonNull

  /// CHECK-START: void Main.$noinline$twoThrowingPathsAndStringBuilderAppend(java.lang.Object) inliner (after)
  /// CHECK: InvokeStaticOrDirect method_name:Main.requireNonNull
  private static void $noinline$twoThrowingPathsAndStringBuilderAppend(Object o) {
    String s1 = "s1";
    String s2 = "s2";
    StringBuilder sb = new StringBuilder();

    // Before inlining, the environment use from this invoke prevents the
    // `StringBuilderAppend` pattern recognition. After inlining, we end up
    // with two paths ending with a `Throw` and we could sink the `sb`
    // instructions from above down to those below, enabling the
    // `StringBuilderAppend` pattern recognition.
    // (But that does not happen when the `StringBuilder` constructor is
    // not inlined, see above.)
    requireNonNull(o);

    String s1s2 = sb.append(s1).append(s2).toString();
    sb = null;
    throw new Error(s1s2);
  }

  private static void requireNonNull(Object o) {
    if (o == null) {
      throw new Error("Object is null");
    }
  }

  private static void assertEquals(int expected, int actual) {
    if (expected != actual) {
      throw new AssertionError("Expected: " + expected + ", Actual: " + actual);
    }
  }

  private static void assertEquals(String expected, String actual) {
    if (!expected.equals(actual)) {
      throw new AssertionError("Expected: " + expected + ", Actual: " + actual);
    }
  }

  volatile int volatileField;
  int intField;
  int intField2;
  Object objectField;
  static boolean doThrow;
  static boolean doLoop;
  static boolean doEarlyReturn;
  static boolean doOtherEarlyReturn;
  static int staticIntField;
  static Main mainField = new Main();
  static Object obj = new Object();
}
