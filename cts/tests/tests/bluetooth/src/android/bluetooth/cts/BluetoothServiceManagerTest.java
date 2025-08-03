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

import static org.junit.Assert.assertThrows;

import android.content.pm.PackageManager;
import android.os.BluetoothServiceManager;
import android.os.BluetoothServiceManager.ServiceNotFoundException;
import android.os.BluetoothServiceManager.ServiceRegisterer;
import android.os.IBinder;
import android.os.ServiceManager;
import android.test.AndroidTestCase;

public class BluetoothServiceManagerTest extends AndroidTestCase {

    private boolean mHasBluetooth;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);
    }

    public void test_ServiceRegisterer() {
        if (!mHasBluetooth) {
            return;
        }
        BluetoothServiceManager serviceManager = new BluetoothServiceManager();
        ServiceRegisterer serviceRegisterer =
                serviceManager.getBluetoothManagerServiceRegisterer();

        assertThrows(SecurityException.class, () ->
                serviceRegisterer.register(serviceRegisterer.get()));

        IBinder bluetoothServiceBinder = serviceRegisterer.get();
        assertNotNull(bluetoothServiceBinder);

        bluetoothServiceBinder = serviceRegisterer.tryGet();
        assertNotNull(bluetoothServiceBinder);

        try {
            bluetoothServiceBinder = serviceRegisterer.getOrThrow();
            assertNotNull(bluetoothServiceBinder);
        } catch (ServiceNotFoundException exception) {
            fail("ServiceNotFoundException should not be thrown");
        }
    }

    public void test_ServiceNotFoundException() {
        ServiceManager.ServiceNotFoundException baseException =
                new ServiceManager.ServiceNotFoundException("");
        String exceptionDescription = "description test";
        String baseExceptionDescription = baseException.getMessage();
        ServiceNotFoundException newException =
                new ServiceNotFoundException(exceptionDescription);
        assertEquals(baseExceptionDescription + exceptionDescription, newException.getMessage());
        try {
            throw newException;
        } catch (ServiceNotFoundException exception) {
            assertEquals(baseExceptionDescription + exceptionDescription, exception.getMessage());
        }
    }
}
