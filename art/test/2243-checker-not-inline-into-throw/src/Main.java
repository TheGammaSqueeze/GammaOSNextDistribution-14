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
    try {
      $noinline$testEndsWithThrow();
      throw new Exception("Unreachable");
    } catch (Error expected) {
    }

    try {
      $noinline$testEndsWithThrowButNotDirectly();
      throw new Exception("Unreachable");
    } catch (Error expected) {
    }
  }

  // Empty methods are easy to inline anywhere.
  private static void easyToInline() {}
  private static void $inline$easyToInline() {}
  private static void twoLevelEasyToInline() { easyToInline(); }

  /// CHECK-START: int Main.$noinline$testEndsWithThrow() inliner (before)
  /// CHECK: InvokeStaticOrDirect method_name:Main.twoLevelEasyToInline

  /// CHECK-START: int Main.$noinline$testEndsWithThrow() inliner (after)
  /// CHECK: InvokeStaticOrDirect method_name:Main.twoLevelEasyToInline
  static int $noinline$testEndsWithThrow() {
    // Use two level inlining to avoid a pattern match in the inliner.
    // The pattern matching is deliberately done before we check if inlining is "encouraged"
    // which includes checking if the block ends with a `throw`.
    twoLevelEasyToInline();
    throw new Error("");
  }

  // Currently we only stop inlining if the method's basic block ends with a throw. We do not stop
  // inlining for methods that eventually always end with a throw.
  static int $noinline$testEndsWithThrowButNotDirectly() {
    $inline$easyToInline();
    if (justABoolean) {
      $inline$easyToInline();
    } else {
      $inline$easyToInline();
    }
    throw new Error("");
  }

  static boolean justABoolean;
}
