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

package android.nfc.cts;

import static com.google.common.truth.Truth.assertThat;

import android.nfc.NfcFrameworkInitializer;
import android.nfc.NfcServiceManager;
import android.test.AndroidTestCase;

public class NfcFrameworkInitializerTest extends AndroidTestCase {

    /**
     * NfcFrameworkInitializer.registerServiceWrappers() should only be called by
     * SystemServiceRegistry during boot up when Nfc is first initialized. Calling this API at
     * any other time should throw an exception.
     */
    public void test_RegisterServiceWrappers_failsWhenCalledOutsideOfSystemServiceRegistry() {
        assertThrows(IllegalStateException.class,
                () -> NfcFrameworkInitializer.registerServiceWrappers());
    }

    public void test_SetNfcServiceManager() {
        assertThrows(IllegalStateException.class,
                () -> NfcFrameworkInitializer.setNfcServiceManager(
                    new NfcServiceManager()));
    }

    // org.junit.Assume.assertThrows is not available until JUnit 4.13
    private static void assertThrows(Class<? extends Exception> exceptionClass, Runnable r) {
        try {
            r.run();
            fail("Expected " + exceptionClass + " to be thrown.");
        } catch (Exception exception) {
            assertThat(exception).isInstanceOf(exceptionClass);
        }
    }
}
