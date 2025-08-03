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

package android.net.wifi.p2p;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;

/**
 * Interface for Wi-Fi p2p listener.
 * @hide
 */
oneway interface IWifiP2pListener
{
    void onStateChanged(boolean p2pEnabled);
    void onDiscoveryStateChanged(boolean started);
    void onListenStateChanged(boolean started);
    void onDeviceConfigurationChanged(in WifiP2pDevice p2pDevice);
    void onPeerListChanged(in WifiP2pDeviceList p2pDeviceList);
    void onPersistentGroupsChanged(in WifiP2pGroupList p2pGroupList);
    void onGroupCreating();
    void onGroupNegotiationRejectedByUser();
    void onGroupCreationFailed();
    void onGroupCreated(in WifiP2pInfo p2pInfo, in WifiP2pGroup p2pGroup);
    void onPeerClientJoined(in WifiP2pInfo p2pInfo, in WifiP2pGroup p2pGroup);
    void onPeerClientDisconnected(in WifiP2pInfo p2pInfo, in WifiP2pGroup p2pGroup);
    void onFrequencyChanged(in WifiP2pInfo p2pInfo, in WifiP2pGroup p2pGroup);
    void onGroupRemoved();
}
