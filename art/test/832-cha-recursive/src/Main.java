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
  int mainField = 42;

  public static void main(String[] args) throws Exception {
    System.loadLibrary(args[0]);
    ensureJitCompiled(Main.class, "$noinline$callRecursiveMethod");
    $noinline$callRecursiveMethod(true);
  }

  public static void expectEquals(int expected, int actual) {
    if (expected != actual) {
      throw new Error("Expected " + expected + ", got " + actual);
    }
  }

  public static void $noinline$callRecursiveMethod(boolean firstEntry) throws Exception {
    Class<?> cls = Class.forName("LoadedLater");
    Main m = (Main)cls.newInstance();
    if (firstEntry) {
      $noinline$callRecursiveMethod(false);
    } else {
      expectEquals(0, m.getField());
    }
  }

  public int getField() {
    return mainField;
  }

  public static native void ensureJitCompiled(Class<?> cls, String methodName);
}

class LoadedLater extends Main {
  public int getField() {
    return 0;
  }
}
