/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.ndk_tests;

import android.os.Bundle;
import android.test.InstrumentationTestCase;
import android.test.InstrumentationTestRunner;

public class NdkTests extends InstrumentationTestCase {
  public void testMain() {
    InstrumentationTestRunner testRunner =
        (InstrumentationTestRunner) getInstrumentation();
    Bundle arguments = testRunner.getArguments();
    String gtestList =
        arguments.getCharSequence("atf-gtest-list", "").toString();
    String gtestFilter =
        arguments.getCharSequence("atf-gtest-filter", "").toString();
    assertEquals(0, runTests(gtestList, gtestFilter));
  }

  native int runTests(String gtestList, String gtestFilter);

  private native float returnFloat(float arg);

  private native int returnInt(int arg);

  // We call this from native part because this test-apk is build using
  // "native infrastructure". Thus only fails in native tests are reported.
  // On the other side "java infrastructure" at the moment doesn't
  // support building native part.
  // TODO(levarum): Make new test on "java infrastructure"
  // when it become more flexible.
  public boolean wrappersABITest() {
    // returnFloat and returnInt are defined to point the same code on ARM.
    // Though, we should correctly generate and use different wrappers for them.
    return (returnFloat(1.f) == 1.f) && (returnInt(1) == 1);
  }

  public boolean jniArgTest(long a1, int a2, long a3, int a4, int a5, long a6) {
    return a1 == 1 && a2 == 2 && a3 == 3 && a4 == 4 && a5 == 5 && a6 == 6;
  }

  public boolean jniFloatArgTest(float a1, int a2, float a3, int a4, int a5,
          float a6) {
    return a1 == 1.0 && a2 == 2 && a3 == 3.0 && a4 == 4 && a5 == 5 && a6 == 6.0;
  }

  public int callReturn42() {
    return return42();
  }

  public native int return42();

  static {
    System.loadLibrary("berberis_ndk_tests");
  }
}
