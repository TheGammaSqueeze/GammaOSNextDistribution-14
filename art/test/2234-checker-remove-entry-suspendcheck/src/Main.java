/*
 * Copyright (C) 2021 The Android Open Source Project
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
  public static class Inner {
    String str;
    int[] arr = {1, 2, 3};

    // Test 1: This test checks whether the SuspendCheck is removed from a simple field get.

    /// CHECK-START: java.lang.String Main$Inner.$noinline$removeSuspendCheckFieldGet() register (before)
    /// CHECK: SuspendCheck
    /// CHECK: InstanceFieldGet
    /// CHECK-START: java.lang.String Main$Inner.$noinline$removeSuspendCheckFieldGet() register (after)
    /// CHECK-NOT: SuspendCheck
    /// CHECK: InstanceFieldGet
    public String $noinline$removeSuspendCheckFieldGet() {
      return this.str;
    }

    // Test 2: This test checks whether the SuspendCheck is removed from a simple array get.

    /// CHECK-START: int Main$Inner.$noinline$removeSuspendCheckArrayGet() register (before)
    /// CHECK: SuspendCheck
    /// CHECK: ArrayGet
    /// CHECK-START: int Main$Inner.$noinline$removeSuspendCheckArrayGet() register (after)
    /// CHECK-NOT: SuspendCheck
    /// CHECK: ArrayGet
    public int $noinline$removeSuspendCheckArrayGet() {
      return this.arr[0];
    }

    // Test 3: This test checks whether the SuspendCheck is removed from a simple array set.

    /// CHECK-START: int Main$Inner.$noinline$removeSuspendCheckArraySet() register (before)
    /// CHECK: SuspendCheck
    /// CHECK: ArraySet
    /// CHECK-START: int Main$Inner.$noinline$removeSuspendCheckArraySet() register (after)
    /// CHECK-NOT: SuspendCheck
    /// CHECK: ArraySet
    public int $noinline$removeSuspendCheckArraySet() {
      return this.arr[0] = 2;
    }
  }

  public static void main(String[] args) throws Exception {
    Inner i = new Inner();
    i.$noinline$removeSuspendCheckFieldGet();
    i.$noinline$removeSuspendCheckArrayGet();
    i.$noinline$removeSuspendCheckArraySet();
  }
}