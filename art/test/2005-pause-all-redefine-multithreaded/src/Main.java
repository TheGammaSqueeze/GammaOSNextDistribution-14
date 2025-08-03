/*
 * Copyright (C) 2019 The Android Open Source Project
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

import dalvik.annotation.optimization.FastNative;

public class Main {
  public static void main(String[] args) throws Exception {
    // Regression test for instrumentation installing exit handler for transition
    // from the suspend check runtime frame to the JNI stub for @FastNative method.
    Thread t = new Thread() {
      public void run() {
        Integer i = fastNativeSleepAndReturnInteger42();
        if (i != 42) {
          throw new Error("Expected 42, got " + i);
        }
      }
    };
    t.start();

    art.Test2005.run();

    t.join();
  }

  @FastNative
  public static native Integer fastNativeSleepAndReturnInteger42();
}
