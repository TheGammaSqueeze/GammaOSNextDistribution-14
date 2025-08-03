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
package com.android.server.uwb.jni;

import com.android.server.uwb.data.UwbMulticastListUpdateStatus;
import com.android.server.uwb.data.UwbRadarData;
import com.android.server.uwb.data.UwbRangingData;

/*import com.android.server.uwb.test.UwbTestLoopBackTestResult;
import com.android.server.uwb.test.UwbTestPeriodicTxResult;
import com.android.server.uwb.test.UwbTestRxPacketErrorRateResult;
import com.android.server.uwb.test.UwbTestRxResult;*/

public interface INativeUwbManager {
    /**
     * Notifies transaction
     */
    interface SessionNotification {
        /**
         * Interface for receiving Ranging Data Notification
         *
         * @param rangingData : refer to UCI GENERIC SPECIFICATION Table 22:Ranging Data
         *                    Notification
         */
        void onRangeDataNotificationReceived(UwbRangingData rangingData);

        /**
         * Interface for receiving Session Status Notification
         *
         * @param id         : Session ID
         * @param state      : Session State
         * @param reasonCode : Reason Code - UCI GENERIC SPECIFICATION Table 15 : state change with
         *                   reason codes
         */
        void onSessionStatusNotificationReceived(long id, int state, int reasonCode);

        /**
         * Interface for receiving Multicast List Update Data
         *
         * @param multicastListUpdateData : refer to SESSION_UPDATE_CONTROLLER_MULTICAST_LIST_NTF
         */
        void onMulticastListUpdateNotificationReceived(
                UwbMulticastListUpdateStatus multicastListUpdateData);

        /**
         * Interface for receiving data from remote device
         *
         * @param sessionID   : Session ID
         * @param status      : Status
         * @param sequenceNum : Sequence Number
         * @param address     : Address of remote address
         * @param data        : Data received from remote address
         */
        // TODO(b/261762781): Change the type of sessionID & sequenceNum parameters to int (to match
        // their 4-octet size in the UCI spec).
        void onDataReceived(
                long sessionID, int status, long sequenceNum, byte[] address, byte[] data);

        /**
         * Interface for receiving the data transfer status, corresponding to a Data packet
         * earlier sent from the host to UWBS.
         *
         * @param sessionId          : Session ID
         * @param dataTransferStatus : Status codes in the DATA_TRANSFER_STATUS_NTF packet
         * @param sequenceNum        : Sequence Number
         * @param txCount            : Transmission count
         */
        void onDataSendStatus(long sessionId, int dataTransferStatus, long sequenceNum,
                int txCount);

        /**
         * Interface for receiving Radar Data Message
         *
         * @param radarData : refer to Android UWB Radar UCI Specification: radar Data Message
         */
        void onRadarDataMessageReceived(UwbRadarData radarData);

        /**
         * Interface for receiving the data transfer phase config notification
         *
         * @param sessionId                     : Session ID
         * @param dataTransferPhaseConfigStatus  : DATA_TRANSFER_PHASE_CONFIG_STATUS_NTF status code
         */
        void onDataTransferPhaseConfigNotificationReceived(long sessionId,
                int dataTransferPhaseConfigStatus);
    }

    interface DeviceNotification {
        /**
         * Interface for receiving Device Status Notification
         *
         * @param state     : refer to UCI GENERIC SPECIFICATION Table 9: Device Status Notification
         * @param chipId    : identifier of UWB chip for multi-HAL devices
         */
        void onDeviceStatusNotificationReceived(int state, String chipId);

        /**
         * Interface for receiving Control Message for Generic Error
         *
         * @param status : refer to UCI GENERIC SPECIFICATION Table 12: Control Message for Generic
         *               Error
         * @param chipId : identifier of UWB chip for multi-HAL devices
         */
        void onCoreGenericErrorNotificationReceived(int status, String chipId);
    }

    interface VendorNotification {
        /**
         * Interface for receiving Vendor UCI notifications.
         */
        void onVendorUciNotificationReceived(int gid, int oid, byte[] payload);
    }
    /* Unused now */
    /*interface RfTestNotification {
        void onPeriodicTxDataNotificationReceived(UwbTestPeriodicTxResult periodicTxData);
        void onPerRxDataNotificationReceived(UwbTestRxPacketErrorRateResult perRxData);
        void onLoopBackTestDataNotificationReceived(UwbTestLoopBackTestResult uwbLoopBackData);
        void onRxTestDataNotificationReceived(UwbTestRxResult rxData);
    }*/
}
