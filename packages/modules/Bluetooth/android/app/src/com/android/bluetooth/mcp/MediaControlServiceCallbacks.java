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

/**
 * Media Control Service callback interface. These callbacks are sent GATT servers => Media Players
 */
interface MediaControlServiceCallbacks {
    void onServiceInstanceRegistered(ServiceStatus status, MediaControlGattServiceInterface serviceProxy);
    void onServiceInstanceUnregistered(ServiceStatus status);
    void onMediaControlRequest(Request request);
    void onSearchRequest(SearchRequest request);
    void onSetObjectIdRequest(int objField, long objectId);
    void onTrackPositionSetRequest(long position);
    void onPlaybackSpeedSetRequest(float speed);
    void onPlayingOrderSetRequest(int order);
    void onCurrentTrackObjectIdSet(long objectId);
    void onNextTrackObjectIdSet(long objectId);
    void onCurrentGroupObjectIdSet(long objectId);
    void onCurrentTrackMetadataRequest();
    void onPlayerStateRequest(PlayerStateField[] stateFields);
    long onGetFeatureFlags();
    long onGetCurrentTrackPosition();
}
