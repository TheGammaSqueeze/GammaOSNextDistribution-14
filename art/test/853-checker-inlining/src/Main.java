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

class Square {
  public int foo() { return 0; }
}

class Circle extends Square {
  @Override
  public final int foo() { return 42; }
}

public class Main {
  public static void assertEquals(int expected, int actual) {
    if (expected != actual) {
      throw new Error("Expected " + expected + ", got " + actual);
    }
  }

  public static void main(String[] args) {
    assertEquals(42, square(new Circle()));
    assertEquals(42, circle(new Circle()));
  }

  /// CHECK-START: int Main.square(Circle) inliner (before)
  /// CHECK: InvokeVirtual

  /// CHECK-START: int Main.square(Circle) inliner (after)
  /// CHECK-NOT: InvokeVirtual
  static int square(Circle c) {
    Square s = c;
    return s.foo();
  }

  /// CHECK-START: int Main.circle(Circle) inliner (before)
  /// CHECK: InvokeVirtual

  /// CHECK-START: int Main.circle(Circle) inliner (after)
  /// CHECK-NOT: InvokeVirtual
  static int circle(Circle c) {
    Circle s = c;
    return s.foo();
  }
}
