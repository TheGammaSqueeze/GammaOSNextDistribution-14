/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.berberis.jnitests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class JniTests {
    static {
        System.loadLibrary("berberis_jni_tests");
    }

    static native int intFromJNI();

    @Test
    public void testReturnInt() {
      assertEquals(42, intFromJNI());
    }

    static native boolean isJNIOnLoadCalled();

    @Test
    public void testOnLoadCalled() {
      assertTrue(isJNIOnLoadCalled());
    }

    static native boolean checkGetVersion();

    @Test
    public void testGetVersion() {
      assertTrue(checkGetVersion());
    }

    static native boolean checkJavaVMCorrespondsToJNIEnv();

    @Test
    public void testJavaVMCorrespondsToJNIEnv() {
      assertTrue(checkJavaVMCorrespondsToJNIEnv());
    }

    static native boolean callRegisterNatives();
    static native int add42(int x);

    @Test
    public void testRegisterNatives() {
      assertTrue(callRegisterNatives());
      assertEquals(84, add42(42));
    }

    static int add(int x, int y) {
      return x + y;
    }

    static native int callAdd(int x, int y);

    @Test
    public void testCallStaticIntMethod() {
      assertEquals(84, callAdd(42, 42));
    }

    static native int callAddA(int x, int y);

    @Test
    public void testCallStaticIntMethodA() {
      assertEquals(84, callAddA(42, 42));
    }

    static int callIntFromJNI() {
      return intFromJNI();
    }

    static native int callCallIntFromJNI();

    @Test
    public void testCallNativeCallJavaCallNative() {
      assertEquals(42, callCallIntFromJNI());
    }
}
