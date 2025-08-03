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

class Main {
  int myField;
  static boolean test;

  static void $noinline$assertEquals(int expected, int actual) {
    if (expected != actual) {
      throw new Error("Expected " + expected + ", got " + actual);
    }
  }

  static void $noinline$empty() {}
  static void $noinline$escape(Object m) {}

  public static void main(String[] args) {
    Main m = new Main();
    if (test) {
      $noinline$escape(m);
    } else {
      m.myField = 42;
    }
    $noinline$empty();
    $noinline$assertEquals(42, m.myField);
  }
}
