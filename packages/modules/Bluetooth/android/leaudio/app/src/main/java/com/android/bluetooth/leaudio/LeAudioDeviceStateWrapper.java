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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHapPresetInfo;
import android.bluetooth.BluetoothLeBroadcastReceiveState;

import androidx.core.util.Pair;
import androidx.lifecycle.MutableLiveData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class LeAudioDeviceStateWrapper {
        public BluetoothDevice device;
        public LeAudioData leAudioData = null;
        public VolumeControlData volumeControlData = null;
        public BassData bassData = null;
        public HapData hapData = null;

        public LeAudioDeviceStateWrapper(BluetoothDevice device) {
                this.device = device;
        }

        public static class LeAudioData {
                public MutableLiveData<Boolean> isConnectedMutable = new MutableLiveData<>();
                public MutableLiveData<Pair<Integer, Integer>> nodeStatusMutable =
                                new MutableLiveData<>();
                public MutableLiveData<Pair<Integer, Pair<Integer, Integer>>> groupStatusMutable =
                                new MutableLiveData<>();
                public MutableLiveData<Pair<Integer, Boolean>> groupLockStateMutable =
                                new MutableLiveData<>();
                public MutableLiveData<Integer> microphoneStateMutable = new MutableLiveData<>();

                public Object viewsData = null;
        }

        public static class HapData {
                public MutableLiveData<Integer> hapStateMutable = new MutableLiveData<>();
                public MutableLiveData<String> hapStatusMutable = new MutableLiveData<>();
                public MutableLiveData<Integer> hapFeaturesMutable = new MutableLiveData<>();
                public MutableLiveData<Integer> hapActivePresetIndexMutable =
                                new MutableLiveData<>();
                public MutableLiveData<List<BluetoothHapPresetInfo>> hapPresetsMutable =
                                new MutableLiveData<>();

                public Object viewsData = null;
        }

        public static class VolumeControlData {
                public MutableLiveData<Boolean> isConnectedMutable = new MutableLiveData<>(false);
                public MutableLiveData<Integer> numInputsMutable = new MutableLiveData<>(0);
                public MutableLiveData<Integer> numOffsetsMutable = new MutableLiveData<>(0);
                public MutableLiveData<Integer> volumeStateMutable = new MutableLiveData<>(0);
                public MutableLiveData<Boolean> mutedStateMutable = new MutableLiveData<>(false);

                public MutableLiveData<Map<Integer, String>> inputDescriptionsMutable =
                                new MutableLiveData<>(new TreeMap<>());
                public MutableLiveData<Map<Integer, Integer>> inputStateGainMutable =
                                new MutableLiveData<>(new TreeMap<>());
                public MutableLiveData<Map<Integer, Integer>> inputStateGainModeMutable =
                                new MutableLiveData<>(new TreeMap<>());
                public MutableLiveData<Map<Integer, Integer>> inputStateGainUnitMutable =
                                new MutableLiveData<>(new TreeMap<>());
                public MutableLiveData<Map<Integer, Integer>> inputStateGainMinMutable =
                                new MutableLiveData<>(new TreeMap<>());
                public MutableLiveData<Map<Integer, Integer>> inputStateGainMaxMutable =
                                new MutableLiveData<>(new TreeMap<>());
                public MutableLiveData<Map<Integer, Boolean>> inputStateMuteMutable =
                                new MutableLiveData<>(new TreeMap<>());
                public MutableLiveData<Map<Integer, Integer>> inputStatusMutable =
                                new MutableLiveData<>(new TreeMap<>());
                public MutableLiveData<Map<Integer, Integer>> inputTypeMutable =
                                new MutableLiveData<>(new TreeMap<>());

                public MutableLiveData<Map<Integer, Integer>> outputVolumeOffsetMutable =
                                new MutableLiveData<>(new TreeMap<>());
                public MutableLiveData<Map<Integer, Integer>> outputLocationMutable =
                                new MutableLiveData<>(new TreeMap<>());
                public MutableLiveData<Map<Integer, String>> outputDescriptionMutable =
                                new MutableLiveData<>(new TreeMap<>());

                public Object viewsData = null;
        }

        public static class BassData {
                public MutableLiveData<Boolean> isConnectedMutable = new MutableLiveData<>();
                public MutableLiveData<HashMap<Integer, BluetoothLeBroadcastReceiveState>>
                        receiverStatesMutable = new MutableLiveData<>();

                public Object viewsData = null;
        }
}
