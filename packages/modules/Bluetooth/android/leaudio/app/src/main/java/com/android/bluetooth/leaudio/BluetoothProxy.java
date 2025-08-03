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
import android.bluetooth.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class BluetoothProxy {
    private static BluetoothProxy INSTANCE;
    private final Application application;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAudio bluetoothLeAudio = null;
    private BluetoothLeBroadcast mBluetoothLeBroadcast = null;
    private BluetoothLeBroadcastAssistant mBluetoothLeBroadcastAssistant = null;
    private Set<BluetoothDevice> mBroadcastScanDelegatorDevices = new HashSet<>();
    private BluetoothCsipSetCoordinator bluetoothCsis = null;
    private BluetoothVolumeControl bluetoothVolumeControl = null;
    private BluetoothHapClient bluetoothHapClient = null;
    private BluetoothProfile.ServiceListener profileListener = null;
    private BluetoothHapClient.Callback hapCallback = null;
    private OnBassEventListener mBassEventListener;
    private OnLocalBroadcastEventListener mLocalBroadcastEventListener;
    private final IntentFilter adapterIntentFilter;
    private final IntentFilter bassIntentFilter;
    private IntentFilter intentFilter;
    private final ExecutorService mExecutor;

    private final Map<Integer, UUID> mGroupLocks = new HashMap<>();

    private int GROUP_NODE_ADDED = 1;
    private int GROUP_NODE_REMOVED = 2;

    private boolean mLeAudioCallbackRegistered = false;
    private BluetoothLeAudio.Callback mLeAudioCallbacks =
    new BluetoothLeAudio.Callback() {
        @Override
        public void onCodecConfigChanged(int groupId, BluetoothLeAudioCodecStatus status) {}
        @Override
        public void onGroupStatusChanged(int groupId, int groupStatus) {
            List<LeAudioDeviceStateWrapper> valid_devices = null;
            valid_devices = allLeAudioDevicesMutable.getValue().stream().filter(
                                state -> state.leAudioData.nodeStatusMutable.getValue() != null
                                        && state.leAudioData.nodeStatusMutable.getValue().first
                                                .equals(groupId))
                                .collect(Collectors.toList());
            for (LeAudioDeviceStateWrapper dev : valid_devices) {
                dev.leAudioData.groupStatusMutable.postValue(
                        new Pair<>(groupId, new Pair<>(groupStatus, 0)));
            }
        }
        @Override
        public void onGroupNodeAdded(BluetoothDevice device, int groupId) {
            Log.d("LeCB:", device + " group added " + groupId);
            if (device == null || groupId == BluetoothLeAudio.GROUP_ID_INVALID) {
                Log.d("LeCB:", "invalid parameter");
                return;
            }
            Optional<LeAudioDeviceStateWrapper> valid_device_opt = allLeAudioDevicesMutable
                            .getValue().stream()
                            .filter(state -> state.device.getAddress().equals(device.getAddress()))
                            .findAny();

            if (!valid_device_opt.isPresent()) {
                Log.d("LeCB:", "Device not present");
                return;
            }

            LeAudioDeviceStateWrapper valid_device = valid_device_opt.get();
            LeAudioDeviceStateWrapper.LeAudioData svc_data = valid_device.leAudioData;

            svc_data.nodeStatusMutable.postValue(new Pair<>(groupId, GROUP_NODE_ADDED));
            svc_data.groupStatusMutable.postValue(new Pair<>(groupId, new Pair<>(-1, -1)));
        }
        @Override
        public void onGroupNodeRemoved(BluetoothDevice device, int groupId) {
            if (device == null || groupId == BluetoothLeAudio.GROUP_ID_INVALID) {
                Log.d("LeCB:", "invalid parameter");
                return;
            }

            Log.d("LeCB:", device + " group added " + groupId);
            if (device == null || groupId == BluetoothLeAudio.GROUP_ID_INVALID) {
                Log.d("LeCB:", "invalid parameter");
                return;
            }

            Optional<LeAudioDeviceStateWrapper> valid_device_opt = allLeAudioDevicesMutable
            .getValue().stream()
            .filter(state -> state.device.getAddress().equals(device.getAddress()))
            .findAny();

            if (!valid_device_opt.isPresent()) {
                Log.d("LeCB:", "Device not present");
                return;
            }

            LeAudioDeviceStateWrapper valid_device = valid_device_opt.get();
            LeAudioDeviceStateWrapper.LeAudioData svc_data = valid_device.leAudioData;

            svc_data.nodeStatusMutable.postValue(new Pair<>(groupId, GROUP_NODE_REMOVED));
            svc_data.groupStatusMutable.postValue(new Pair<>(groupId, new Pair<>(-1, -1)));
        }
    };

    private final MutableLiveData<Boolean> enabledBluetoothMutable;
    private final BroadcastReceiver adapterIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int toState =
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (toState == BluetoothAdapter.STATE_ON) {
                    enabledBluetoothMutable.postValue(true);
                } else if (toState == BluetoothAdapter.STATE_OFF) {
                    enabledBluetoothMutable.postValue(false);
                }
            }
        }
    };
    private final MutableLiveData<List<LeAudioDeviceStateWrapper>> allLeAudioDevicesMutable;
    private final BroadcastReceiver leAudioIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (allLeAudioDevicesMutable.getValue() != null) {
                if (device != null) {
                    Optional<LeAudioDeviceStateWrapper> valid_device_opt = allLeAudioDevicesMutable
                            .getValue().stream()
                            .filter(state -> state.device.getAddress().equals(device.getAddress()))
                            .findAny();

                    if (valid_device_opt.isPresent()) {
                        LeAudioDeviceStateWrapper valid_device = valid_device_opt.get();
                        LeAudioDeviceStateWrapper.LeAudioData svc_data = valid_device.leAudioData;
                        int group_id;

                        // Handle Le Audio actions
                        switch (action) {
                            case BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED: {
                                final int toState =
                                        intent.getIntExtra(BluetoothLeAudio.EXTRA_STATE, -1);
                                if (toState == BluetoothLeAudio.STATE_CONNECTED
                                        || toState == BluetoothLeAudio.STATE_DISCONNECTED)
                                    svc_data.isConnectedMutable
                                            .postValue(toState == BluetoothLeAudio.STATE_CONNECTED);

                                group_id = bluetoothLeAudio.getGroupId(device);
                                svc_data.nodeStatusMutable.postValue(
                                        new Pair<>(group_id, GROUP_NODE_ADDED));
                                svc_data.groupStatusMutable
                                        .postValue(new Pair<>(group_id, new Pair<>(-1, -1)));
                                break;
                            }
                        }
                    }
                }
            }
        }
    };

    private final BroadcastReceiver hapClientIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (allLeAudioDevicesMutable.getValue() != null) {
                if (device != null) {
                    Optional<LeAudioDeviceStateWrapper> valid_device_opt = allLeAudioDevicesMutable
                            .getValue().stream()
                            .filter(state -> state.device.getAddress().equals(device.getAddress()))
                            .findAny();

                    if (valid_device_opt.isPresent()) {
                        LeAudioDeviceStateWrapper valid_device = valid_device_opt.get();
                        LeAudioDeviceStateWrapper.HapData svc_data = valid_device.hapData;

                        switch (action) {
                            case BluetoothHapClient.ACTION_HAP_CONNECTION_STATE_CHANGED: {
                                final int toState =
                                        intent.getIntExtra(BluetoothHapClient.EXTRA_STATE, -1);
                                svc_data.hapStateMutable.postValue(toState);
                                break;
                            }
                            // Hidden API
                            case "android.bluetooth.action.HAP_DEVICE_AVAILABLE": {
                                final int features = intent
                                        .getIntExtra("android.bluetooth.extra.HAP_FEATURES", -1);
                                svc_data.hapFeaturesMutable.postValue(features);
                                break;
                            }
                            default:
                                // Do nothing
                                break;
                        }
                    }
                }
            }
        }
    };

    private final BroadcastReceiver volumeControlIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (allLeAudioDevicesMutable.getValue() != null) {
                if (device != null) {
                    Optional<LeAudioDeviceStateWrapper> valid_device_opt = allLeAudioDevicesMutable
                            .getValue().stream()
                            .filter(state -> state.device.getAddress().equals(device.getAddress()))
                            .findAny();

                    if (valid_device_opt.isPresent()) {
                        LeAudioDeviceStateWrapper valid_device = valid_device_opt.get();
                        LeAudioDeviceStateWrapper.VolumeControlData svc_data =
                                valid_device.volumeControlData;

                        switch (action) {
                            case BluetoothVolumeControl.ACTION_CONNECTION_STATE_CHANGED:
                                final int toState =
                                        intent.getIntExtra(BluetoothVolumeControl.EXTRA_STATE, -1);
                                if (toState == BluetoothVolumeControl.STATE_CONNECTED
                                        || toState == BluetoothVolumeControl.STATE_DISCONNECTED)
                                    svc_data.isConnectedMutable.postValue(
                                            toState == BluetoothVolumeControl.STATE_CONNECTED);
                                break;
                        }
                    }
                }
            }
        }
    };
    private final MutableLiveData<BluetoothLeBroadcastMetadata> mBroadcastUpdateMutableLive;
    private final MutableLiveData<Pair<Integer /* reason */, Integer /* broadcastId */>> mBroadcastPlaybackStartedMutableLive;
    private final MutableLiveData<Pair<Integer /* reason */, Integer /* broadcastId */>> mBroadcastPlaybackStoppedMutableLive;
    private final MutableLiveData<Integer /* broadcastId */> mBroadcastAddedMutableLive;
    private final MutableLiveData<Pair<Integer /* reason */, Integer /* broadcastId */>> mBroadcastRemovedMutableLive;
    private final MutableLiveData<String> mBroadcastStatusMutableLive;
    private final BluetoothLeBroadcast.Callback mBroadcasterCallback =
            new BluetoothLeBroadcast.Callback() {
                @Override
                public void onBroadcastStarted(int reason, int broadcastId) {
                    if ((reason != BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST)
                            && (reason != BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST)) {
                        mBroadcastStatusMutableLive.postValue("Unable to create broadcast: "
                                + broadcastId + ", reason: " + reason);
                    }

                    mBroadcastAddedMutableLive.postValue(broadcastId);
                    if (mLocalBroadcastEventListener != null) {
                        mLocalBroadcastEventListener.onBroadcastStarted(broadcastId);
                    }
                }

                @Override
                public void onBroadcastStartFailed(int reason) {
                    mBroadcastStatusMutableLive
                            .postValue("Unable to START broadcast due to reason: " + reason);
                }

                @Override
                public void onBroadcastStopped(int reason, int broadcastId) {
                    mBroadcastRemovedMutableLive.postValue(new Pair<>(reason, broadcastId));
                    if (mLocalBroadcastEventListener != null) {
                        mLocalBroadcastEventListener.onBroadcastStopped(broadcastId);
                    }
                }

                @Override
                public void onBroadcastStopFailed(int reason) {
                    mBroadcastStatusMutableLive
                            .postValue("Unable to STOP broadcast due to reason: " + reason);
                }

                @Override
                public void onPlaybackStarted(int reason, int broadcastId) {
                    mBroadcastPlaybackStartedMutableLive.postValue(new Pair<>(reason, broadcastId));
                }

                @Override
                public void onPlaybackStopped(int reason, int broadcastId) {
                    mBroadcastPlaybackStoppedMutableLive.postValue(new Pair<>(reason, broadcastId));
                }

                @Override
                public void onBroadcastUpdated(int reason, int broadcastId) {
                    mBroadcastStatusMutableLive.postValue("Broadcast " + broadcastId
                            + "has been updated due to reason: " + reason);
                    if (mLocalBroadcastEventListener != null) {
                        mLocalBroadcastEventListener.onBroadcastUpdated(broadcastId);
                    }
                }

                @Override
                public void onBroadcastUpdateFailed(int reason, int broadcastId) {
                    mBroadcastStatusMutableLive.postValue("Unable to UPDATE broadcast "
                            + broadcastId + " due to reason: " + reason);
                }

                @Override
                public void onBroadcastMetadataChanged(int broadcastId,
                        BluetoothLeBroadcastMetadata metadata) {
                    mBroadcastUpdateMutableLive.postValue(metadata);
                    if (mLocalBroadcastEventListener != null) {
                        mLocalBroadcastEventListener.onBroadcastMetadataChanged(
                                broadcastId, metadata);
                    }
                }
            };

    // TODO: Add behaviors in empty methods if necessary.
    private final BluetoothLeBroadcastAssistant.Callback mBroadcastAssistantCallback =
            new BluetoothLeBroadcastAssistant.Callback() {
        @Override
        public void onSearchStarted(int reason) {}

        @Override
        public void onSearchStartFailed(int reason) {}

        @Override
        public void onSearchStopped(int reason) {}

        @Override
        public void onSearchStopFailed(int reason) {}

        @Override
        public void onSourceFound(BluetoothLeBroadcastMetadata source) {
            Log.d("BluetoothProxy", "onSourceFound");
            if (mBassEventListener != null) {
                mBassEventListener.onSourceFound(source);
            }
        }

        @Override
        public void onSourceAdded(BluetoothDevice sink, int sourceId, int reason) {}

        @Override
        public void onSourceAddFailed(BluetoothDevice sink,
                BluetoothLeBroadcastMetadata source, int reason) {}

        @Override
        public void onSourceModified(BluetoothDevice sink, int sourceId, int reason) {}

        @Override
        public void onSourceModifyFailed(BluetoothDevice sink, int sourceId, int reason) {}

        @Override
        public void onSourceRemoved(BluetoothDevice sink, int sourceId, int reason) {}

        @Override
        public void onSourceRemoveFailed(BluetoothDevice sink, int sourceId, int reason) {}

        @Override
        public void onReceiveStateChanged(BluetoothDevice sink, int sourceId,
                BluetoothLeBroadcastReceiveState state) {
            Log.d("BluetoothProxy", "onReceiveStateChanged");
            if (allLeAudioDevicesMutable.getValue() != null) {
                Optional<LeAudioDeviceStateWrapper> valid_device_opt = allLeAudioDevicesMutable
                        .getValue().stream()
                        .filter(stateWrapper -> stateWrapper.device.getAddress().equals(
                                sink.getAddress()))
                        .findAny();

                if (!valid_device_opt.isPresent())
                    return;

                LeAudioDeviceStateWrapper valid_device = valid_device_opt.get();
                LeAudioDeviceStateWrapper.BassData svc_data = valid_device.bassData;

                /**
                 * From "Introducing-Bluetooth-LE-Audio-book" 8.6.3.1:
                 *
                 * The Source_ID is an Acceptor generated number which is used to identify a
                 * specific set of
                 * broadcast device and BIG information. It is local to an Acceptor and used as a
                 * reference for
                 * a Broadcast Assistant. In the case of a Coordinated Set of Acceptors, such as
                 * a left and right
                 * earbud, the Source_IDs are not related and may be different, even if both are
                 * receiving the
                 * same BIS, as each Acceptor independently creates their own Source ID values
                 */

                /**
                 * Broadcast receiver's endpoint identifier.
                 */
                synchronized(this) {
                    HashMap<Integer, BluetoothLeBroadcastReceiveState> states =
                            svc_data.receiverStatesMutable.getValue();
                    if (states == null)
                        states = new HashMap<>();
                    states.put(state.getSourceId(), state);

                    // Use SetValue instead of PostValue() since we want to make it
                    // synchronous due to getValue() we do here as well
                    // Otherwise we could miss the update and store only the last
                    // receiver ID
//                    svc_data.receiverStatesMutable.setValue(states);
                    svc_data.receiverStatesMutable.postValue(states);
                }
            }
        }
    };

    private final BroadcastReceiver bassIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothLeBroadcastAssistant.ACTION_CONNECTION_STATE_CHANGED)) {
                final BluetoothDevice device = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);

                if (allLeAudioDevicesMutable.getValue() != null) {
                    if (device != null) {
                        Optional<LeAudioDeviceStateWrapper> valid_device_opt =
                                allLeAudioDevicesMutable
                                        .getValue().stream()
                                        .filter(state -> state.device.getAddress().equals(
                                                device.getAddress()))
                                        .findAny();

                        if (valid_device_opt.isPresent()) {
                            LeAudioDeviceStateWrapper valid_device = valid_device_opt.get();
                            LeAudioDeviceStateWrapper.BassData svc_data = valid_device.bassData;

                            final int toState = intent
                                    .getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                            if (toState == BluetoothProfile.STATE_CONNECTED
                                    || toState == BluetoothProfile.STATE_DISCONNECTED)
                                svc_data.isConnectedMutable.postValue(
                                        toState == BluetoothProfile.STATE_CONNECTED);
                        }
                    }
                }
            }
            // TODO: Remove this if unnecessary.
