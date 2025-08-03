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
package com.android.server.uwb.indev;

import com.android.server.uwb.data.UwbRangingData;


/**
 * Callback from Uwb Service
 */
interface IUwbServiceListener {
    /**
     * Interface for receiving a signal that core Uwb Service has been reset
     * due to internal error. All Uwb sessions are closed
     *
     * @param success : Indicates whether the reset was successful
     */
    void onServiceResetReceived(boolean success);

    /**
     * Interface for receiving Ranging Data Notification
     *
     * @param sessionId   : Session ID
     * @param rangingData : refer to UCI GENERIC SPECIFICATION Table 22:Ranging Data
     *                    Notification
     */
    void onRangeDataNotificationReceived(long sessionId, UwbRangingData rangingData);

    /**
     * Interface for receiving Session Status Notification
     *
     * @param sessionId         : Session ID
     * @param state      : Session State
     * @param reasonCode : Reason Code - UCI GENERIC SPECIFICATION Table 15 : state change with
     *                   reason codes
     */
    void onSessionStatusNotificationReceived(long sessionId, int state, int reasonCode);

    /**
     * Interface for receiving Device Status Notification
     *
     * @param state : refer to UCI GENERIC SPECIFICATION Table 9: Device Status Notification
     */
    void onDeviceStatusNotificationReceived(int state);

    /**
     * Interface for receiving Vendor UCI notifications.
     *
     * @param gid     : Group Identifier
     * @param oid     : Opcode Identifier
     * @param payload : Payload
     */
    void onVendorUciNotificationReceived(int gid, int oid, byte[] payload);
}
