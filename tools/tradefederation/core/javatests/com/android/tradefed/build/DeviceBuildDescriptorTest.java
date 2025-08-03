/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.tradefed.build;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DeviceBuildDescriptor}. */
@RunWith(JUnit4.class)
public class DeviceBuildDescriptorTest {

    @Test
    public void testDeviceBuildDescriptor() throws DeviceNotAvailableException {
        BuildInfo b = new BuildInfo();
        ITestDevice d = mock(ITestDevice.class);
        when(d.getProperty("ro.product.name")).thenReturn("yakju");
        when(d.getProperty("ro.build.type")).thenReturn("userdebug");
        when(d.getProperty("ro.product.brand")).thenReturn("google");
        when(d.getProperty("ro.product.model")).thenReturn("Galaxy Nexus");
        when(d.getProperty("ro.build.version.release")).thenReturn("4.2");

        DeviceBuildDescriptor.injectDeviceAttributes(d, b);
        DeviceBuildDescriptor db = new DeviceBuildDescriptor(b);
        assertEquals("yakju-userdebug", db.getDeviceBuildFlavor());
        assertEquals("Google Galaxy Nexus 4.2", db.getDeviceUserDescription());
    }
}
