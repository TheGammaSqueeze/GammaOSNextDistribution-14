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

package com.android.art_cts;

import junit.framework.TestCase;

import static junit.framework.Assert.assertEquals;

public class ArtTest extends TestCase {

  static class Helper_b197981962 {
    int myField;
    static Helper_b197981962 escape;

    static void runTest(boolean condition) {
      Helper_b197981962 l = new Helper_b197981962();
      // LSE will find that this store can be removed, as both branches override the value
      // with a new one.
      l.myField = 42;
      if (condition) {
        // LSE will remove this store as well, as it's the value after the store of 42 is removed.
        l.myField = 0;
        // This makes sure `m` gets materialized. At this point, the bug is that the partial LSE
        // optimization thinks the value incoming this block for `m.myField` is 42, however that
        // store, as well as the store to 0, have been removed.
        escape = l;
        // Do something the compiler cannot explore.
        escape.getClass().getDeclaredMethods();
        assertEquals(0, escape.myField);
      } else {
        l.myField = 3;
        assertEquals(3, l.myField);
      }
    }
  }

  public void test_b197981962() {
    // Run enough times to trigger compilation.
    for (int i = 0; i < 100000; ++i) {
      Helper_b197981962.runTest(true);
      Helper_b197981962.runTest(false);
    }
  }
}
