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

import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.test.AndroidTestCase;

import androidx.test.filters.SmallTest;

/**
 * Unit test cases for {@link BluetoothHidDeviceAppSdpSettings}.
 */
public class BluetoothHidDeviceAppSdpSettingsTest extends AndroidTestCase {
    @SmallTest
    public void testGetters() {
        String name = "test-name";
        String description = "test-description";
        String provider = "test-provider";
        byte subclass = 1;
        byte[] descriptors = new byte[] {10};
        BluetoothHidDeviceAppSdpSettings settings = new BluetoothHidDeviceAppSdpSettings(
                name, description, provider, subclass, descriptors);
        assertEquals(name, settings.getName());
        assertEquals(description, settings.getDescription());
        assertEquals(provider, settings.getProvider());
        assertEquals(subclass, settings.getSubclass());
        assertEquals(descriptors.length, settings.getDescriptors().length);
        assertEquals(descriptors[0], settings.getDescriptors()[0]);
    }
}
