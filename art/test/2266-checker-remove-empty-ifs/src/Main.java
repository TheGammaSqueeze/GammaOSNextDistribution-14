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
    public static void main(String[] args) {}
    public static void $inline$empty() {}
    public static void $inline$empty2() {}

    /// CHECK-START: void Main.andBoolean2(boolean, boolean) dead_code_elimination$after_inlining (before)
    /// CHECK: If
    /// CHECK: If

    /// CHECK-START: void Main.andBoolean2(boolean, boolean) dead_code_elimination$after_inlining (after)
    /// CHECK-NOT: If
    public static void andBoolean2(boolean a, boolean b) {
        if (a && b) {
            $inline$empty();
        } else {
            $inline$empty2();
        }
    }

    /// CHECK-START: void Main.andBoolean3(boolean, boolean, boolean) dead_code_elimination$after_inlining (before)
    /// CHECK: If
    /// CHECK: If
    /// CHECK: If

    /// CHECK-START: void Main.andBoolean3(boolean, boolean, boolean) dead_code_elimination$after_inlining (after)
    /// CHECK-NOT: If
    public static void andBoolean3(boolean a, boolean b, boolean c) {
        if (a && b && c) {
            $inline$empty();
        } else {
            $inline$empty2();
        }
    }

    /// CHECK-START: void Main.andBoolean4(boolean, boolean, boolean, boolean) dead_code_elimination$after_inlining (before)
    /// CHECK: If
    /// CHECK: If
    /// CHECK: If
    /// CHECK: If

    /// CHECK-START: void Main.andBoolean4(boolean, boolean, boolean, boolean) dead_code_elimination$after_inlining (after)
    /// CHECK-NOT: If
    public static void andBoolean4(boolean a, boolean b, boolean c, boolean d) {
        if (a && b && c && d) {
            $inline$empty();
        } else {
            $inline$empty2();
        }
    }

    /// CHECK-START: void Main.orBoolean2(boolean, boolean) dead_code_elimination$after_inlining (before)
    /// CHECK: If
    /// CHECK: If

    /// CHECK-START: void Main.orBoolean2(boolean, boolean) dead_code_elimination$after_inlining (after)
    /// CHECK-NOT: If
    public static void orBoolean2(boolean a, boolean b) {
        if (a || b) {
            $inline$empty();
        } else {
            $inline$empty2();
        }
    }

    /// CHECK-START: void Main.orBoolean3(boolean, boolean, boolean) dead_code_elimination$after_inlining (before)
    /// CHECK: If
    /// CHECK: If
    /// CHECK: If

    /// CHECK-START: void Main.orBoolean3(boolean, boolean, boolean) dead_code_elimination$after_inlining (after)
    /// CHECK-NOT: If
    public static void orBoolean3(boolean a, boolean b, boolean c) {
        if (a || b || c) {
            $inline$empty();
        } else {
            $inline$empty2();
        }
    }

    /// CHECK-START: void Main.orBoolean4(boolean, boolean, boolean, boolean) dead_code_elimination$after_inlining (before)
    /// CHECK: If
    /// CHECK: If
    /// CHECK: If
    /// CHECK: If

    /// CHECK-START: void Main.orBoolean4(boolean, boolean, boolean, boolean) dead_code_elimination$after_inlining (after)
    /// CHECK-NOT: If
    public static void orBoolean4(boolean a, boolean b, boolean c, boolean d) {
        if (a || b || c || d) {
            $inline$empty();
        } else {
            $inline$empty2();
        }
    }

    /// CHECK-START: void Main.andInt(int, int, int, int) dead_code_elimination$after_inlining (before)
    /// CHECK: If
    /// CHECK: If
    /// CHECK: If
    /// CHECK: If
    /// CHECK: If

    /// CHECK-START: void Main.andInt(int, int, int, int) dead_code_elimination$after_inlining (after)
    /// CHECK-NOT: If
    public static void andInt(int a, int b, int c, int d) {
        if (a <= b && c <= d && a >= 20 && b <= 78 && c >= 50 && d <= 70) {
            $inline$empty();
        } else {
            $inline$empty2();
        }
    }

    class MyObject {
        boolean inner;
        boolean inner2;
    }

    /// CHECK-START: void Main.andObject(Main$MyObject) dead_code_elimination$after_inlining (before)
    /// CHECK: If
    /// CHECK: If

    /// CHECK-START: void Main.andObject(Main$MyObject) dead_code_elimination$after_inlining (before)
    /// CHECK: InstanceFieldGet
    /// CHECK: InstanceFieldGet

    /// CHECK-START: void Main.andObject(Main$MyObject) dead_code_elimination$after_inlining (after)
    /// CHECK-NOT: If
    /// CHECK-NOT: InstanceFieldGet
    public static void andObject(MyObject o) {
        if (o != null && o.inner && o.inner2) {
            $inline$empty();
        } else {
            $inline$empty2();
        }
    }
}
