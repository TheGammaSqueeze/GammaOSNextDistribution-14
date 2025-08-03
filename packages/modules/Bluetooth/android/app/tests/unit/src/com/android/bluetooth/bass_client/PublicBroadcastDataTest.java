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

import static org.junit.Assert.assertThrows;

import android.bluetooth.BluetoothLeBroadcastMetadata;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PublicBroadcastDataTest {

    @Test
    public void publicBroadcastInfo() {
        PublicBroadcastData.PublicBroadcastInfo info =
                new PublicBroadcastData.PublicBroadcastInfo();

        info.print();

        info.isEncrypted = true;
        info.audioConfigQuality = (
                BluetoothLeBroadcastMetadata.AUDIO_CONFIG_QUALITY_STANDARD |
                BluetoothLeBroadcastMetadata.AUDIO_CONFIG_QUALITY_HIGH);
        info.metaDataLength = 3;
        info.metaData = new byte[] { 0x06, 0x07, 0x08 };
        info.print();
    }

    @Test
    public void parsePublicBroadcastData() {
        assertThrows(IllegalArgumentException.class, () ->
                PublicBroadcastData.parsePublicBroadcastData(null));

        byte[] serviceDataInvalid = new byte[] {
                (byte) 0x02, // features, non-encrypted, standard quality prsent
        };
        assertThrows(IllegalArgumentException.class, () ->
                PublicBroadcastData.parsePublicBroadcastData(serviceDataInvalid));

        byte[] serviceDataInvalid2 = new byte[] {
                (byte) 0x02, // features, non-encrypted, standard quality prsent
                (byte) 0x03,  // metaDataLength
                (byte) 0x06, (byte) 0x07,  // invalid metaData
        };
        assertThrows(IllegalArgumentException.class, () ->
                PublicBroadcastData.parsePublicBroadcastData(serviceDataInvalid2));

        byte[] serviceData = new byte[] {
                (byte) 0x07, // features
                (byte) 0x03,  // metaDataLength
                (byte) 0x06, (byte) 0x07, (byte) 0x08,  // metaData
        };
        PublicBroadcastData data = PublicBroadcastData.parsePublicBroadcastData(serviceData);
        assertThat(data.isEncrypted()).isTrue();
        assertThat(data.getAudioConfigQuality()).isEqualTo(3);
        assertThat(data.getMetadataLength()).isEqualTo(3);
        assertThat(data.getMetadata()).isEqualTo(new byte[] { 0x06, 0x07, 0x08 });

        byte[] serviceDataNoMetaData = new byte[] {
                (byte) 0x02, // features, non-encrypted, standard quality prsent
                (byte) 0x00,  // metaDataLength
        };
        PublicBroadcastData dataNoMetaData =
                PublicBroadcastData.parsePublicBroadcastData(serviceDataNoMetaData);
        assertThat(dataNoMetaData.isEncrypted()).isFalse();
        assertThat(dataNoMetaData.getAudioConfigQuality()).isEqualTo(1);
        assertThat(dataNoMetaData.getMetadataLength()).isEqualTo(0);
        assertThat(dataNoMetaData.getMetadata()).isEqualTo(new byte[] {});
    }
}