//          case BluetoothBroadcastAudioScan.ACTION_BASS_BROADCAST_ANNONCEMENT_AVAILABLE:
//              // FIXME: Never happen since there is no valid device with this intent
//              break;
        }
    };

    private BluetoothProxy(Application application) {
        this.application = application;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        enabledBluetoothMutable = new MutableLiveData<>();
        allLeAudioDevicesMutable = new MutableLiveData<>();

        mBroadcastUpdateMutableLive = new MutableLiveData<>();
        mBroadcastStatusMutableLive = new MutableLiveData<>();

        mBroadcastPlaybackStartedMutableLive = new MutableLiveData<>();
        mBroadcastPlaybackStoppedMutableLive = new MutableLiveData<>();
        mBroadcastAddedMutableLive = new MutableLiveData();
        mBroadcastRemovedMutableLive = new MutableLiveData<>();

        MutableLiveData<String> mBroadcastStatusMutableLive;

        mExecutor = Executors.newSingleThreadExecutor();

        adapterIntentFilter = new IntentFilter();
        adapterIntentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        adapterIntentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        application.registerReceiver(adapterIntentReceiver, adapterIntentFilter,
                Context.RECEIVER_EXPORTED);

        bassIntentFilter = new IntentFilter();
        bassIntentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        bassIntentFilter.addAction(BluetoothLeBroadcastAssistant.ACTION_CONNECTION_STATE_CHANGED);
        application.registerReceiver(bassIntentReceiver, bassIntentFilter,
                Context.RECEIVER_EXPORTED);
    }

    // Lazy constructing Singleton acquire method
    public static BluetoothProxy getBluetoothProxy(Application application) {
        if (INSTANCE == null) {
            INSTANCE = new BluetoothProxy(application);
        }
        return (INSTANCE);
    }

    public void initProfiles() {
        if (profileListener != null) return;

        hapCallback = new BluetoothHapClient.Callback() {
            @Override
            public void onPresetSelected(BluetoothDevice device, int presetIndex, int statusCode) {
                Optional<LeAudioDeviceStateWrapper> valid_device_opt = allLeAudioDevicesMutable
                        .getValue().stream()
                        .filter(state -> state.device.getAddress().equals(device.getAddress()))
                        .findAny();

                if (!valid_device_opt.isPresent())
                    return;

                LeAudioDeviceStateWrapper valid_device = valid_device_opt.get();
                LeAudioDeviceStateWrapper.HapData svc_data = valid_device.hapData;

                svc_data.hapActivePresetIndexMutable.postValue(presetIndex);

                svc_data.hapStatusMutable
                        .postValue("Preset changed to " + presetIndex + ", reason: " + statusCode);
            }

            @Override
            public void onPresetSelectionFailed(BluetoothDevice device, int statusCode) {
                Optional<LeAudioDeviceStateWrapper> valid_device_opt = allLeAudioDevicesMutable
                        .getValue().stream()
                        .filter(state -> state.device.getAddress().equals(device.getAddress()))
                        .findAny();

                if (!valid_device_opt.isPresent())
                    return;

                LeAudioDeviceStateWrapper valid_device = valid_device_opt.get();
                LeAudioDeviceStateWrapper.HapData svc_data = valid_device.hapData;

                svc_data.hapStatusMutable
                        .postValue("Select preset failed with status " + statusCode);
            }

            @Override
            public void onPresetSelectionForGroupFailed(int hapGroupId, int statusCode) {
                List<LeAudioDeviceStateWrapper> valid_devices = null;
                if (hapGroupId != BluetoothCsipSetCoordinator.GROUP_ID_INVALID)
                    valid_devices = allLeAudioDevicesMutable.getValue().stream()
                            .filter(state -> state.leAudioData.nodeStatusMutable.getValue() != null
                                    && state.leAudioData.nodeStatusMutable.getValue().first
                                            .equals(hapGroupId))
                            .collect(Collectors.toList());

                if (valid_devices != null) {
                    for (LeAudioDeviceStateWrapper device : valid_devices) {
                        device.hapData.hapStatusMutable.postValue("Select preset for group "
                                + hapGroupId + " failed with status " + statusCode);
                    }
                }
            }

            @Override
            public void onPresetInfoChanged(BluetoothDevice device,
                    List<BluetoothHapPresetInfo> presetInfoList, int statusCode) {
                Optional<LeAudioDeviceStateWrapper> valid_device_opt = allLeAudioDevicesMutable
                        .getValue().stream()
                        .filter(state -> state.device.getAddress().equals(device.getAddress()))
                        .findAny();

                if (!valid_device_opt.isPresent())
                    return;

                LeAudioDeviceStateWrapper valid_device = valid_device_opt.get();
                LeAudioDeviceStateWrapper.HapData svc_data = valid_device.hapData;

                svc_data.hapStatusMutable
                        .postValue("Preset list changed due to status " + statusCode);
                svc_data.hapPresetsMutable.postValue(presetInfoList);
            }

            @Override
            public void onSetPresetNameFailed(BluetoothDevice device, int status) {
                Optional<LeAudioDeviceStateWrapper> valid_device_opt = allLeAudioDevicesMutable
                        .getValue().stream()
                        .filter(state -> state.device.getAddress().equals(device.getAddress()))
                        .findAny();

                if (!valid_device_opt.isPresent())
                    return;

                LeAudioDeviceStateWrapper valid_device = valid_device_opt.get();
                LeAudioDeviceStateWrapper.HapData svc_data = valid_device.hapData;

                svc_data.hapStatusMutable.postValue("Name set error: " + status);
            }

            @Override
            public void onSetPresetNameForGroupFailed(int hapGroupId, int status) {
                List<LeAudioDeviceStateWrapper> valid_devices = null;
                if (hapGroupId != BluetoothCsipSetCoordinator.GROUP_ID_INVALID)
                    valid_devices = allLeAudioDevicesMutable.getValue().stream()
                            .filter(state -> state.leAudioData.nodeStatusMutable.getValue() != null
                                    && state.leAudioData.nodeStatusMutable.getValue().first
                                            .equals(hapGroupId))
                            .collect(Collectors.toList());

                if (valid_devices != null) {
                    for (LeAudioDeviceStateWrapper device : valid_devices) {
                        device.hapData.hapStatusMutable
                                .postValue("Group Name set error: " + status);
                    }
                }
            }
        };

        profileListener = new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int i, BluetoothProfile bluetoothProfile) {
                Log.d("BluetoothProxy", "onServiceConnected(): i = " + i + " bluetoothProfile = " +
                        bluetoothProfile);
                switch (i) {
                    case BluetoothProfile.CSIP_SET_COORDINATOR:
                        bluetoothCsis = (BluetoothCsipSetCoordinator) bluetoothProfile;
                        break;
                    case BluetoothProfile.LE_AUDIO:
                        bluetoothLeAudio = (BluetoothLeAudio) bluetoothProfile;
                        if (!mLeAudioCallbackRegistered) {
                            try {
                            bluetoothLeAudio.registerCallback(mExecutor, mLeAudioCallbacks);
                            mLeAudioCallbackRegistered = true;
                            } catch (Exception e){
                                Log.e("Unicast:" ,
                                    " Probably not supported: Exception on registering callbacks: " + e);
                            }
                        }
                        break;
                    case BluetoothProfile.VOLUME_CONTROL:
                        bluetoothVolumeControl = (BluetoothVolumeControl) bluetoothProfile;
                        break;
                    case BluetoothProfile.HAP_CLIENT:
                        bluetoothHapClient = (BluetoothHapClient) bluetoothProfile;
                        try {
                            bluetoothHapClient.registerCallback(mExecutor, hapCallback);
                        } catch (IllegalArgumentException e) {
                            Log.e("HAP", "Application callback already registered.");
                        }
                        break;
                    case BluetoothProfile.LE_AUDIO_BROADCAST:
                        mBluetoothLeBroadcast = (BluetoothLeBroadcast) bluetoothProfile;
                        try {
                            mBluetoothLeBroadcast.registerCallback(mExecutor, mBroadcasterCallback);
                        } catch (IllegalArgumentException e) {
                            Log.e("Broadcast", "Application callback already registered.");
                        }
                        break;
                    case BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT:
                        Log.d("BluetoothProxy", "LE_AUDIO_BROADCAST_ASSISTANT Service connected");
                        mBluetoothLeBroadcastAssistant = (BluetoothLeBroadcastAssistant)
                                bluetoothProfile;
                        try {
                            mBluetoothLeBroadcastAssistant.registerCallback(mExecutor,
                                mBroadcastAssistantCallback);
                        } catch (IllegalArgumentException e) {
                            Log.e("BASS", "Application callback already registered.");
                        }
                        break;
                }
                queryLeAudioDevices();
            }

            @Override
            public void onServiceDisconnected(int i) {}
        };

        initCsisProxy();
        initLeAudioProxy();
        initVolumeControlProxy();
        initHapProxy();
        initLeAudioBroadcastProxy();
        initBassProxy();
    }

    public void cleanupProfiles() {
        if (profileListener == null) return;

        cleanupCsisProxy();
        cleanupLeAudioProxy();
        cleanupVolumeControlProxy();
        cleanupHapProxy();
        cleanupLeAudioBroadcastProxy();
        cleanupBassProxy();

        profileListener = null;
    }

    private void initCsisProxy() {
        if (!isCoordinatedSetProfileSupported()) return;
        if (bluetoothCsis == null) {
            bluetoothAdapter.getProfileProxy(this.application, profileListener,
                    BluetoothProfile.CSIP_SET_COORDINATOR);
        }
    }

    private void cleanupCsisProxy() {
        if (!isCoordinatedSetProfileSupported()) return;
        if (bluetoothCsis != null) {
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.LE_AUDIO, bluetoothCsis);
        }
    }

    private void initLeAudioProxy() {
        if (!isLeAudioUnicastSupported()) return;
        if (bluetoothLeAudio == null) {
            bluetoothAdapter.getProfileProxy(this.application, profileListener,
                    BluetoothProfile.LE_AUDIO);
        }

        intentFilter = new IntentFilter();
        intentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        intentFilter.addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED);
        application.registerReceiver(leAudioIntentReceiver, intentFilter,
                Context.RECEIVER_EXPORTED);
    }

    private void cleanupLeAudioProxy() {
        if (!isLeAudioUnicastSupported()) return;
        if (bluetoothLeAudio != null) {
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.LE_AUDIO, bluetoothLeAudio);
            application.unregisterReceiver(leAudioIntentReceiver);
        }
    }

    private void initVolumeControlProxy() {
        if (!isVolumeControlClientSupported()) return;
        bluetoothAdapter.getProfileProxy(this.application, profileListener,
                BluetoothProfile.VOLUME_CONTROL);

        intentFilter = new IntentFilter();
        intentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        intentFilter.addAction(BluetoothVolumeControl.ACTION_CONNECTION_STATE_CHANGED);
        application.registerReceiver(volumeControlIntentReceiver, intentFilter,
                Context.RECEIVER_EXPORTED);
    }

    private void cleanupVolumeControlProxy() {
        if (!isVolumeControlClientSupported()) return;
        if (bluetoothVolumeControl != null) {
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.VOLUME_CONTROL,
                    bluetoothVolumeControl);
            application.unregisterReceiver(volumeControlIntentReceiver);
        }
    }

    private void initHapProxy() {
        if (!isLeAudioHearingAccessClientSupported()) return;
        bluetoothAdapter.getProfileProxy(this.application, profileListener,
                BluetoothProfile.HAP_CLIENT);

        intentFilter = new IntentFilter();
        intentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        intentFilter.addAction(BluetoothHapClient.ACTION_HAP_CONNECTION_STATE_CHANGED);
        intentFilter.addAction("android.bluetooth.action.HAP_DEVICE_AVAILABLE");
        application.registerReceiver(hapClientIntentReceiver, intentFilter,
                Context.RECEIVER_EXPORTED);
    }

    private void cleanupHapProxy() {
        if (!isLeAudioHearingAccessClientSupported()) return;
        if (bluetoothHapClient != null) {
            bluetoothHapClient.unregisterCallback(hapCallback);
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HAP_CLIENT, bluetoothHapClient);
            application.unregisterReceiver(hapClientIntentReceiver);
        }
    }

    private void initBassProxy() {
        if (!isLeAudioBroadcastScanAssistanSupported()) return;
        bluetoothAdapter.getProfileProxy(this.application, profileListener,
                BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);
    }

    private void cleanupBassProxy() {
        if (!isLeAudioBroadcastScanAssistanSupported()) return;
        if (mBluetoothLeBroadcastAssistant != null) {
            mBluetoothLeBroadcastAssistant.unregisterCallback(mBroadcastAssistantCallback);
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT,
                    mBluetoothLeBroadcastAssistant);
        }
    }

    private Boolean checkForEnabledBluetooth() {
        Boolean current_state = bluetoothAdapter.isEnabled();

        // Force the update since event may not come if bt was already enabled
        if (!Objects.equals(enabledBluetoothMutable.getValue(), current_state))
            enabledBluetoothMutable.setValue(current_state);

        return current_state;
    }

    public void queryLeAudioDevices() {
        if (checkForEnabledBluetooth()) {
            // Consider those with the ASC service as valid devices
            List<LeAudioDeviceStateWrapper> validDevices = new ArrayList<>();
            for (BluetoothDevice dev : bluetoothAdapter.getBondedDevices()) {
                LeAudioDeviceStateWrapper state_wrapper = new LeAudioDeviceStateWrapper(dev);
                Boolean valid_device = false;

                if (Arrays.asList(dev.getUuids() != null ? dev.getUuids() : new ParcelUuid[0])
                        .contains(ParcelUuid
                                .fromString(application.getString(R.string.svc_uuid_le_audio)))) {
                    if (state_wrapper.leAudioData == null)
                        state_wrapper.leAudioData = new LeAudioDeviceStateWrapper.LeAudioData();
                    valid_device = true;

                    if (bluetoothLeAudio != null) {
                        state_wrapper.leAudioData.isConnectedMutable.postValue(bluetoothLeAudio
                                .getConnectionState(dev) == BluetoothLeAudio.STATE_CONNECTED);
                        int group_id = bluetoothLeAudio.getGroupId(dev);
                        state_wrapper.leAudioData.nodeStatusMutable
                                .setValue(new Pair<>(group_id, GROUP_NODE_ADDED));
                        state_wrapper.leAudioData.groupStatusMutable
                                .setValue(new Pair<>(group_id, new Pair<>(-1, -1)));
                    }
                }

                if (Arrays.asList(dev.getUuids() != null ? dev.getUuids() : new ParcelUuid[0])
                        .contains(ParcelUuid.fromString(
                                application.getString(R.string.svc_uuid_volume_control)))) {
                    if (state_wrapper.volumeControlData == null)
                        state_wrapper.volumeControlData =
                                new LeAudioDeviceStateWrapper.VolumeControlData();
                    valid_device = true;

                    if (bluetoothVolumeControl != null) {
                        state_wrapper.volumeControlData.isConnectedMutable
                                .postValue(bluetoothVolumeControl.getConnectionState(
                                        dev) == BluetoothVolumeControl.STATE_CONNECTED);
                        // FIXME: We don't have the api to get the volume and mute states? :(
                    }
                }

                if (Arrays.asList(dev.getUuids() != null ? dev.getUuids() : new ParcelUuid[0])
                        .contains(ParcelUuid
                                .fromString(application.getString(R.string.svc_uuid_has)))) {
                    if (state_wrapper.hapData == null)
                        state_wrapper.hapData = new LeAudioDeviceStateWrapper.HapData();
                    valid_device = true;

                    if (bluetoothHapClient != null) {
                        state_wrapper.hapData.hapStateMutable
                                .postValue(bluetoothHapClient.getConnectionState(dev));
                        boolean is_connected = bluetoothHapClient
                                .getConnectionState(dev) == BluetoothHapClient.STATE_CONNECTED;
                        if (is_connected) {
                            // Use hidden API
                            try {
                                Method getFeaturesMethod = BluetoothHapClient.class
                                        .getDeclaredMethod("getFeatures", BluetoothDevice.class);
                                getFeaturesMethod.setAccessible(true);
                                state_wrapper.hapData.hapFeaturesMutable
                                        .postValue((Integer) getFeaturesMethod
                                                .invoke(bluetoothHapClient, dev));
                            } catch (NoSuchMethodException | IllegalAccessException
                                    | InvocationTargetException e) {
                                state_wrapper.hapData.hapStatusMutable
                                        .postValue("Hidden API for getFeatures not accessible.");
                            }

                            state_wrapper.hapData.hapPresetsMutable
                                    .postValue(bluetoothHapClient.getAllPresetInfo(dev));
                            try {
                                Method getActivePresetIndexMethod =
                                        BluetoothHapClient.class.getDeclaredMethod(
                                                "getActivePresetIndex", BluetoothDevice.class);
                                getActivePresetIndexMethod.setAccessible(true);
                                state_wrapper.hapData.hapActivePresetIndexMutable
                                        .postValue((Integer) getActivePresetIndexMethod
                                                .invoke(bluetoothHapClient, dev));
                            } catch (NoSuchMethodException | IllegalAccessException
                                    | InvocationTargetException e) {
                                state_wrapper.hapData.hapStatusMutable
                                        .postValue("Hidden API for getFeatures not accessible.");
                            }
                        }
                    }
                }

                if (Arrays.asList(dev.getUuids() != null ? dev.getUuids() : new ParcelUuid[0])
                        .contains(ParcelUuid.fromString(
                                application.getString(R.string.svc_uuid_broadcast_audio)))) {
                    if (state_wrapper.bassData == null)
                        state_wrapper.bassData = new LeAudioDeviceStateWrapper.BassData();
                    valid_device = true;

                    if (mBluetoothLeBroadcastAssistant != null) {
                        boolean is_connected = mBluetoothLeBroadcastAssistant
                                .getConnectionState(dev) == BluetoothProfile.STATE_CONNECTED;
                        state_wrapper.bassData.isConnectedMutable.setValue(is_connected);
                    }
                }


                if (valid_device) validDevices.add(state_wrapper);
            }

            // Async update
            allLeAudioDevicesMutable.postValue(validDevices);
        }
    }

    public void connectLeAudio(BluetoothDevice device, boolean connect) {
        if (bluetoothLeAudio != null) {
            if (connect) {
                try {
                    Method connectMethod = BluetoothLeAudio.class.getDeclaredMethod("connect",
                            BluetoothDevice.class);
                    connectMethod.setAccessible(true);
                    connectMethod.invoke(bluetoothLeAudio, device);
                } catch (NoSuchMethodException | IllegalAccessException
                        | InvocationTargetException e) {
                    // Do nothing
                }
            } else {
                try {
                    Method disconnectMethod = BluetoothLeAudio.class.getDeclaredMethod("disconnect",
                            BluetoothDevice.class);
                    disconnectMethod.setAccessible(true);
                    disconnectMethod.invoke(bluetoothLeAudio, device);
                } catch (NoSuchMethodException | IllegalAccessException
                        | InvocationTargetException e) {
                    // Do nothing
                }
            }
        }
    }

    public void streamAction(Integer group_id, int action, Integer content_type) {
        if (bluetoothLeAudio != null) {
            switch (action) {
                case 0:
                    // No longer available, not needed
                    // bluetoothLeAudio.groupStream(group_id, content_type);
                    break;
                case 1:
                    // No longer available, not needed
                    // bluetoothLeAudio.groupSuspend(group_id);
                    break;
                case 2:
                    // No longer available, not needed
                    // bluetoothLeAudio.groupStop(group_id);
                    break;
                default:
                    break;
            }
        }
    }

    public void groupSet(BluetoothDevice device, Integer group_id) {
        if (bluetoothLeAudio == null) return;

        try {
            Method groupAddNodeMethod = BluetoothLeAudio.class.getDeclaredMethod("groupAddNode",
                    int.class, BluetoothDevice.class);
            groupAddNodeMethod.setAccessible(true);
            groupAddNodeMethod.invoke(bluetoothLeAudio, group_id, device);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Do nothing
        }
    }

    public void groupUnset(BluetoothDevice device, Integer group_id) {
        if (bluetoothLeAudio == null) return;

        try {
            Method groupRemoveNodeMethod = BluetoothLeAudio.class
                    .getDeclaredMethod("groupRemoveNode", int.class, BluetoothDevice.class);
            groupRemoveNodeMethod.setAccessible(true);
            groupRemoveNodeMethod.invoke(bluetoothLeAudio, group_id, device);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Do nothing
        }
    }

    public void groupSetLock(Integer group_id, boolean lock) {
        if (bluetoothCsis == null) return;

        Log.d("Lock", "lock: " + lock);
        if (lock) {
            if (mGroupLocks.containsKey(group_id)) {
                Log.e("Lock", "group" + group_id + " is already in locking process or locked: " + lock);
                return;
            }

            UUID uuid = bluetoothCsis.lockGroup(group_id, mExecutor,
                    (int group, int op_status, boolean is_locked) -> {
                        Log.d("LockCb", "lock: " + is_locked + " status: " + op_status);
                        if (((op_status == BluetoothStatusCodes.SUCCESS)
                                || (op_status == BluetoothStatusCodes.ERROR_CSIP_LOCKED_GROUP_MEMBER_LOST))
                                && (group != BluetoothLeAudio.GROUP_ID_INVALID)) {
                            allLeAudioDevicesMutable.getValue().forEach((dev_wrapper) -> {
                                if (dev_wrapper.leAudioData.nodeStatusMutable.getValue() != null
                                        && dev_wrapper.leAudioData.nodeStatusMutable
                                                .getValue().first.equals(group_id)) {
                                    dev_wrapper.leAudioData.groupLockStateMutable.postValue(
                                            new Pair<Integer, Boolean>(group, is_locked));
                                }
                            });
                        } else {
                            // TODO: Set error status so it could be notified/toasted to the
                            // user
                        }

                        if (!is_locked)
                            mGroupLocks.remove(group_id);
                    });
            // Store the lock key
            mGroupLocks.put(group_id, uuid);
        } else {
            if (!mGroupLocks.containsKey(group_id)) return;

            // Use the stored lock key
            bluetoothCsis.unlockGroup(mGroupLocks.get(group_id));
            mGroupLocks.remove(group_id);
        }
    }

    public void connectBass(BluetoothDevice device, boolean connect) {
        if (mBluetoothLeBroadcastAssistant != null) {
            if (connect) {
                mBluetoothLeBroadcastAssistant.setConnectionPolicy(device,
                        BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            } else {
                mBluetoothLeBroadcastAssistant.setConnectionPolicy(device,
                        BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
            }
        }
    }

    public boolean scanForBroadcasts(@Nullable BluetoothDevice scanDelegator, boolean scan) {
        if (mBluetoothLeBroadcastAssistant != null) {
            // Note: startSearchingForSources() does not support scanning on behalf of
            // a specific device - it only searches for all BASS connected devices.
            // Therefore, we manage the list of the devices and start/stop the scanning.
            if (scan) {
                if (scanDelegator != null) {
                    mBroadcastScanDelegatorDevices.add(scanDelegator);
                }
                try {
                    mBluetoothLeBroadcastAssistant.startSearchingForSources(new ArrayList<>());
                } catch (IllegalArgumentException e) {
                    Log.e("BluetoothProxy", " Unexpected " + e);
                }
                if (mBassEventListener != null) {
                    mBassEventListener.onScanningStateChanged(true);
                }
            } else {
                if (scanDelegator != null) {
                    mBroadcastScanDelegatorDevices.remove(scanDelegator);
                }
                if (mBroadcastScanDelegatorDevices.isEmpty()) {
                    try {
                        mBluetoothLeBroadcastAssistant.stopSearchingForSources();
                        if (mBassEventListener != null) {
                            mBassEventListener.onScanningStateChanged(false);
                        }
                    } catch (IllegalArgumentException e) {
                        Log.e("BluetoothProxy", " Unexpected " + e);
                    }
                }
            }
            return true;
        }
        return false;
    }

    public boolean stopBroadcastObserving() {
        if (mBluetoothLeBroadcastAssistant != null) {
            mBroadcastScanDelegatorDevices.clear();
            try {
                mBluetoothLeBroadcastAssistant.stopSearchingForSources();
            } catch (IllegalArgumentException e) {
                Log.e("BluetoothProxy", " Unexpected " + e);
            }

            if (mBassEventListener != null) {
                mBassEventListener.onScanningStateChanged(false);
            }
            return true;
        }
        return false;
    }

    // TODO: Uncomment this method if necessary
//    public boolean getBroadcastReceiverState(BluetoothDevice device, int receiver_id) {
//        if (mBluetoothLeBroadcastAssistant != null) {
//            return mBluetoothLeBroadcastAssistant.getBroadcastReceiverState(device, receiver_id);
//        }
//        return false;
//    }

    public boolean addBroadcastSource(BluetoothDevice sink, BluetoothLeBroadcastMetadata sourceMetadata) {
        if (mBluetoothLeBroadcastAssistant != null) {
            mBluetoothLeBroadcastAssistant.addSource(sink, sourceMetadata, true /* isGroupOp */);
            return true;
        }
        return false;
    }

    public boolean modifyBroadcastSource(BluetoothDevice sink, int sourceId,
            BluetoothLeBroadcastMetadata metadata) {
        if (mBluetoothLeBroadcastAssistant != null) {
            mBluetoothLeBroadcastAssistant.modifySource(sink, sourceId, metadata);
            return true;
        }
        return false;
    }

    public boolean removeBroadcastSource(BluetoothDevice sink, int sourceId) {
        if (mBluetoothLeBroadcastAssistant != null) {
            mBluetoothLeBroadcastAssistant.removeSource(sink, sourceId);
            return true;
        }
        return false;
    }

    public void setVolume(BluetoothDevice device, int volume) {
        if (bluetoothLeAudio != null && !bluetoothLeAudio.getConnectedDevices().isEmpty()) {
            bluetoothLeAudio.setVolume(volume);
        } else if (bluetoothVolumeControl != null) {
            bluetoothVolumeControl.setVolumeOffset(device, volume);
        }
    }

    public LiveData<Boolean> getBluetoothEnabled() {
        return enabledBluetoothMutable;
    }

    public LiveData<List<LeAudioDeviceStateWrapper>> getAllLeAudioDevices() {
        return allLeAudioDevicesMutable;
    }

    public void connectHap(BluetoothDevice device, boolean connect) {
        if (bluetoothHapClient != null) {
            if (connect) {
                bluetoothHapClient.setConnectionPolicy(device,
                        BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            } else {
                bluetoothHapClient.setConnectionPolicy(device,
                        BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
            }
        }
    }

    public boolean hapReadPresetInfo(BluetoothDevice device, int preset_index) {
        if (bluetoothHapClient == null)
            return false;

        BluetoothHapPresetInfo new_preset = null;

        // Use hidden API
        try {
            Method getPresetInfoMethod = BluetoothHapClient.class.getDeclaredMethod("getPresetInfo",
                    BluetoothDevice.class, int.class);
            getPresetInfoMethod.setAccessible(true);

            new_preset = (BluetoothHapPresetInfo) getPresetInfoMethod.invoke(bluetoothHapClient,
                    device, preset_index);
            if (new_preset == null)
                return false;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Do nothing'
            return false;
        }

        Optional<LeAudioDeviceStateWrapper> valid_device_opt = allLeAudioDevicesMutable.getValue()
                .stream().filter(state -> state.device.getAddress().equals(device.getAddress()))
                .findAny();

        if (!valid_device_opt.isPresent())
            return false;

        LeAudioDeviceStateWrapper valid_device = valid_device_opt.get();
        LeAudioDeviceStateWrapper.HapData svc_data = valid_device.hapData;

        List current_presets = svc_data.hapPresetsMutable.getValue();
        if (current_presets == null)
            current_presets = new ArrayList<BluetoothHapPresetInfo>();

        // Remove old one and add back the new one
        ListIterator<BluetoothHapPresetInfo> iter = current_presets.listIterator();
        while (iter.hasNext()) {
            if (iter.next().getIndex() == new_preset.getIndex()) {
                iter.remove();
            }
        }
        current_presets.add(new_preset);

        svc_data.hapPresetsMutable.postValue(current_presets);
        return true;
    }

    public boolean hapSetActivePreset(BluetoothDevice device, int preset_index) {
        if (bluetoothHapClient == null)
            return false;

        bluetoothHapClient.selectPreset(device, preset_index);
        return true;
    }

    public boolean hapSetActivePresetForGroup(BluetoothDevice device, int preset_index) {
        if (bluetoothHapClient == null)
            return false;

        int groupId = bluetoothLeAudio.getGroupId(device);
        bluetoothHapClient.selectPresetForGroup(groupId, preset_index);
        return true;
    }

    public boolean hapChangePresetName(BluetoothDevice device, int preset_index, String name) {
        if (bluetoothHapClient == null)
            return false;

        bluetoothHapClient.setPresetName(device, preset_index, name);
        return true;
    }

    public boolean hapPreviousDevicePreset(BluetoothDevice device) {
        if (bluetoothHapClient == null)
            return false;

        // Use hidden API
        try {
            Method switchToPreviousPresetMethod = BluetoothHapClient.class
                    .getDeclaredMethod("switchToPreviousPreset", BluetoothDevice.class);
            switchToPreviousPresetMethod.setAccessible(true);

            switchToPreviousPresetMethod.invoke(bluetoothHapClient, device);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return false;
        }
        return true;
    }

    public boolean hapNextDevicePreset(BluetoothDevice device) {
        if (bluetoothHapClient == null)
            return false;

        // Use hidden API
        try {
            Method switchToNextPresetMethod = BluetoothHapClient.class
                    .getDeclaredMethod("switchToNextPreset", BluetoothDevice.class);
            switchToNextPresetMethod.setAccessible(true);

            switchToNextPresetMethod.invoke(bluetoothHapClient, device);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return false;
        }
        return true;
    }

    public boolean hapPreviousGroupPreset(int group_id) {
        if (bluetoothHapClient == null)
            return false;

        // Use hidden API
        try {
            Method switchToPreviousPresetForGroupMethod = BluetoothHapClient.class
                    .getDeclaredMethod("switchToPreviousPresetForGroup", int.class);
            switchToPreviousPresetForGroupMethod.setAccessible(true);

            switchToPreviousPresetForGroupMethod.invoke(bluetoothHapClient, group_id);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return false;
        }
        return true;
    }

    public boolean hapNextGroupPreset(int group_id) {
        if (bluetoothHapClient == null)
            return false;

        // Use hidden API
        try {
            Method switchToNextPresetForGroupMethod = BluetoothHapClient.class
                    .getDeclaredMethod("switchToNextPresetForGroup", int.class);
            switchToNextPresetForGroupMethod.setAccessible(true);

            switchToNextPresetForGroupMethod.invoke(bluetoothHapClient, group_id);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return false;
        }
        return true;
    }

    public int hapGetHapGroup(BluetoothDevice device) {
        if (bluetoothHapClient == null)
            return BluetoothCsipSetCoordinator.GROUP_ID_INVALID;

        // Use hidden API
        try {
            Method getHapGroupMethod = BluetoothHapClient.class.getDeclaredMethod("getHapGroup",
                    BluetoothDevice.class);
            getHapGroupMethod.setAccessible(true);

            return (Integer) getHapGroupMethod.invoke(bluetoothHapClient, device);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Do nothing
        }
        return BluetoothCsipSetCoordinator.GROUP_ID_INVALID;
    }

    private void initLeAudioBroadcastProxy() {
        if (!isLeAudioBroadcastSourceSupported()) return;
        if (mBluetoothLeBroadcast == null) {
            bluetoothAdapter.getProfileProxy(this.application, profileListener,
                    BluetoothProfile.LE_AUDIO_BROADCAST);
        }
    }

    private void cleanupLeAudioBroadcastProxy() {
        if (!isLeAudioBroadcastSourceSupported()) return;
        if (mBluetoothLeBroadcast != null) {
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.LE_AUDIO_BROADCAST,
                    mBluetoothLeBroadcast);
        }
    }

    public LiveData<BluetoothLeBroadcastMetadata> getBroadcastUpdateMetadataLive() {
        return mBroadcastUpdateMutableLive;
    }

    public LiveData<Pair<Integer /* reason */, Integer /* broadcastId */>> getBroadcastPlaybackStartedMutableLive() {
        return mBroadcastPlaybackStartedMutableLive;
    }

    public LiveData<Pair<Integer /* reason */, Integer /* broadcastId */>> getBroadcastPlaybackStoppedMutableLive() {
        return mBroadcastPlaybackStoppedMutableLive;
    }

    public LiveData<Integer /* broadcastId */> getBroadcastAddedMutableLive() {
        return mBroadcastAddedMutableLive;
    }

    public LiveData<Pair<Integer /* reason */, Integer /* broadcastId */>> getBroadcastRemovedMutableLive() {
        return mBroadcastRemovedMutableLive;
    }

    public LiveData<String> getBroadcastStatusMutableLive() {
        return mBroadcastStatusMutableLive;
    }

    public boolean startBroadcast(BluetoothLeBroadcastSettings settings) {
        if (mBluetoothLeBroadcast == null)
            return false;
        mBluetoothLeBroadcast.startBroadcast(settings);
        return true;
    }

    public boolean stopBroadcast(int broadcastId) {
        if (mBluetoothLeBroadcast == null) return false;
        mBluetoothLeBroadcast.stopBroadcast(broadcastId);
        return true;
    }

    public List<BluetoothLeBroadcastMetadata> getAllLocalBroadcasts() {
        if (mBluetoothLeBroadcast == null) return Collections.emptyList();
        return mBluetoothLeBroadcast.getAllBroadcastMetadata();
    }

    public boolean updateBroadcast(int broadcastId, BluetoothLeBroadcastSettings settings) {
        if (mBluetoothLeBroadcast == null) return false;

        mBluetoothLeBroadcast.updateBroadcast(broadcastId, settings);
        return true;
    }

    public int getMaximumNumberOfBroadcast() {
        if (mBluetoothLeBroadcast == null) {
            Log.d("BluetoothProxy", "mBluetoothLeBroadcast is null");
            return 0;
        }
        return mBluetoothLeBroadcast.getMaximumNumberOfBroadcasts();
    }

    public boolean isPlaying(int broadcastId) {
        if (mBluetoothLeBroadcast == null) return false;
        return mBluetoothLeBroadcast.isPlaying(broadcastId);
    }

    boolean isLeAudioUnicastSupported() {
        return (bluetoothAdapter
                .isLeAudioSupported() == BluetoothStatusCodes.FEATURE_SUPPORTED);
    }

    boolean isCoordinatedSetProfileSupported() {
        return isLeAudioUnicastSupported();
    }

    boolean isVolumeControlClientSupported() {
        return isLeAudioUnicastSupported();
    }

    boolean isLeAudioHearingAccessClientSupported() {
        return isLeAudioUnicastSupported();
    }

    public boolean isLeAudioBroadcastSourceSupported() {
        return (bluetoothAdapter
                .isLeAudioBroadcastSourceSupported() == BluetoothStatusCodes.FEATURE_SUPPORTED);
    }

    public boolean isLeAudioBroadcastScanAssistanSupported() {
        return (bluetoothAdapter
                .isLeAudioBroadcastAssistantSupported() == BluetoothStatusCodes.FEATURE_SUPPORTED);
    }

    public void setOnBassEventListener(OnBassEventListener listener) {
        mBassEventListener = listener;
    }

    // Used by BroadcastScanViewModel
    public interface OnBassEventListener {
        void onSourceFound(BluetoothLeBroadcastMetadata source);
        void onScanningStateChanged(boolean isScanning);
    }

    public void setOnLocalBroadcastEventListener(OnLocalBroadcastEventListener listener) {
        mLocalBroadcastEventListener = listener;
    }

    // Used by BroadcastScanViewModel
    public interface OnLocalBroadcastEventListener {
        // TODO: Add arguments in methods
        void onBroadcastStarted(int broadcastId);
        void onBroadcastStopped(int broadcastId);
        void onBroadcastUpdated(int broadcastId);
        void onBroadcastMetadataChanged(int broadcastId, BluetoothLeBroadcastMetadata metadata);
    }
}
