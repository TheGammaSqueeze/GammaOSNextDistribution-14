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

package com.android.bluetooth.leaudio;

import android.app.Application;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastSettings;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

public class BroadcasterViewModel extends AndroidViewModel {
    private final BluetoothProxy mBluetooth;
    private final Application mApplication;

    public BroadcasterViewModel(@NonNull Application application) {
        super(application);
        mApplication = application;

        mBluetooth = BluetoothProxy.getBluetoothProxy(application);
        mBluetooth.initProfiles();
    }

    public boolean startBroadcast(BluetoothLeBroadcastSettings settings) {
        return mBluetooth.startBroadcast(settings);
    }

    public boolean stopBroadcast(int broadcastId) {
        return mBluetooth.stopBroadcast(broadcastId);
    }

    public boolean updateBroadcast(int broadcastId, BluetoothLeBroadcastSettings settings) {
        return mBluetooth.updateBroadcast(broadcastId, settings);
    }

    public int getMaximumNumberOfBroadcast() {
        return mBluetooth.getMaximumNumberOfBroadcast();
    }

    public List<BluetoothLeBroadcastMetadata> getAllBroadcastMetadata() {
        return mBluetooth.getAllLocalBroadcasts();
    }

    public int getBroadcastCount() {
        return mBluetooth.getAllLocalBroadcasts().size();
    }

    public LiveData<BluetoothLeBroadcastMetadata> getBroadcastUpdateMetadataLive() {
        return mBluetooth.getBroadcastUpdateMetadataLive();
    }

    public LiveData<Pair<Integer /* reason */, Integer /* broadcastId */>> getBroadcastPlaybackStartedMutableLive() {
        return mBluetooth.getBroadcastPlaybackStartedMutableLive();
    }

    public LiveData<Pair<Integer /* reason */, Integer /* broadcastId */>> getBroadcastPlaybackStoppedMutableLive() {
        return mBluetooth.getBroadcastPlaybackStoppedMutableLive();
    }

    public LiveData<Integer /* broadcastId */> getBroadcastAddedMutableLive() {
        return mBluetooth.getBroadcastAddedMutableLive();
    }

    public LiveData<Pair<Integer /* reason */, Integer /* broadcastId */>> getBroadcastRemovedMutableLive() {
        return mBluetooth.getBroadcastRemovedMutableLive();
    }

    public LiveData<String> getBroadcastStatusMutableLive() {
        return mBluetooth.getBroadcastStatusMutableLive();
    }

    @Override
    public void onCleared() {}
}
