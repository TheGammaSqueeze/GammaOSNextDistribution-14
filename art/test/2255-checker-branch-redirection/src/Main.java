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
    public static void main(String[] args) throws Exception {
        assertEquals(40, $noinline$testEliminateIf(20, 40));
        assertEquals(30, $noinline$testEliminateIf(20, 10));
        assertEquals(40, $noinline$testEliminateIfTwiceInARow(20, 40));
        assertEquals(30, $noinline$testEliminateIfTwiceInARow(20, 10));
        assertEquals(40, $noinline$testEliminateIfThreePredecessors(20, 40));
        assertEquals(30, $noinline$testEliminateIfThreePredecessors(20, 10));
        assertEquals(40, $noinline$testEliminateIfOppositeCondition(20, 40));
        assertEquals(30, $noinline$testEliminateIfOppositeCondition(20, 10));
        assertEquals(40, $noinline$testEliminateIfParameter(20, 40, 20 < 40));
        assertEquals(30, $noinline$testEliminateIfParameter(20, 10, 20 < 10));
        assertEquals(40, $noinline$testEliminateIfParameterReverseCondition(20, 40, 20 < 40));
        assertEquals(30, $noinline$testEliminateIfParameterReverseCondition(20, 10, 20 < 10));
        assertEquals(40, $noinline$testEliminateIfParameterOppositeCondition(20, 40, 20 < 40));
        assertEquals(30, $noinline$testEliminateIfParameterOppositeCondition(20, 10, 20 < 10));
        assertEquals(40, $noinline$testEliminateIfParameterOppositeCondition_2(20, 40, 20 < 40));
        assertEquals(30, $noinline$testEliminateIfParameterOppositeCondition_2(20, 10, 20 < 10));
    }

    private static int $noinline$emptyMethod(int a) {
        return a;
    }

    /// CHECK-START: int Main.$noinline$testEliminateIf(int, int) dead_code_elimination$after_gvn (before)
    /// CHECK:     If
    /// CHECK:     If

    /// CHECK-START: int Main.$noinline$testEliminateIf(int, int) dead_code_elimination$after_gvn (after)
    /// CHECK:     If
    /// CHECK-NOT: If
    private static int $noinline$testEliminateIf(int a, int b) {
        int result = 0;
        if (a < b) {
            $noinline$emptyMethod(a + b);
        } else {
            $noinline$emptyMethod(a - b);
        }
        if (a < b) {
            result += $noinline$emptyMethod(a * 2);
        } else {
            result += $noinline$emptyMethod(b * 3);
        }
        return result;
    }

    /// CHECK-START: int Main.$noinline$testEliminateIfTwiceInARow(int, int) dead_code_elimination$after_gvn (before)
    /// CHECK:     If
    /// CHECK:     If
    /// CHECK:     If

    /// CHECK-START: int Main.$noinline$testEliminateIfTwiceInARow(int, int) dead_code_elimination$after_gvn (after)
    /// CHECK:     If
    /// CHECK-NOT: If
    private static int $noinline$testEliminateIfTwiceInARow(int a, int b) {
        int result = 0;
        if (a < b) {
            $noinline$emptyMethod(a + b);
        } else {
            $noinline$emptyMethod(a - b);
        }
        if (a < b) {
            $noinline$emptyMethod(a * 2);
        } else {
            $noinline$emptyMethod(b * 3);
        }
        if (a < b) {
            result += $noinline$emptyMethod(40);
        } else {
            result += $noinline$emptyMethod(30);
        }
        return result;
    }

    /// CHECK-START: int Main.$noinline$testEliminateIfThreePredecessors(int, int) dead_code_elimination$after_gvn (before)
    /// CHECK:     If
    /// CHECK:     If
    /// CHECK:     If

    /// CHECK-START: int Main.$noinline$testEliminateIfThreePredecessors(int, int) dead_code_elimination$after_gvn (after)
    /// CHECK:     If
    /// CHECK:     If
    /// CHECK-NOT: If
    private static int $noinline$testEliminateIfThreePredecessors(int a, int b) {
        int result = 0;
        if (a < b) {
            $noinline$emptyMethod(a + b);
        } else {
            if (b < 5) {
                $noinline$emptyMethod(a - b);
            } else {
                $noinline$emptyMethod(a * b);
            }
        }
        if (a < b) {
            result += $noinline$emptyMethod(a * 2);
        } else {
            result += $noinline$emptyMethod(b * 3);
        }
        return result;
    }

    // Note that we can perform this optimization in dead_code_elimination$initial since we don't
    // rely on gvn to de-duplicate the values.

    /// CHECK-START: int Main.$noinline$testEliminateIfOppositeCondition(int, int) dead_code_elimination$initial (before)
    /// CHECK:     If
    /// CHECK:     If

    /// CHECK-START: int Main.$noinline$testEliminateIfOppositeCondition(int, int) dead_code_elimination$initial (after)
    /// CHECK:     If
    /// CHECK-NOT: If
    private static int $noinline$testEliminateIfOppositeCondition(int a, int b) {
        int result = 0;
        if (a < b) {
            $noinline$emptyMethod(a + b);
        } else {
            $noinline$emptyMethod(a - b);
        }
        if (a >= b) {
            result += $noinline$emptyMethod(b * 3);
        } else {
            result += $noinline$emptyMethod(a * 2);
        }
        return result;
    }

    // In this scenario, we have a BooleanNot before the If instructions so we have to wait until
    // the following pass to perform the optimization. The BooleanNot is dead at this time (even
    // when starting DCE), but RemoveDeadInstructions runs after SimplifyIfs so the optimization
    // doesn't trigger.

    /// CHECK-START: int Main.$noinline$testEliminateIfParameter(int, int, boolean) dead_code_elimination$initial (before)
    /// CHECK:     BooleanNot
    /// CHECK:     If
    /// CHECK:     BooleanNot
    /// CHECK:     If

    /// CHECK-START: int Main.$noinline$testEliminateIfParameter(int, int, boolean) dead_code_elimination$initial (after)
    /// CHECK:     If
    /// CHECK:     Phi
    /// CHECK:     If

    /// CHECK-START: int Main.$noinline$testEliminateIfParameter(int, int, boolean) dead_code_elimination$initial (after)
    /// CHECK-NOT: BooleanNot

    /// CHECK-START: int Main.$noinline$testEliminateIfParameter(int, int, boolean) dead_code_elimination$after_gvn (before)
    /// CHECK:     If
    /// CHECK:     Phi
    /// CHECK:     If

    /// CHECK-START: int Main.$noinline$testEliminateIfParameter(int, int, boolean) dead_code_elimination$after_gvn (after)
    /// CHECK:     If
    /// CHECK-NOT: If
    private static int $noinline$testEliminateIfParameter(int a, int b, boolean condition) {
        int result = 0;
        if (condition) {
            $noinline$emptyMethod(a + b);
        } else {
            $noinline$emptyMethod(a - b);
        }
        if (condition) {
            result += $noinline$emptyMethod(a * 2);
        } else {
            result += $noinline$emptyMethod(b * 3);
        }
        return result;
    }

    // Same in the following two cases: we do it in dead_code_elimination$initial since GVN is not
    // needed.

    /// CHECK-START: int Main.$noinline$testEliminateIfParameterReverseCondition(int, int, boolean) dead_code_elimination$initial (before)
    /// CHECK:     If
    /// CHECK:     If

    /// CHECK-START: int Main.$noinline$testEliminateIfParameterReverseCondition(int, int, boolean) dead_code_elimination$initial (after)
    /// CHECK:     If
    /// CHECK-NOT: If
    private static int $noinline$testEliminateIfParameterReverseCondition(
            int a, int b, boolean condition) {
        int result = 0;
        if (!condition) {
            $noinline$emptyMethod(a + b);
        } else {
            $noinline$emptyMethod(a - b);
        }
        if (!condition) {
            result += $noinline$emptyMethod(b * 3);
        } else {
            result += $noinline$emptyMethod(a * 2);
        }
        return result;
    }

    /// CHECK-START: int Main.$noinline$testEliminateIfParameterOppositeCondition(int, int, boolean) dead_code_elimination$initial (before)
    /// CHECK:     If
    /// CHECK:     If

    /// CHECK-START: int Main.$noinline$testEliminateIfParameterOppositeCondition(int, int, boolean) dead_code_elimination$initial (after)
    /// CHECK:     If
    /// CHECK-NOT: If
    private static int $noinline$testEliminateIfParameterOppositeCondition(
            int a, int b, boolean condition) {
        int result = 0;
        if (condition) {
            $noinline$emptyMethod(a + b);
        } else {
            $noinline$emptyMethod(a - b);
        }
        if (!condition) {
            result += $noinline$emptyMethod(b * 3);
        } else {
            result += $noinline$emptyMethod(a * 2);
        }
        return result;
    }

    // In this scenario, we have a BooleanNot before the If instructions so we have to wait until
    // the following pass to perform the optimization. The BooleanNot is dead at this time (even
    // when starting DCE), but RemoveDeadInstructions runs after SimplifyIfs so the optimization
    // doesn't trigger.

    /// CHECK-START: int Main.$noinline$testEliminateIfParameterOppositeCondition_2(int, int, boolean) dead_code_elimination$initial (before)
    /// CHECK:     If
    /// CHECK:     BooleanNot
    /// CHECK:     If

    /// CHECK-START: int Main.$noinline$testEliminateIfParameterOppositeCondition_2(int, int, boolean) dead_code_elimination$initial (after)
    /// CHECK:     If
    /// CHECK:     Phi
    /// CHECK:     If

    /// CHECK-START: int Main.$noinline$testEliminateIfParameterOppositeCondition_2(int, int, boolean) dead_code_elimination$initial (after)
    /// CHECK-NOT: BooleanNot

    /// CHECK-START: int Main.$noinline$testEliminateIfParameterOppositeCondition_2(int, int, boolean) dead_code_elimination$after_gvn (before)
    /// CHECK:     If
    /// CHECK:     Phi
    /// CHECK:     If

    /// CHECK-START: int Main.$noinline$testEliminateIfParameterOppositeCondition_2(int, int, boolean) dead_code_elimination$after_gvn (after)
    /// CHECK:     If
    /// CHECK-NOT: If
    private static int $noinline$testEliminateIfParameterOppositeCondition_2(
            int a, int b, boolean condition) {
        int result = 0;
        if (!condition) {
            $noinline$emptyMethod(a + b);
        } else {
            $noinline$emptyMethod(a - b);
        }
        if (condition) {
            result += $noinline$emptyMethod(a * 2);
        } else {
            result += $noinline$emptyMethod(b * 3);
        }
        return result;
    }

    public static void assertEquals(int expected, int result) {
        if (expected != result) {
            throw new Error("Expected: " + expected + ", found: " + result);
        }
    }
}
