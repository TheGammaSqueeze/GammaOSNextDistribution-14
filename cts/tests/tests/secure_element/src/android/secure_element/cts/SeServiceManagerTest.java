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

package android.secure_element.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.content.pm.PackageManager;
import android.nfc.Flags;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.se.omapi.SeServiceManager;
import android.se.omapi.SeServiceManager.ServiceNotFoundException;
import android.se.omapi.SeServiceManager.ServiceRegisterer;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
@RunWith(AndroidJUnit4.class)
public class SeServiceManagerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private boolean hasSecureElementPackage(PackageManager pm) {
        try {
            pm.getPackageInfo("com.android.se", 0 /* flags*/);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Before
    public void setUp() throws NoSuchFieldException, RemoteException {
        MockitoAnnotations.initMocks(this);
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        assumeTrue(hasSecureElementPackage(pm));
    }

    @Test
    public void test_ServiceRegisterer() throws Exception {
        SeServiceManager serviceManager = new SeServiceManager();
        ServiceRegisterer serviceRegisterer =
                serviceManager.getSeManagerServiceRegisterer();

        // service not accessible to cts, so this is not very meaningful.
        assertThrows(NullPointerException.class, () ->
                serviceRegisterer.register(serviceRegisterer.get()));
        assertNull(serviceRegisterer.get());
        assertNull(serviceRegisterer.tryGet());
        assertThrows(ServiceNotFoundException.class, () ->
                serviceRegisterer.getOrThrow());
    }

    @Test
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
