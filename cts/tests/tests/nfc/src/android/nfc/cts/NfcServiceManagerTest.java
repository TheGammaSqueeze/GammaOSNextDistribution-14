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

package android.nfc.cts;

import static org.junit.Assert.assertThrows;

import android.content.pm.PackageManager;
import android.nfc.NfcServiceManager;
import android.nfc.NfcServiceManager.ServiceNotFoundException;
import android.nfc.NfcServiceManager.ServiceRegisterer;
import android.os.IBinder;
import android.os.ServiceManager;
import android.test.AndroidTestCase;

public class NfcServiceManagerTest extends AndroidTestCase {

    private boolean mHasNfc;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHasNfc = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_NFC);
    }

    public void test_ServiceRegisterer() {
        if (!mHasNfc) {
            return;
        }
        NfcServiceManager serviceManager = new NfcServiceManager();
        ServiceRegisterer serviceRegisterer =
                serviceManager.getNfcManagerServiceRegisterer();

        assertThrows(SecurityException.class, () ->
                serviceRegisterer.register(serviceRegisterer.get()));

        IBinder nfcServiceBinder = serviceRegisterer.get();
        assertNotNull(nfcServiceBinder);

        nfcServiceBinder = serviceRegisterer.tryGet();
        assertNotNull(nfcServiceBinder);

        try {
            nfcServiceBinder = serviceRegisterer.getOrThrow();
            assertNotNull(nfcServiceBinder);
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
