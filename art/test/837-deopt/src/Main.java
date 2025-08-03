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

class Main {
  int field = 42;

  // Test that deoptimization preserves objects that are singletons.
  public static int $noinline$foo(Main arg) {
    Main m = new Main();
    arg.returnValue();
    return m.field;
  }

  // Test that doing OSR after deoptimization works.
  public static int $noinline$foo2(Main arg, boolean osr) {
    Main m = new Main();
    arg.returnValue();
    if (osr) {
      while (!isInOsrCode("$noinline$foo2")) {}
    }
    return m.field;
  }

  public static void main(String[] args) throws Throwable {
    System.loadLibrary(args[0]);
    if (isDebuggable()) {
      // We do not deoptimize with inline caches when the app is debuggable, so just don't run the
      // test.
      return;
    }
    test1();
    test2();
  }

  public static void assertEquals(int expected, int actual) {
    if (expected != actual) {
      throw new Error("Expected " + expected + ", got " + actual);
    }
  }

  public static void test1() {
    ensureJitBaselineCompiled(Main.class, "$noinline$foo");
    // Surround the call with GCs to increase chances we execute $noinline$foo
    // while the GC isn't marking. This makes sure the inline cache is populated.
    Runtime.getRuntime().gc();
    assertEquals(42, $noinline$foo(new Main()));
    Runtime.getRuntime().gc();

    ensureJitCompiled(Main.class, "$noinline$foo");
    assertEquals(42, $noinline$foo(new SubMain()));
  }

  public static void test2() {
    ensureJitBaselineCompiled(Main.class, "$noinline$foo2");
    // Surround the call with GCs to increase chances we execute $noinline$foo
    // while the GC isn't marking. This makes sure the inline cache is populated.
    Runtime.getRuntime().gc();
    assertEquals(42, $noinline$foo2(new Main(), false));
    Runtime.getRuntime().gc();

    ensureJitCompiled(Main.class, "$noinline$foo2");
    assertEquals(42, $noinline$foo2(new SubMain(), true));
  }

  public String returnValue() {
    return "Main";
  }

  public static native void ensureJitCompiled(Class<?> cls, String methodName);
  public static native void ensureJitBaselineCompiled(Class<?> cls, String methodName);
  public static native boolean isInOsrCode(String methodName);
  public static native boolean isDebuggable();
}

// Define a subclass with another implementation of returnValue to deoptimize $noinline$foo and
// $noinline$foo2.
class SubMain extends Main {
  public String returnValue() {
    return "SubMain";
  }
}
