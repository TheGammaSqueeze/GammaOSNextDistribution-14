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
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeBroadcastMetadata;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

public class LeAudioViewModel extends AndroidViewModel {
    private final BluetoothProxy bluetoothProxy;

    public LeAudioViewModel(@NonNull Application application) {
        super(application);
        bluetoothProxy = BluetoothProxy.getBluetoothProxy(application);
        bluetoothProxy.initProfiles();
    }

    @Override
    public void onCleared() {
        bluetoothProxy.cleanupProfiles();
    }

    public void queryDevices() {
        bluetoothProxy.queryLeAudioDevices();
    }

    public void connectLeAudio(BluetoothDevice device, boolean connect) {
        bluetoothProxy.connectLeAudio(device, connect);
    }

    public void streamAction(Integer group_id, int action, Integer content_type) {
        bluetoothProxy.streamAction(group_id, action, content_type);
    }

    public void groupSet(BluetoothDevice device, Integer group_id) {
        bluetoothProxy.groupSet(device, group_id);
    }

    public void groupUnset(BluetoothDevice device, Integer group_id) {
        bluetoothProxy.groupUnset(device, group_id);
    }

    public void groupSetLock(Integer group_id, boolean lock) {
        bluetoothProxy.groupSetLock(group_id, lock);
    }

    public void setVolume(BluetoothDevice device, int volume) {
        bluetoothProxy.setVolume(device, volume);
    }

    public void connectHap(BluetoothDevice device, boolean connect) {
        bluetoothProxy.connectHap(device, connect);
    }

    public void hapReadPresetInfo(BluetoothDevice device, int preset_index) {
        bluetoothProxy.hapReadPresetInfo(device, preset_index);
    }

    public void hapSetActivePreset(BluetoothDevice device, int preset_index) {
        bluetoothProxy.hapSetActivePreset(device, preset_index);
    }

    public void hapSetActivePresetForGroup(BluetoothDevice device, int preset_index) {
        bluetoothProxy.hapSetActivePresetForGroup(device, preset_index);
    }

    public void hapChangePresetName(BluetoothDevice device, int preset_index, String name) {
        bluetoothProxy.hapChangePresetName(device, preset_index, name);
    }

    public void hapPreviousDevicePreset(BluetoothDevice device) {
        bluetoothProxy.hapPreviousDevicePreset(device);
    }

    public void hapNextDevicePreset(BluetoothDevice device) {
        bluetoothProxy.hapNextDevicePreset(device);
    }

    public boolean hapPreviousGroupPreset(int group_id) {
        return bluetoothProxy.hapPreviousGroupPreset(group_id);
    }

    public boolean hapNextGroupPreset(int group_id) {
        return bluetoothProxy.hapNextGroupPreset(group_id);
    }

    public int hapGetHapGroup(BluetoothDevice device) {
        return bluetoothProxy.hapGetHapGroup(device);
    }

    public LiveData<Boolean> getBluetoothEnabledLive() {
        return bluetoothProxy.getBluetoothEnabled();
    }

    public LiveData<List<LeAudioDeviceStateWrapper>> getAllLeAudioDevicesLive() {
        return bluetoothProxy.getAllLeAudioDevices();
    }

    public boolean isLeAudioBroadcastSourceSupported() {
        return bluetoothProxy.isLeAudioBroadcastSourceSupported();
    }

    public void connectBass(BluetoothDevice sink, boolean connect) {
        bluetoothProxy.connectBass(sink, connect);
    }

    public boolean stopBroadcastObserving() {
        return bluetoothProxy.stopBroadcastObserving();
    }

    // TODO: Uncomment this method if necessary
//    public boolean getBroadcastReceiverState(BluetoothDevice device, int receiver_id) {
//        return bluetoothProxy.getBroadcastReceiverState(device, receiver_id);
//    }

    // TODO: Uncomment this method if necessary
//    public boolean modifyBroadcastSource(BluetoothDevice device, int receiver_id, boolean sync_pa,
//            List<BluetoothBroadcastAudioScanBaseConfig> configs) {
//        return bluetoothProxy.modifyBroadcastSource(device, receiver_id, sync_pa, configs);
//    }

    public boolean removeBroadcastSource(BluetoothDevice sink, int receiver_id) {
        // TODO: Find source ID from receiver_id. What is receiver_id?
        int sourceId = receiver_id;
        return bluetoothProxy.removeBroadcastSource(sink, sourceId);
    }

    public boolean setBroadcastCode(BluetoothDevice sink, int receiver_id, byte[] bcast_code) {
        // TODO: Find source ID from receiver_id. What is receiver_id?
        // TODO: Build BluetoothLeBroadcastMetadata with the new bcast_code.
        int sourceId = receiver_id;
        BluetoothLeBroadcastMetadata metadata = null;
        return bluetoothProxy.modifyBroadcastSource(sink, sourceId, metadata);
    }
}
