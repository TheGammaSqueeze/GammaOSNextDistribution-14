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

class Main {
  public static void main(String[] args) {
    Foo f = new Foo(args);
    if (!f.getClass().isRecord()) {
      throw new Error("Expected " + f.getClass() + " to be a record");
    }
    // Trigger a GC, which used to crash when visiting an instance of a record class.
    Runtime.getRuntime().gc();
  }

  record Foo(Object o) {}
}
