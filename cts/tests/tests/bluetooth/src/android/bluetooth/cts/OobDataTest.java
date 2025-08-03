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

package android.bluetooth.cts;

import static android.bluetooth.cts.TestUtils.assertArrayEquals;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.bluetooth.OobData;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OobDataTest {

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        Assume.assumeTrue(
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void classicBuilder() {
        byte[] defaultRandomizerHash = new byte[OobData.RANDOMIZER_OCTETS];
        byte[] defaultClassOfDevice = new byte[OobData.CLASS_OF_DEVICE_OCTETS];
        // Default device name: "Bluetooth Device"
        byte[] defaultDeviceName = new byte[] {
                // Bluetooth
                0x42, 0x6c, 0x75, 0x65, 0x74, 0x6f, 0x6f, 0x74, 0x68,
                // <space>Device
                0x20, 0x44, 0x65, 0x76, 0x69, 0x63, 0x65
        };
        byte[] defaultLeTemporaryKey = new byte[OobData.LE_TK_OCTETS];
        byte[] defaultLeAppearance = new byte[OobData.LE_APPEARANCE_OCTETS];

        byte[] confirmationHash = new byte[] {0x52, 0x70, 0x49, 0x41, 0x1A, (byte) 0xB3, 0x3F, 0x5C,
                (byte) 0xE0, (byte) 0x99, 0x37, 0x29, 0x21, 0x52, 0x65, 0x49};
        byte[] length = new byte[] {0x00, 0x08};
        byte[] address = new byte[] {0x12, 0x34, 0x56, 0x78, (byte) 0x8A, (byte) 0xBC, 0x0};

        // Test invalid constructor parameters
        try {
            new OobData.ClassicBuilder(null, length, address);
            fail("Constructor does not throw a NullPointerException with a null confirmationHash");
        } catch(NullPointerException ex) {
        }
        try {
            new OobData.ClassicBuilder(confirmationHash, null, address);
            fail("Constructor does not throw a NullPointerException with a null classicLength");
        } catch(NullPointerException ex) {
        }
        try {
            new OobData.ClassicBuilder(confirmationHash, length, null);
            fail("Constructor does not throw a NullPointerException with a null "
                    + "deviceAddressWithType");
        } catch(NullPointerException ex) {
        }

        // Create a classic OobData with the required fields and verify all values set properly
        OobData.ClassicBuilder classicBuilder = new OobData.ClassicBuilder(
                confirmationHash, length, address);
        OobData defaultClassicOobData = classicBuilder.build();

        assertArrayEquals(confirmationHash, defaultClassicOobData.getConfirmationHash());
        assertArrayEquals(length, defaultClassicOobData.getClassicLength());
        assertArrayEquals(address, defaultClassicOobData.getDeviceAddressWithType());
        assertArrayEquals(defaultRandomizerHash, defaultClassicOobData.getRandomizerHash());
        assertArrayEquals(defaultClassOfDevice, defaultClassicOobData.getClassOfDevice());
        assertArrayEquals(defaultDeviceName, defaultClassicOobData.getDeviceName());
        assertEquals(-1, defaultClassicOobData.getLeDeviceRole());
        assertArrayEquals(defaultLeTemporaryKey, defaultClassicOobData.getLeTemporaryKey());
        assertArrayEquals(defaultLeAppearance, defaultClassicOobData.getLeAppearance());
        assertEquals(OobData.LE_FLAG_LIMITED_DISCOVERY_MODE, defaultClassicOobData.getLeFlags());

        // Test setting optional classic OobData fields and verifying values set properly
        String deviceNameString = "Test Device Name";
        byte[] deviceName = deviceNameString.getBytes();
        byte[] randomizerHash = new byte[] {(byte) 0x9E, 0x43, 0x51, 0x10, 0x70, 0x33, 0x01,
                (byte) 0xDE, 0x00, 0x02, 0x03, 0x05, 0x09, 0x10, 0x40, 0x07};
        byte[] classOfDevice = new byte[] {0x72, 0x02, 0x0C};

        classicBuilder
                .setClassOfDevice(classOfDevice)
                .setDeviceName(deviceName)
                .setRandomizerHash(randomizerHash);
        OobData classicData = classicBuilder.build();

        assertArrayEquals(randomizerHash, classicData.getRandomizerHash());
        assertArrayEquals(classOfDevice, classicData.getClassOfDevice());
        assertArrayEquals(deviceName, classicData.getDeviceName());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void lEBuilder() {
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        byte[] defaultRandomizerHash = new byte[OobData.RANDOMIZER_OCTETS];
        byte[] defaultClassOfDevice = new byte[OobData.CLASS_OF_DEVICE_OCTETS];
        byte[] defaultClassicLength = new byte[OobData.OOB_LENGTH_OCTETS];
        // Default device name: "Bluetooth Device"
        byte[] defaultDeviceName = new byte[] {
                // Bluetooth
                0x42, 0x6c, 0x75, 0x65, 0x74, 0x6f, 0x6f, 0x74, 0x68,
                // <space>Device
                0x20, 0x44, 0x65, 0x76, 0x69, 0x63, 0x65
        };
        byte[] defaultLeTemporaryKey = new byte[OobData.LE_TK_OCTETS];
        byte[] defaultLeAppearance = new byte[OobData.LE_APPEARANCE_OCTETS];

        byte[] confirmationHash = new byte[] {0x52, 0x70, 0x49, 0x41, 0x1A, (byte) 0xB3, 0x3F, 0x5C,
                (byte) 0xE0, (byte) 0x99, 0x37, 0x29, 0x21, 0x52, 0x65, 0x49};
        byte[] address = new byte[] {0x12, 0x34, 0x56, 0x78, (byte) 0x8A, (byte) 0xBC, 0x0};

        // Test invalid constructor parameters
        try {
            new OobData.LeBuilder(null, address, OobData.LE_DEVICE_ROLE_PERIPHERAL_ONLY);
            fail("Constructor does not throw a NullPointerException with a null confirmationHash");
        } catch(NullPointerException ex) {
        }
        try {
            new OobData.LeBuilder(confirmationHash, null, OobData.LE_DEVICE_ROLE_PERIPHERAL_ONLY);
            fail("Constructor does not throw a NullPointerException with a null "
                    + "deviceAddressWithType");
        } catch(NullPointerException ex) {
        }
        try {
            new OobData.LeBuilder(confirmationHash, address, -1);
            fail("Constructor does not throw an IllegalArgumentException with a null leDeviceRole");
        } catch(IllegalArgumentException ex) {
        }

        // Create a classic OobData with the required fields and verify all values set properly
        OobData.LeBuilder leBuilder = new OobData.LeBuilder(confirmationHash, address,
                OobData.LE_DEVICE_ROLE_PERIPHERAL_ONLY);
        OobData defaultLeOobData = leBuilder.build();

        assertArrayEquals(confirmationHash, defaultLeOobData.getConfirmationHash());
        assertArrayEquals(address, defaultLeOobData.getDeviceAddressWithType());
        assertEquals(OobData.LE_DEVICE_ROLE_PERIPHERAL_ONLY, defaultLeOobData.getLeDeviceRole());
        assertArrayEquals(defaultClassicLength, defaultLeOobData.getClassicLength());
        assertArrayEquals(defaultRandomizerHash, defaultLeOobData.getRandomizerHash());
        assertArrayEquals(defaultClassOfDevice, defaultLeOobData.getClassOfDevice());
        assertArrayEquals(defaultDeviceName, defaultLeOobData.getDeviceName());
        assertArrayEquals(defaultLeTemporaryKey, defaultLeOobData.getLeTemporaryKey());
        assertArrayEquals(defaultLeAppearance, defaultLeOobData.getLeAppearance());
        assertEquals(OobData.LE_FLAG_GENERAL_DISCOVERY_MODE, defaultLeOobData.getLeFlags());

        // Test setting optional classic OobData fields and verifying values set properly
        String deviceNameString = "Test Device Name";
        byte[] deviceName = deviceNameString.getBytes();
        byte[] randomizerHash = new byte[] {(byte) 0x9E, 0x43, 0x51, 0x10, 0x70, 0x33, 0x01,
                (byte) 0xDE, 0x00, 0x02, 0x03, 0x05, 0x09, 0x10, 0x40, 0x07};
        byte[] leTemporaryKey = new byte[] {0x01, 0x12, 0x34, 0x56, 0x78, (byte) 0x9A, (byte) 0xBC,
                (byte) 0xDE, (byte) 0xF0, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77};

        leBuilder
                .setDeviceName(deviceName)
                .setRandomizerHash(randomizerHash)
                .setLeTemporaryKey(leTemporaryKey)
                .setLeFlags(OobData.LE_FLAG_BREDR_NOT_SUPPORTED);
        OobData leData = leBuilder.build();

        assertArrayEquals(deviceName, leData.getDeviceName());
        assertArrayEquals(randomizerHash, leData.getRandomizerHash());
        assertArrayEquals(leTemporaryKey, leData.getLeTemporaryKey());
        assertEquals(OobData.LE_FLAG_BREDR_NOT_SUPPORTED, leData.getLeFlags());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void testToString() {
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        byte[] confirmationHash = new byte[]{0x52, 0x70, 0x49, 0x41, 0x1A, (byte) 0xB3, 0x3F, 0x5C,
                (byte) 0xE0, (byte) 0x99, 0x37, 0x29, 0x21, 0x52, 0x65, 0x49};
        byte[] address = new byte[]{0x12, 0x34, 0x56, 0x78, (byte) 0x8A, (byte) 0xBC, 0x0};

        OobData.LeBuilder leBuilder = new OobData.LeBuilder(confirmationHash, address,
                OobData.LE_DEVICE_ROLE_PERIPHERAL_ONLY);

        String deviceNameString = "Test Device Name";
        byte[] deviceName = deviceNameString.getBytes();
        byte[] randomizerHash = new byte[]{(byte) 0x9E, 0x43, 0x51, 0x10, 0x70, 0x33, 0x01,
                (byte) 0xDE, 0x00, 0x02, 0x03, 0x05, 0x09, 0x10, 0x40, 0x07};
        byte[] leTemporaryKey = new byte[]{0x01, 0x12, 0x34, 0x56, 0x78, (byte) 0x9A, (byte) 0xBC,
                (byte) 0xDE, (byte) 0xF0, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77};

        leBuilder
                .setDeviceName(deviceName)
                .setRandomizerHash(randomizerHash)
                .setLeTemporaryKey(leTemporaryKey)
                .setLeFlags(OobData.LE_FLAG_BREDR_NOT_SUPPORTED);
        OobData leData = leBuilder.build();

        String expected = "OobData: \n\t"
                + "Device Address With Type: "
                + toHexString(leData.getDeviceAddressWithType()) + "\n\t"
                + "Confirmation: " + toHexString(leData.getConfirmationHash()) + "\n\t"
                + "Randomizer: " + toHexString(leData.getRandomizerHash()) + "\n\t"
                + "Device Name: " + toHexString(leData.getDeviceName()) + "\n\t"
                + "OobData Length: " + toHexString(leData.getClassicLength()) + "\n\t"
                + "Class of Device: " + toHexString(leData.getClassOfDevice()) + "\n\t"
                + "LE Device Role: " + toHexString(leData.getLeDeviceRole()) + "\n\t"
                + "LE Temporary Key: " + toHexString(leData.getLeTemporaryKey()) + "\n\t"
                + "LE Appearance: " + toHexString(leData.getLeAppearance()) + "\n\t"
                + "LE Flags: " + toHexString(leData.getLeFlags()) + "\n\t";
        String toString = leData.toString();

        assertThat(toString).isEqualTo(expected);

        int describeContents = 0;
        assertThat(leData.describeContents()).isEqualTo(describeContents);
    }

    private String toHexString(int b) {
        return toHexString(new byte[]{(byte) b});
    }

    private String toHexString(byte[] array) {
        if (array == null) return "null";
        StringBuilder builder = new StringBuilder(array.length * 2);
        for (byte b : array) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
