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

import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.util.Log;

import java.util.Arrays;

/**
 * Helper class to parse the Public Broadcast Announcement data
 */
class PublicBroadcastData {
    private static final String TAG = "Bassclient.PublicBroadcastData";
    private static final int FEATURES_ENCRYPTION_BIT = 0x01 << 0;
    private static final int FEATURES_STANDARD_QUALITY_BIT = 0x01 << 1;
    private static final int FEATURES_HIGH_QUALITY_BIT = 0x01 << 2;
    // public announcement service data should at least include features and metadata length
    private static final int PUBLIC_BROADCAST_SERVICE_DATA_LEN_MIN = 2;

    private final PublicBroadcastInfo mPublicBroadcastInfo;

    public static class PublicBroadcastInfo {
        public byte metaDataLength;
        public byte[] metaData;
        public boolean isEncrypted;
        public int audioConfigQuality;

        PublicBroadcastInfo() {
            metaDataLength = 0;
            metaData = new byte[0];
            isEncrypted = false;
            audioConfigQuality = BluetoothLeBroadcastMetadata.AUDIO_CONFIG_QUALITY_NONE;
            log("PublicBroadcastInfo is Initialized");
        }

        void print() {
            log("**BEGIN: Public Broadcast Information**");
            log("encrypted: " + isEncrypted);
            log("audio config quality: " + audioConfigQuality);
            log("metaDataLength: " + metaDataLength);
            if (metaDataLength != (byte) 0) {
                log("metaData: " + Arrays.toString(metaData));
            }
            log("**END: Public Broadcast Information****");
        }
    }

    PublicBroadcastData(PublicBroadcastInfo publicBroadcastInfo) {
        mPublicBroadcastInfo = publicBroadcastInfo;
    }

    static PublicBroadcastData parsePublicBroadcastData(byte[] serviceData) {
        if (serviceData == null || serviceData.length < PUBLIC_BROADCAST_SERVICE_DATA_LEN_MIN) {
            Log.e(TAG, "Invalid service data for PublicBroadcastData construction");
            throw new IllegalArgumentException("PublicBroadcastData: serviceData is invalid");
        }
        PublicBroadcastInfo publicBroadcastInfo = new PublicBroadcastInfo();

        log("PublicBroadcast input" + Arrays.toString(serviceData));

        int offset = 0;
        // Parse Public broadcast announcement features
        int features = serviceData[offset++];
        publicBroadcastInfo.isEncrypted =
                ((features & FEATURES_ENCRYPTION_BIT) != 0) ? true : false;
        publicBroadcastInfo.audioConfigQuality =
                BluetoothLeBroadcastMetadata.AUDIO_CONFIG_QUALITY_NONE;
        if ((features & FEATURES_STANDARD_QUALITY_BIT) != 0) {
            publicBroadcastInfo.audioConfigQuality |=
                    BluetoothLeBroadcastMetadata.AUDIO_CONFIG_QUALITY_STANDARD;
        }
        if ((features & FEATURES_HIGH_QUALITY_BIT) != 0) {
            publicBroadcastInfo.audioConfigQuality |=
                    BluetoothLeBroadcastMetadata.AUDIO_CONFIG_QUALITY_HIGH;
        }

        // Parse Public broadcast announcement metadata
        publicBroadcastInfo.metaDataLength = serviceData[offset++];
        if (serviceData.length
                != (publicBroadcastInfo.metaDataLength + PUBLIC_BROADCAST_SERVICE_DATA_LEN_MIN)) {
            Log.e(TAG, "Invalid meta data length for PublicBroadcastData construction");
            throw new IllegalArgumentException("PublicBroadcastData: metaData is invalid");
        }
        if (publicBroadcastInfo.metaDataLength != 0) {
            publicBroadcastInfo.metaData = new byte[(int) publicBroadcastInfo.metaDataLength];
            System.arraycopy(serviceData, offset,
                    publicBroadcastInfo.metaData, 0, (int) publicBroadcastInfo.metaDataLength);
            offset += publicBroadcastInfo.metaDataLength;
        }
        publicBroadcastInfo.print();
        return new PublicBroadcastData(publicBroadcastInfo);
    }

    boolean isEncrypted() {
        return mPublicBroadcastInfo.isEncrypted;
    }

    int getAudioConfigQuality() {
        return mPublicBroadcastInfo.audioConfigQuality;
    }

    int getMetadataLength() {
        return mPublicBroadcastInfo.metaDataLength;
    }

    byte[] getMetadata() {
        return mPublicBroadcastInfo.metaData;
    }

    void print() {
        mPublicBroadcastInfo.print();
    }

    static void log(String msg) {
        if (BassConstants.BASS_DBG) {
            Log.d(TAG, msg);
        }
    }
}
