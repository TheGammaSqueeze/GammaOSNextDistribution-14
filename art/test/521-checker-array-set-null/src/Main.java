/*
 * Copyright (C) 2015 The Android Open Source Project
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
    $noinline$testWithNull(new Object[2]);
    $noinline$testWithUnknown(new Object[2], new Object());
    $noinline$testWithSame(new Object[2]);
    $noinline$testWithSameRTI();
  }

  // Known null can eliminate the type check in early stages.

  /// CHECK-START: void Main.$noinline$testWithNull(java.lang.Object[]) instruction_simplifier (before)
  /// CHECK:          ArraySet needs_type_check:true can_trigger_gc:true

  /// CHECK-START: void Main.$noinline$testWithNull(java.lang.Object[]) instruction_simplifier (after)
  /// CHECK:          ArraySet needs_type_check:false can_trigger_gc:false

  /// CHECK-START: void Main.$noinline$testWithNull(java.lang.Object[]) disassembly (after)
  /// CHECK:          ArraySet needs_type_check:false can_trigger_gc:false
  public static void $noinline$testWithNull(Object[] o) {
    o[0] = null;
  }

  /// CHECK-START: void Main.$noinline$testWithUnknown(java.lang.Object[], java.lang.Object) disassembly (after)
  /// CHECK:          ArraySet needs_type_check:true can_trigger_gc:true
  public static void $noinline$testWithUnknown(Object[] o, Object obj) {
    o[0] = obj;
  }

  // After GVN we know that we are setting values from the same array so there's no need for a type
  // check.

  /// CHECK-START: void Main.$noinline$testWithSame(java.lang.Object[]) instruction_simplifier$after_gvn (before)
  /// CHECK:          ArraySet needs_type_check:true can_trigger_gc:true

  /// CHECK-START: void Main.$noinline$testWithSame(java.lang.Object[]) instruction_simplifier$after_gvn (after)
  /// CHECK:          ArraySet needs_type_check:false can_trigger_gc:false

  /// CHECK-START: void Main.$noinline$testWithSame(java.lang.Object[]) disassembly (after)
  /// CHECK:          ArraySet needs_type_check:false can_trigger_gc:false
  public static void $noinline$testWithSame(Object[] o) {
    o[0] = o[1];
  }

  // We know that the array and the static Object have the same RTI in early stages. No need for a
  // type check.

  /// CHECK-START: java.lang.Object[] Main.$noinline$testWithSameRTI() instruction_simplifier (before)
  /// CHECK:          ArraySet needs_type_check:true can_trigger_gc:true

  /// CHECK-START: java.lang.Object[] Main.$noinline$testWithSameRTI() instruction_simplifier (after)
  /// CHECK:          ArraySet needs_type_check:false can_trigger_gc:false

  /// CHECK-START: java.lang.Object[] Main.$noinline$testWithSameRTI() disassembly (after)
  /// CHECK:          ArraySet needs_type_check:false can_trigger_gc:false
  public static Object[] $noinline$testWithSameRTI() {
    Object[] arr = new Object[1];
    arr[0] = static_obj;
    // Return so that LSE doesn't eliminate the ArraySet.
    return arr;
  }

  static Object static_obj;
}
