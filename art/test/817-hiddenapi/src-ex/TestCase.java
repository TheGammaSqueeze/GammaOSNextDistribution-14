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

public class TestCase {

  public static void test() {
    testPublicSdk();
    testUnsupportedAppUsage();
  }

  public static void testPublicSdk() {
    // This call should be successful as the method is accessible through the interface.
    int value = new InheritAbstract().methodPublicSdkNotInAbstractParent();
    if (value != 42) {
      throw new Error("Expected 42, got " + value);
    }
  }

  public static void testUnsupportedAppUsage() {
    // This call should throw an exception as the only accessible method is unsupportedappusage.
    try {
      new InheritAbstract().methodUnsupportedNotInAbstractParent();
      throw new Error("Expected NoSuchMethodError");
    } catch (NoSuchMethodError e) {
      // Expected.
    }
  }

  public static void testNative(String library) {
    System.load(library);
    int value = testNativeInternal();
    if (value != 42) {
      throw new Error("Expected 42, got " + value);
    }

    // Test that we consistently return we cannot access a hidden method.

    // Dedupe hidden api warnings to trigger the optimization described below.
    dedupeHiddenApiWarnings();
    assertFalse(testAccessInternal(
        InheritAbstract.class, "methodUnsupportedNotInAbstractParent", "()I"));
    // Access the accessible method through reflection. This will do an optimization pretending the
    // method is public API.
    try {
      NotInAbstractInterface.class.getDeclaredMethod("methodUnsupportedNotInAbstractParent").
          invoke(new InheritAbstract());
    } catch (Exception e) {
      throw new Error(e);
    }
    assertFalse(testAccessInternal(
        InheritAbstract.class, "methodUnsupportedNotInAbstractParent", "()I"));
  }

  public static void assertTrue(boolean value) {
    if (!value) {
      throw new Error("Expected true value");
    }
  }

  public static void assertFalse(boolean value) {
    if (value) {
      throw new Error("Expected false value");
    }
  }

  public static native int testNativeInternal();
  public static native void dedupeHiddenApiWarnings();
  public static native boolean testAccessInternal(Class<?> cls, String method, String signature);
}
