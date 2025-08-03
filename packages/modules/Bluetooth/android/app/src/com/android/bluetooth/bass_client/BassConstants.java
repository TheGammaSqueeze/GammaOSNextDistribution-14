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

package com.android.bluetooth.bass_client;

import android.os.ParcelUuid;

import java.util.UUID;

/**
 * Broadcast Audio Scan Service constants class
 */
public class BassConstants {
    public static final boolean BASS_DBG = true;
    public static final ParcelUuid BAAS_UUID =
            ParcelUuid.fromString("00001852-0000-1000-8000-00805F9B34FB");
    public static final UUID BASS_UUID =
            UUID.fromString("0000184F-0000-1000-8000-00805F9B34FB");
    public static final UUID BASS_BCAST_AUDIO_SCAN_CTRL_POINT =
            UUID.fromString("00002BC7-0000-1000-8000-00805F9B34FB");
    public static final UUID BASS_BCAST_RECEIVER_STATE =
            UUID.fromString("00002BC8-0000-1000-8000-00805F9B34FB");
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final ParcelUuid BASIC_AUDIO_UUID =
            ParcelUuid.fromString("00001851-0000-1000-8000-00805F9B34FB");
    public static final ParcelUuid PUBLIC_BROADCAST_UUID =
            ParcelUuid.fromString("00001856-0000-1000-8000-00805F9B34FB");

    public static final int INVALID_SYNC_HANDLE = -1;
    public static final int INVALID_ADV_SID = -1;
    public static final int INVALID_ADV_ADDRESS_TYPE = -1;
    public static final int INVALID_ADV_INTERVAL = -1;
    public static final int INVALID_BROADCAST_ID = -1;
    public static final int BROADCAST_ASSIST_ADDRESS_TYPE_PUBLIC = 0;
    public static final int INVALID_SOURCE_ID = -1;
    public static final int ADV_ADDRESS_DONT_MATCHES_EXT_ADV_ADDRESS = 0x00000001;
    public static final int ADV_ADDRESS_DONT_MATCHES_SOURCE_ADV_ADDRESS = 0x00000002;
    // types of command for select and add Broadcast source operations
    public static final int AUTO = 1;
    public static final int USER = 2;
    public static final int BASS_MAX_BYTES = 100;
    // broadcast receiver state indices
    public static final int BCAST_RCVR_STATE_SRC_ID_IDX = 0;
    public static final int BCAST_RCVR_STATE_SRC_ADDR_TYPE_IDX = 1;
    public static final int BCAST_RCVR_STATE_SRC_ADDR_START_IDX = 2;
    public static final int BCAST_RCVR_STATE_SRC_BCAST_ID_START_IDX = 9;
    public static final int BCAST_RCVR_STATE_SRC_ADDR_SIZE = 6;
    public static final int BCAST_RCVR_STATE_SRC_ADV_SID_IDX = 8;
    public static final int BCAST_RCVR_STATE_PA_SYNC_IDX = 12;
    public static final int BCAST_RCVR_STATE_ENC_STATUS_IDX = 13;
    public static final int BCAST_RCVR_STATE_BADCODE_START_IDX = 14;
    public static final int BCAST_RCVR_STATE_BADCODE_SIZE = 16;
    public static final int BCAST_RCVR_STATE_BIS_SYNC_SIZE = 4;
    // 30 secs time out for all gatt writes
    public static final int GATT_TXN_TIMEOUT_MS = 30000;
    // 3 min time out for keeping PSYNC active
    public static final int PSYNC_ACTIVE_TIMEOUT_MS = 3 * 60000;
    // 2 secs time out achieving psync
    public static final int PSYNC_TIMEOUT = 200;
    public static final int PIN_CODE_CMD_LEN = 18;
    public static final int CONNECT_TIMEOUT_MS = 30000;
    // broadcast name AD type and length
    public static final int BCAST_NAME_AD_TYPE = 0x30;
    public static final int BCAST_NAME_LEN_MIN = 4;
    public static final int BCAST_NAME_LEN_MAX = 32;
}
