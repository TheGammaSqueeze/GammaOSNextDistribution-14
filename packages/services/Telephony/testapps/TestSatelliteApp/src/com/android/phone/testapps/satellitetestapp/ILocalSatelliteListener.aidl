
/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.phone.testapps.satellitetestapp;

import android.telephony.satellite.stub.SatelliteDatagram;

/**
 * {@hide}
 */
oneway interface ILocalSatelliteListener {
    /**
     * Indicates that the remote service - SatelliteModemInterface - has successfully connected to
     * the TestSatelliteService.
     */
    void onRemoteServiceConnected();

    /**
     * Indicates that TestSatelliteService has just received the request
     * startSendingSatellitePointingInfo from Telephony.
     */
    void onStartSendingSatellitePointingInfo();

    /**
     * Indicates that TestSatelliteService has just received the request
     * stopSendingSatellitePointingInfo from Telephony.
     */
    void onStopSendingSatellitePointingInfo();

    /**
     * Indicates that TestSatelliteService has just received the request
     * pollPendingSatelliteDatagrams from Telephony.
     */
    void onPollPendingSatelliteDatagrams();

    /**
     * Indicates that TestSatelliteService has just received the request
     * sendSatelliteDatagram from Telephony.
     */
    void onSendSatelliteDatagram(in SatelliteDatagram datagram, in boolean isEmergency);

    /**
     * Indicates that TestSatelliteService has just received the request
     * requestSatelliteListeningEnabled from Telephony.
     */
    void onSatelliteListeningEnabled(in boolean enabled);

    /**
     * Indicates that TestSatelliteService has just received the request
     * enableCellularModemWhileSatelliteModeIsOn from Telephony.
     */
    void onEnableCellularModemWhileSatelliteModeIsOn(in boolean enable);

    /**
     * Indicates that MockSatelliteService has just received the request
     * setSatellitePlmn from Telephony.
     */
    void onSetSatellitePlmn();
}
