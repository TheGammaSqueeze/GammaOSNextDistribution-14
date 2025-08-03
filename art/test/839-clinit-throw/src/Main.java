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

  static class NoPreloadHolder {
    static Object o = null;

    static {
      o.toString();
    }

    static void $noinline$doCall() {
    }
  }

  public static void main(String[] args) {
    try {
      NoPreloadHolder.$noinline$doCall();
      throw new Error("Expected ExceptionInInitializerError");
    } catch (ExceptionInInitializerError e) {
      // expected
      check(e, mainLine);
    }
  }

  public static int mainLine = 32;

  static void check(ExceptionInInitializerError ie, int mainLine) {
    StackTraceElement[] trace = ie.getStackTrace();
    assertEquals(trace.length, 1);
    checkElement(trace[0], "Main", "main", "Main.java", mainLine);
  }

  static void checkElement(StackTraceElement element,
                           String declaringClass, String methodName,
                           String fileName, int lineNumber) {
    assertEquals(declaringClass, element.getClassName());
    assertEquals(methodName, element.getMethodName());
    assertEquals(fileName, element.getFileName());
    assertEquals(lineNumber, element.getLineNumber());
  }

  static void assertEquals(Object expected, Object actual) {
    if (!expected.equals(actual)) {
      String msg = "Expected \"" + expected + "\" but got \"" + actual + "\"";
      throw new AssertionError(msg);
    }
  }

  static void assertEquals(int expected, int actual) {
    if (expected != actual) {
      throw new AssertionError("Expected " + expected + " got " + actual);
    }
  }
}
