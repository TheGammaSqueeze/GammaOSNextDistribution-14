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
  public static void main(String[] args) {
    // Test that the cross-dex inlining is working for HInstructions that need an environment.
    System.out.println($noinline$testNeedsEnvironment());

    // Test that the cross-dex inlining is working for HInstructions that need a bss entry.
    System.out.println($noinline$testNeedsBssEntryString());
    System.out.println($noinline$testNeedsBssEntryInvoke());
    System.out.println($noinline$testClass());

    // Test that we are able to inline try catches across dex files.
    System.out.println($noinline$testTryCatch());
  }

  /// CHECK-START: java.lang.String Main.$noinline$testNeedsEnvironment() inliner (before)
  /// CHECK:       InvokeStaticOrDirect method_name:Multi.$inline$NeedsEnvironmentMultiDex

  /// CHECK-START: java.lang.String Main.$noinline$testNeedsEnvironment() inliner (after)
  /// CHECK-NOT:   InvokeStaticOrDirect method_name:Multi.$inline$NeedsEnvironmentMultiDex

  /// CHECK-START: java.lang.String Main.$noinline$testNeedsEnvironment() inliner (after)
  /// CHECK:       StringBuilderAppend
  private static String $noinline$testNeedsEnvironment() {
    return Multi.$inline$NeedsEnvironmentMultiDex("abc");
  }

  /// CHECK-START: java.lang.String Main.$noinline$testNeedsBssEntryString() inliner (before)
  /// CHECK:       InvokeStaticOrDirect method_name:Multi.$inline$NeedsBssEntryStringMultiDex

  /// CHECK-START: java.lang.String Main.$noinline$testNeedsBssEntryString() inliner (after)
  /// CHECK-NOT:   InvokeStaticOrDirect method_name:Multi.$inline$NeedsBssEntryStringMultiDex

  /// CHECK-START: java.lang.String Main.$noinline$testNeedsBssEntryString() inliner (after)
  /// CHECK:       LoadString load_kind:BssEntry
  private static String $noinline$testNeedsBssEntryString() {
    return Multi.$inline$NeedsBssEntryStringMultiDex();
  }

  /// CHECK-START: java.lang.String Main.$noinline$testNeedsBssEntryInvoke() inliner (before)
  /// CHECK:       InvokeStaticOrDirect method_name:Multi.$inline$NeedsBssEntryInvokeMultiDex

  /// CHECK-START: java.lang.String Main.$noinline$testNeedsBssEntryInvoke() inliner (after)
  /// CHECK-NOT:   InvokeStaticOrDirect method_name:Multi.$inline$NeedsBssEntryInvokeMultiDex

  /// CHECK-START: java.lang.String Main.$noinline$testNeedsBssEntryInvoke() inliner (after)
  /// CHECK:       InvokeStaticOrDirect method_name:Multi.$noinline$InnerInvokeMultiDex method_load_kind:BssEntry
  private static String $noinline$testNeedsBssEntryInvoke() {
    return Multi.$inline$NeedsBssEntryInvokeMultiDex();
  }

  /// CHECK-START: java.lang.Class Main.$noinline$testClass() inliner (before)
  /// CHECK:       InvokeStaticOrDirect method_name:Multi.NeedsBssEntryClassMultiDex

  /// CHECK-START: java.lang.Class Main.$noinline$testClass() inliner (after)
  /// CHECK-NOT:   InvokeStaticOrDirect method_name:Multi.NeedsBssEntryClassMultiDex

  /// CHECK-START: java.lang.Class Main.$noinline$testClass() inliner (after)
  /// CHECK:       LoadClass load_kind:BssEntry class_name:Multi$Multi2
  private static Class<?> $noinline$testClass() {
    return Multi.NeedsBssEntryClassMultiDex();
  }


  /// CHECK-START: int Main.$noinline$testTryCatch() inliner (before)
  /// CHECK-NOT:   TryBoundary

  /// CHECK-START: int Main.$noinline$testTryCatch() inliner (after)
  /// CHECK:       TryBoundary
  private static int $noinline$testTryCatch() {
    return Multi.$inline$TryCatch("123") + Multi.$inline$TryCatch("abc");
  }
}
