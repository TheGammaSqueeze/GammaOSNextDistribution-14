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

package android.bluetooth.cts;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothFrameworkInitializer;
import android.os.BluetoothServiceManager;
import android.test.AndroidTestCase;

import java.util.function.Consumer;

public class BluetoothFrameworkInitializerTest extends AndroidTestCase {

    /**
     * BluetoothFrameworkInitializer.registerServiceWrappers() should only be called by
     * SystemServiceRegistry during boot up when Bluetooth is first initialized. Calling this API at
     * any other time should throw an exception.
     */
    public void test_RegisterServiceWrappers_failsWhenCalledOutsideOfSystemServiceRegistry() {
        assertThrows(IllegalStateException.class,
                () -> BluetoothFrameworkInitializer.registerServiceWrappers());
    }

    public void test_SetBluetoothServiceManager() {
        assertThrows(IllegalStateException.class,
                () -> BluetoothFrameworkInitializer.setBluetoothServiceManager(
                    new BluetoothServiceManager()));
    }

    public void test_SetBinderCallsStatsInitializer() {
        assertThrows(IllegalStateException.class,
                () -> BluetoothFrameworkInitializer.setBinderCallsStatsInitializer(new Consumer() {
                        @Override
                        public void accept(Object o) {
                        }
                }));
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
