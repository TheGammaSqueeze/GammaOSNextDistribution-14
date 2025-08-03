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
  public float myFloatField = 42f;
  public double myDoubleField = 42d;
  public boolean myBooleanField = true;

  public float returnFloat() {
    return myFloatField;
  }

  public double returnDouble() {
    return myDoubleField;
  }

  public boolean returnBoolean() {
    return myBooleanField;
  }

  public static void assertEquals(float a, float b) {
    if (a != b) {
      throw new Error("Expected " + a + ", got " + b);
    }
  }

  public static void assertEquals(double a, double b) {
    if (a != b) {
      throw new Error("Expected " + a + ", got " + b);
    }
  }

  public static void assertEquals(boolean a, boolean b) {
    if (a != b) {
      throw new Error("Expected " + a + ", got " + b);
    }
  }

  public static void main(String[] args) {
    System.loadLibrary(args[0]);
    ensureJitBaselineCompiled(Main.class, "returnFloat");
    ensureJitBaselineCompiled(Main.class, "returnDouble");
    ensureJitBaselineCompiled(Main.class, "returnBoolean");
    Main m = new Main();
    assertEquals(m.myFloatField, m.returnFloat());
    assertEquals(m.myDoubleField, m.returnDouble());
    assertEquals(m.myBooleanField, m.returnBoolean());
  }

  public static native void ensureJitBaselineCompiled(Class<?> cls, String methodName);
}
