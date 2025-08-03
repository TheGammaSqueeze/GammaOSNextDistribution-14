/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.mcp;

import android.bluetooth.BluetoothDevice;

import java.util.Map;
import java.util.UUID;

/**
 * Media Control Service interface. These are sent Media Players => GATT Servers
 */
public interface MediaControlGattServiceInterface {
    /**
     * Track position unavailable definition
     */
    static final long TRACK_POSITION_UNAVAILABLE = -1L;

    /**
     * Track duration unavailable definition
     */
    static final long TRACK_DURATION_UNAVAILABLE = -1L;

    /**
     * API for Media Control Profile service control
     */
    void updatePlaybackState(MediaState state);
    void updatePlayerState(Map stateFields);
    void updateObjectID(int objField, long objectId);
    void setMediaControlRequestResult(Request request,
            Request.Results resultStatus);
    void setSearchRequestResult(SearchRequest request,
            SearchRequest.Results resultStatus, long resultObjectId);
    int getContentControlId();
    UUID getServiceUuid();
    void onDeviceAuthorizationSet(BluetoothDevice device);
    void destroy();
    void dump(StringBuilder sb);
}
