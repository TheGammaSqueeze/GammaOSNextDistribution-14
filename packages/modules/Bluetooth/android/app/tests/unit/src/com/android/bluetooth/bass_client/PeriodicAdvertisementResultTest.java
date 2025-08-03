/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.bluetooth.bass_client;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeBroadcastMetadata;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PeriodicAdvertisementResultTest {
    private static final String REMOTE_DEVICE_ADDRESS = "00:01:02:03:04:05";
    private static final String TEST_BROADCAST_NAME = "Test";

    BluetoothDevice mDevice;

    @Before
    public void setUp() {
        mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(REMOTE_DEVICE_ADDRESS);
    }

    @Test
    public void constructor() {
        int addressType = 1;
        int syncHandle = 2;
        int advSid = 3;
        int paInterval = 4;
        int broadcastId = 5;
        PublicBroadcastData pbData = generatePublicBroadcastData();
        String broadcastName = TEST_BROADCAST_NAME;
        PeriodicAdvertisementResult result = new PeriodicAdvertisementResult(
                mDevice, addressType, syncHandle, advSid, paInterval, broadcastId,
                pbData, broadcastName);

        assertThat(result.getAddressType()).isEqualTo(addressType);
        assertThat(result.getSyncHandle()).isEqualTo(syncHandle);
        assertThat(result.getAdvSid()).isEqualTo(advSid);
        assertThat(result.getAdvInterval()).isEqualTo(paInterval);
        assertThat(result.getBroadcastId()).isEqualTo(broadcastId);
        assertThat(result.getPublicBroadcastData()).isEqualTo(pbData);
        assertThat(result.getBroadcastName()).isEqualTo(broadcastName);
    }

    @Test
    public void updateMethods() {
        int addressType = 1;
        int syncHandle = 2;
        int advSid = 3;
        int paInterval = 4;
        int broadcastId = 5;
        PublicBroadcastData pbData = null;
        String broadcastName = null;
        PeriodicAdvertisementResult result = new PeriodicAdvertisementResult(
                mDevice, addressType, syncHandle, advSid, paInterval, broadcastId,
                pbData, broadcastName);

        int newAddressType = 6;
        result.updateAddressType(newAddressType);
        assertThat(result.getAddressType()).isEqualTo(newAddressType);

        int newSyncHandle = 7;
        result.updateSyncHandle(newSyncHandle);
        assertThat(result.getSyncHandle()).isEqualTo(newSyncHandle);

        int newAdvSid = 8;
        result.updateAdvSid(newAdvSid);
        assertThat(result.getAdvSid()).isEqualTo(newAdvSid);

        int newAdvInterval = 9;
        result.updateAdvInterval(newAdvInterval);
        assertThat(result.getAdvInterval()).isEqualTo(newAdvInterval);

        int newBroadcastId = 10;
        result.updateBroadcastId(newBroadcastId);
        assertThat(result.getBroadcastId()).isEqualTo(newBroadcastId);

        PublicBroadcastData newPbData = generatePublicBroadcastData();
        result.updatePublicBroadcastData(newPbData);
        assertThat(result.getPublicBroadcastData()).isEqualTo(newPbData);

        String newBroadcastName = TEST_BROADCAST_NAME;
        result.updateBroadcastName(newBroadcastName);
        assertThat(result.getBroadcastName()).isEqualTo(newBroadcastName);
    }

    @Test
    public void print_doesNotCrash() {
        int addressType = 1;
        int syncHandle = 2;
        int advSid = 3;
        int paInterval = 4;
        int broadcastId = 5;
        PublicBroadcastData pbData = generatePublicBroadcastData();
        String broadcastName = TEST_BROADCAST_NAME;
        PeriodicAdvertisementResult result = new PeriodicAdvertisementResult(
                mDevice, addressType, syncHandle, advSid, paInterval, broadcastId,
                pbData, broadcastName);

        result.print();
    }

    /**
     * Helper to generate test data for public broadcast.
     */
    private PublicBroadcastData generatePublicBroadcastData() {
        PublicBroadcastData.PublicBroadcastInfo info =
                new PublicBroadcastData.PublicBroadcastInfo();
        info.isEncrypted = true;
        info.audioConfigQuality = (
                BluetoothLeBroadcastMetadata.AUDIO_CONFIG_QUALITY_STANDARD |
                BluetoothLeBroadcastMetadata.AUDIO_CONFIG_QUALITY_HIGH);
        info.metaDataLength = 3;
        info.metaData = new byte[] { 0x06, 0x07, 0x08 };
        return new PublicBroadcastData(info);
    }
}
