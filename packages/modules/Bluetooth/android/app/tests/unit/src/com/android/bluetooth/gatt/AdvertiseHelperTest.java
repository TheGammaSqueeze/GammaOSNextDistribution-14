/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.gatt;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.TransportDiscoveryData;
import android.os.ParcelUuid;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

/**
 * Test cases for {@link AdvertiseHelper}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AdvertiseHelperTest {

    @Test
    public void advertiseDataToBytes() throws Exception {
        byte[] emptyBytes = AdvertiseHelper.advertiseDataToBytes(null, "");

        assertThat(emptyBytes.length).isEqualTo(0);

        int manufacturerId = 1;
        byte[] manufacturerData = new byte[]{
                0x30, 0x31, 0x32, 0x34
        };

        byte[] serviceData = new byte[]{
                0x10, 0x12, 0x14
        };

        byte[] transportDiscoveryData = new byte[]{
                0x40, 0x44, 0x48
        };

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addManufacturerData(manufacturerId, manufacturerData)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(new ParcelUuid(UUID.randomUUID()))
                .addServiceData(new ParcelUuid(UUID.randomUUID()), serviceData)
                .addServiceSolicitationUuid(new ParcelUuid(UUID.randomUUID()))
                .addTransportDiscoveryData(new TransportDiscoveryData(transportDiscoveryData))
                .build();
        String deviceName = "TestDeviceName";

        int expectedAdvDataBytesLength = 86;
        byte[] advDataBytes = AdvertiseHelper.advertiseDataToBytes(advertiseData, deviceName);

        String deviceNameLong = "TestDeviceNameLongTestDeviceName";

        assertThat(advDataBytes.length).isEqualTo(expectedAdvDataBytesLength);

        int expectedAdvDataBytesLongNameLength = 98;
        byte[] advDataBytesLongName = AdvertiseHelper
                .advertiseDataToBytes(advertiseData, deviceNameLong);

        assertThat(advDataBytesLongName.length).isEqualTo(expectedAdvDataBytesLongNameLength);
    }

    @Test
    public void checkLength_withGT255_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AdvertiseHelper.check_length(0X00, 256)
        );
    }
}