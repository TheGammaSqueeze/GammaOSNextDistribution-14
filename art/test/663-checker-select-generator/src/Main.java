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
  // Check that we don't generate a select since we don't have no Phi (not even at the builder
  // stage) since both values are the same.

  /// CHECK-START: int Main.$noinline$testSimpleDiamondSameValue(boolean) builder (after)
  /// CHECK-NOT: Phi

  /// CHECK-START: int Main.$noinline$testSimpleDiamondSameValue(boolean) select_generator (before)
  /// CHECK-NOT: Phi

  /// CHECK-START: int Main.$noinline$testSimpleDiamondSameValue(boolean) select_generator (after)
  /// CHECK-NOT: Phi

  /// CHECK-START: int Main.$noinline$testSimpleDiamondSameValue(boolean) select_generator (after)
  /// CHECK-NOT: Select
  private static int $noinline$testSimpleDiamondSameValue(boolean bool_param) {
    int return_value;
    if (bool_param) {
      return_value = 10;
    } else {
      return_value = 10;
    }
    return return_value;
  }

  // Check that we generate a select for a simple diamond pattern, with different values.

  /// CHECK-START: int Main.$noinline$testSimpleDiamondDifferentValue(boolean) select_generator (before)
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:   <<Phi:i\d+>>     Phi [<<Arg1:i\d+>>,<<Arg2:i\d+>>]
  /// CHECK-DAG:                    Return [<<Phi>>]
  /// CHECK-EVAL:  set(["<<Arg1>>","<<Arg2>>"]) == set(["<<Const10>>","<<Const20>>"])

  /// CHECK-START: int Main.$noinline$testSimpleDiamondDifferentValue(boolean) select_generator (after)
  /// CHECK-DAG:   <<Bool:z\d+>>    ParameterValue
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:   <<Select:i\d+>>  Select [<<Const20>>,<<Const10>>,<<Bool>>]
  /// CHECK-DAG:                    Return [<<Select>>]
  private static int $noinline$testSimpleDiamondDifferentValue(boolean bool_param) {
    int return_value;
    if (bool_param) {
      return_value = 10;
    } else {
      return_value = 20;
    }
    return return_value;
  }

  // Check that we don't generate a select since we don't have no Phi (not even at the builder
  // stage) since all values are the same.

  /// CHECK-START: int Main.$noinline$testDoubleDiamondSameValue(boolean, boolean) builder (after)
  /// CHECK-NOT: Phi

  /// CHECK-START: int Main.$noinline$testDoubleDiamondSameValue(boolean, boolean) select_generator (before)
  /// CHECK-NOT: Phi

  /// CHECK-START: int Main.$noinline$testDoubleDiamondSameValue(boolean, boolean) select_generator (after)
  /// CHECK-NOT: Phi

  /// CHECK-START: int Main.$noinline$testDoubleDiamondSameValue(boolean, boolean) select_generator (after)
  /// CHECK-NOT: Select
  private static int $noinline$testDoubleDiamondSameValue(boolean bool_param_1, boolean bool_param_2) {
      int return_value;
    if (bool_param_1) {
      return_value = 10;
    } else {
      if (bool_param_2) {
        return_value = 10;
      } else {
        return_value = 10;
      }
    }
    return return_value;
  }

  // Check that we generate a select for a double diamond pattern, with a different value in the outer branch.

  /// CHECK-START: int Main.$noinline$testDoubleDiamondSameValueButNotAllOuter(boolean, boolean) select_generator (before)
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:   <<Phi:i\d+>>     Phi [<<Arg1:i\d+>>,<<Arg2:i\d+>>,<<Arg3:i\d+>>]
  /// CHECK-DAG:                    Return [<<Phi>>]
  /// CHECK-EVAL:  set(["<<Arg1>>","<<Arg2>>","<<Arg3>>"]) == set(["<<Const10>>","<<Const20>>","<<Const20>>"])

  /// CHECK-START: int Main.$noinline$testDoubleDiamondSameValueButNotAllOuter(boolean, boolean) select_generator (after)
  /// CHECK-DAG:   <<Bool1:z\d+>>   ParameterValue
  /// CHECK-DAG:   <<Bool2:z\d+>>   ParameterValue
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:   <<Select:i\d+>>  Select [<<Const20>>,<<Const20>>,<<Bool2>>]
  /// CHECK-DAG:   <<Select2:i\d+>> Select [<<Select>>,<<Const10>>,<<Bool1>>]
  /// CHECK-DAG:                    Return [<<Select2>>]
  private static int $noinline$testDoubleDiamondSameValueButNotAllOuter(boolean bool_param_1, boolean bool_param_2) {
      int return_value;
    if (bool_param_1) {
      return_value = 10;
    } else {
      if (bool_param_2) {
        return_value = 20;
      } else {
        return_value = 20;
      }
    }
    return return_value;
  }

  // Check that we generate a select for a double diamond pattern, with a different value in the inner branch.

  /// CHECK-START: int Main.$noinline$testDoubleDiamondSameValueButNotAllInner(boolean, boolean) select_generator (before)
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:   <<Phi:i\d+>>     Phi [<<Arg1:i\d+>>,<<Arg2:i\d+>>,<<Arg3:i\d+>>]
  /// CHECK-DAG:                    Return [<<Phi>>]
  /// CHECK-EVAL:  set(["<<Arg1>>","<<Arg2>>","<<Arg3>>"]) == set(["<<Const10>>","<<Const20>>","<<Const20>>"])

  /// CHECK-START: int Main.$noinline$testDoubleDiamondSameValueButNotAllInner(boolean, boolean) select_generator (after)
  /// CHECK-DAG:   <<Bool1:z\d+>>   ParameterValue
  /// CHECK-DAG:   <<Bool2:z\d+>>   ParameterValue
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:   <<Select:i\d+>>  Select [<<Const20>>,<<Const10>>,<<Bool2>>]
  /// CHECK-DAG:   <<Select2:i\d+>> Select [<<Select>>,<<Const20>>,<<Bool1>>]
  /// CHECK-DAG:                    Return [<<Select2>>]
  private static int $noinline$testDoubleDiamondSameValueButNotAllInner(boolean bool_param_1, boolean bool_param_2) {
      int return_value;
    if (bool_param_1) {
      return_value = 20;
    } else {
      if (bool_param_2) {
        return_value = 10;
      } else {
        return_value = 20;
      }
    }
    return return_value;
  }

  // Check that we generate a select for a double diamond pattern, with a all different values.

  /// CHECK-START: int Main.$noinline$testDoubleDiamondDifferentValue(boolean, boolean) select_generator (before)
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:   <<Const30:i\d+>> IntConstant 30
  /// CHECK-DAG:   <<Phi:i\d+>>     Phi [<<Arg1:i\d+>>,<<Arg2:i\d+>>,<<Arg3:i\d+>>]
  /// CHECK-DAG:                    Return [<<Phi>>]
  /// CHECK-EVAL:  set(["<<Arg1>>","<<Arg2>>","<<Arg3>>"]) == set(["<<Const10>>","<<Const20>>","<<Const30>>"])

  /// CHECK-START: int Main.$noinline$testDoubleDiamondDifferentValue(boolean, boolean) select_generator (after)
  /// CHECK-DAG:   <<Bool1:z\d+>>   ParameterValue
  /// CHECK-DAG:   <<Bool2:z\d+>>   ParameterValue
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:   <<Const30:i\d+>> IntConstant 30
  /// CHECK-DAG:   <<Select:i\d+>>  Select [<<Const30>>,<<Const20>>,<<Bool2>>]
  /// CHECK-DAG:   <<Select2:i\d+>> Select [<<Select>>,<<Const10>>,<<Bool1>>]
  /// CHECK-DAG:                    Return [<<Select2>>]
  private static int $noinline$testDoubleDiamondDifferentValue(boolean bool_param_1, boolean bool_param_2) {
      int return_value;
    if (bool_param_1) {
      return_value = 10;
    } else {
      if (bool_param_2) {
        return_value = 20;
      } else {
        return_value = 30;
      }
    }
    return return_value;
  }

  private static void assertEquals(int expected, int actual) {
    if (expected != actual) {
      throw new AssertionError("Expected " + expected + " got " + actual);
    }
  }

  // Check that we don't generate a select since we only have a single return.

  /// CHECK-START: int Main.$noinline$testSimpleDiamondSameValueWithReturn(boolean) builder (after)
  /// CHECK:       <<Const10:i\d+>> IntConstant 10
  /// CHECK:       Return [<<Const10>>]

  /// CHECK-START: int Main.$noinline$testSimpleDiamondSameValueWithReturn(boolean) builder (after)
  /// CHECK:       Return
  /// CHECK-NOT:   Return

  private static int $noinline$testSimpleDiamondSameValueWithReturn(boolean bool_param) {
    if (bool_param) {
      return 10;
    } else {
      return 10;
    }
  }

  // Same as testSimpleDiamondDifferentValue, but branches return.

  /// CHECK-START: int Main.$noinline$testSimpleDiamondDifferentValueWithReturn(boolean) select_generator (before)
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:                    Return [<<Const10>>]
  /// CHECK-DAG:                    Return [<<Const20>>]

  /// CHECK-START: int Main.$noinline$testSimpleDiamondDifferentValueWithReturn(boolean) select_generator (after)
  /// CHECK-DAG:   <<Bool:z\d+>>    ParameterValue
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:   <<Select:i\d+>>  Select [<<Const20>>,<<Const10>>,<<Bool>>]
  /// CHECK-DAG:                    Return [<<Select>>]
  private static int $noinline$testSimpleDiamondDifferentValueWithReturn(boolean bool_param) {
    if (bool_param) {
      return 10;
    } else {
      return 20;
    }
  }

  // Check that we don't generate a select since we only have a single return.

  /// CHECK-START: int Main.$noinline$testSimpleDiamondSameValueWithReturn(boolean) builder (after)
  /// CHECK:       <<Const10:i\d+>> IntConstant 10
  /// CHECK:       Return [<<Const10>>]

  /// CHECK-START: int Main.$noinline$testSimpleDiamondSameValueWithReturn(boolean) builder (after)
  /// CHECK:       Return
  /// CHECK-NOT:   Return
  private static int $noinline$testDoubleDiamondSameValueWithReturn(boolean bool_param_1, boolean bool_param_2) {
    if (bool_param_1) {
      return 10;
    } else {
      if (bool_param_2) {
        return 10;
      } else {
        return 10;
      }
    }
  }

  // Same as testDoubleDiamondSameValueButNotAllOuter, but branches return.

  /// CHECK-START: int Main.$noinline$testDoubleDiamondSameValueButNotAllOuterWithReturn(boolean, boolean) select_generator (before)
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:                    Return [<<Const10>>]
  /// CHECK-DAG:                    Return [<<Const20>>]

  // Note that we have 2 returns instead of 3 as the two `return 20;` get merged into one before `select_generator`.
  /// CHECK-START: int Main.$noinline$testDoubleDiamondSameValueButNotAllOuterWithReturn(boolean, boolean) select_generator (before)
  /// CHECK:                    Return
  /// CHECK:                    Return
  /// CHECK-NOT:                Return

  /// CHECK-START: int Main.$noinline$testDoubleDiamondSameValueButNotAllOuterWithReturn(boolean, boolean) select_generator (after)
  /// CHECK-DAG:   <<Bool1:z\d+>>   ParameterValue
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:   <<Select2:i\d+>> Select [<<Const20>>,<<Const10>>,<<Bool1>>]
  /// CHECK-DAG:                    Return [<<Select2>>]
  private static int $noinline$testDoubleDiamondSameValueButNotAllOuterWithReturn(boolean bool_param_1, boolean bool_param_2) {
    if (bool_param_1) {
      return 10;
    } else {
      if (bool_param_2) {
        return 20;
      } else {
        return 20;
      }
    }
  }

  // Same as testDoubleDiamondSameValueButNotAllInner, but branches return.

  /// CHECK-START: int Main.$noinline$testDoubleDiamondSameValueButNotAllInnerWithReturn(boolean, boolean) select_generator (before)
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:                    Return [<<Const10>>]
  /// CHECK-DAG:                    Return [<<Const20>>]
  /// CHECK-DAG:                    Return [<<Const20>>]

  /// CHECK-START: int Main.$noinline$testDoubleDiamondSameValueButNotAllInnerWithReturn(boolean, boolean) select_generator (after)
  /// CHECK-DAG:   <<Bool1:z\d+>>   ParameterValue
  /// CHECK-DAG:   <<Bool2:z\d+>>   ParameterValue
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:   <<Select:i\d+>>  Select [<<Const20>>,<<Const10>>,<<Bool2>>]
  /// CHECK-DAG:   <<Select2:i\d+>> Select [<<Select>>,<<Const20>>,<<Bool1>>]
  /// CHECK-DAG:                    Return [<<Select2>>]
  private static int $noinline$testDoubleDiamondSameValueButNotAllInnerWithReturn(boolean bool_param_1, boolean bool_param_2) {
    if (bool_param_1) {
      return 20;
    } else {
      if (bool_param_2) {
        return 10;
      } else {
        return 20;
      }
    }
  }

  // Same as testDoubleDiamondDifferentValue, but branches return.

  /// CHECK-START: int Main.$noinline$testDoubleDiamondDifferentValueWithReturn(boolean, boolean) select_generator (before)
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:   <<Const30:i\d+>> IntConstant 30
  /// CHECK-DAG:                    Return [<<Const10>>]
  /// CHECK-DAG:                    Return [<<Const20>>]
  /// CHECK-DAG:                    Return [<<Const30>>]

  /// CHECK-START: int Main.$noinline$testDoubleDiamondDifferentValueWithReturn(boolean, boolean) select_generator (after)
  /// CHECK-DAG:   <<Bool1:z\d+>>   ParameterValue
  /// CHECK-DAG:   <<Bool2:z\d+>>   ParameterValue
  /// CHECK-DAG:   <<Const10:i\d+>> IntConstant 10
  /// CHECK-DAG:   <<Const20:i\d+>> IntConstant 20
  /// CHECK-DAG:   <<Const30:i\d+>> IntConstant 30
  /// CHECK-DAG:   <<Select:i\d+>>  Select [<<Const30>>,<<Const20>>,<<Bool2>>]
  /// CHECK-DAG:   <<Select2:i\d+>> Select [<<Select>>,<<Const10>>,<<Bool1>>]
  /// CHECK-DAG:                    Return [<<Select2>>]
  private static int $noinline$testDoubleDiamondDifferentValueWithReturn(boolean bool_param_1, boolean bool_param_2) {
    if (bool_param_1) {
      return 10;
    } else {
      if (bool_param_2) {
        return 20;
      } else {
        return 30;
      }
    }
  }

  public static void main(String[] args) throws Throwable {
    // With phi
    assertEquals(10, $noinline$testSimpleDiamondSameValue(false));
    assertEquals(20, $noinline$testSimpleDiamondDifferentValue(false));
    assertEquals(10, $noinline$testDoubleDiamondSameValue(false, false));
    assertEquals(20, $noinline$testDoubleDiamondSameValueButNotAllOuter(false, false));
    assertEquals(20, $noinline$testDoubleDiamondSameValueButNotAllInner(false, false));
    assertEquals(30, $noinline$testDoubleDiamondDifferentValue(false, false));

    // With return
    assertEquals(10, $noinline$testSimpleDiamondSameValueWithReturn(false));
    assertEquals(20, $noinline$testSimpleDiamondDifferentValueWithReturn(false));
    assertEquals(10, $noinline$testDoubleDiamondSameValueWithReturn(false, false));
    assertEquals(20, $noinline$testDoubleDiamondSameValueButNotAllOuterWithReturn(false, false));
    assertEquals(20, $noinline$testDoubleDiamondSameValueButNotAllInnerWithReturn(false, false));
    assertEquals(30, $noinline$testDoubleDiamondDifferentValueWithReturn(false, false));
  }
}
