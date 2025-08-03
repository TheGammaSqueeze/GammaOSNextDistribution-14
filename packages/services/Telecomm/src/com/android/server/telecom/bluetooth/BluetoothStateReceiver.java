/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom.bluetooth;

import static com.android.server.telecom.CallAudioRouteAdapter.BT_ACTIVE_DEVICE_GONE;
import static com.android.server.telecom.CallAudioRouteAdapter.BT_ACTIVE_DEVICE_PRESENT;
import static com.android.server.telecom.CallAudioRouteAdapter.BT_AUDIO_CONNECTED;
import static com.android.server.telecom.CallAudioRouteAdapter.BT_AUDIO_DISCONNECTED;
import static com.android.server.telecom.CallAudioRouteAdapter.BT_DEVICE_ADDED;
import static com.android.server.telecom.CallAudioRouteAdapter.BT_DEVICE_REMOVED;
import static com.android.server.telecom.bluetooth.BluetoothRouteManager.BT_AUDIO_IS_ON;
import static com.android.server.telecom.bluetooth.BluetoothRouteManager.BT_AUDIO_LOST;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.telecom.Log;
import android.telecom.Logging.Session;

import com.android.internal.os.SomeArgs;
import com.android.server.telecom.AudioRoute;
import com.android.server.telecom.CallAudioCommunicationDeviceTracker;
import com.android.server.telecom.CallAudioRouteAdapter;
import com.android.server.telecom.flags.FeatureFlags;
import com.android.server.telecom.flags.Flags;


public class BluetoothStateReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = BluetoothStateReceiver.class.getSimpleName();
    public static final IntentFilter INTENT_FILTER;
    static {
        INTENT_FILTER = new IntentFilter();
        INTENT_FILTER.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        INTENT_FILTER.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        INTENT_FILTER.addAction(BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED);
        INTENT_FILTER.addAction(BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED);
        INTENT_FILTER.addAction(BluetoothHearingAid.ACTION_ACTIVE_DEVICE_CHANGED);
        INTENT_FILTER.addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED);
        INTENT_FILTER.addAction(BluetoothLeAudio.ACTION_LE_AUDIO_ACTIVE_DEVICE_CHANGED);
        INTENT_FILTER.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
    }

    // If not in a call, BSR won't listen to the Bluetooth stack's HFP on/off messages, since
    // other apps could be turning it on and off. We don't want to interfere.
    private boolean mIsInCall = false;
    private final BluetoothRouteManager mBluetoothRouteManager;
    private final BluetoothDeviceManager mBluetoothDeviceManager;
    private CallAudioCommunicationDeviceTracker mCommunicationDeviceTracker;
    private FeatureFlags mFeatureFlags;
    private CallAudioRouteAdapter mCallAudioRouteAdapter;

    public void onReceive(Context context, Intent intent) {
        Log.startSession("BSR.oR");
        try {
            String action = intent.getAction();
            switch (action) {
                case BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED:
                    handleAudioStateChanged(intent);
                    break;
                case BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED:
                case BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED:
                case BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED:
                    handleConnectionStateChanged(intent);
                    break;
                case BluetoothLeAudio.ACTION_LE_AUDIO_ACTIVE_DEVICE_CHANGED:
                case BluetoothHearingAid.ACTION_ACTIVE_DEVICE_CHANGED:
                case BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED:
                    handleActiveDeviceChanged(intent);
                    break;
            }
        } finally {
            Log.endSession();
        }
    }

    private void handleAudioStateChanged(Intent intent) {
        int bluetoothHeadsetAudioState =
                intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                        BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
        BluetoothDevice device =
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        if (device == null) {
            Log.w(LOG_TAG, "Got null device from broadcast. " +
                    "Ignoring.");
            return;
        }

        Log.i(LOG_TAG, "Device %s transitioned to audio state %d",
                device.getAddress(), bluetoothHeadsetAudioState);
        Session session = Log.createSubsession();
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = session;
        args.arg2 = device.getAddress();
        switch (bluetoothHeadsetAudioState) {
            case BluetoothHeadset.STATE_AUDIO_CONNECTED:
                if (Flags.useRefactoredAudioRouteSwitching()) {
                    mCallAudioRouteAdapter.sendMessageWithSessionInfo(BT_AUDIO_CONNECTED, 0,
                            device);
                } else {
                    if (!mIsInCall) {
                        Log.i(LOG_TAG, "Ignoring BT audio on since we're not in a call");
                        return;
                    }
                    mBluetoothRouteManager.sendMessage(BT_AUDIO_IS_ON, args);
                }
                break;
            case BluetoothHeadset.STATE_AUDIO_DISCONNECTED:
                if (Flags.useRefactoredAudioRouteSwitching()) {
                    mCallAudioRouteAdapter.sendMessageWithSessionInfo(BT_AUDIO_DISCONNECTED, 0,
                            device);
                }  else {
                    mBluetoothRouteManager.sendMessage(BT_AUDIO_LOST, args);
                }
                break;
        }
    }

    private void handleConnectionStateChanged(Intent intent) {
        int bluetoothHeadsetState = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                BluetoothHeadset.STATE_DISCONNECTED);
        BluetoothDevice device =
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);

        if (device == null) {
            Log.w(LOG_TAG, "Got null device from broadcast. " +
                    "Ignoring.");
            return;
        }

        int deviceType;
        @AudioRoute.AudioRouteType int audioRouteType;
        if (BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED.equals(intent.getAction())) {
            deviceType = BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO;
            audioRouteType = AudioRoute.TYPE_BLUETOOTH_LE;
        } else if (BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) {
            deviceType = BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID;
            audioRouteType = AudioRoute.TYPE_BLUETOOTH_HA;
        } else if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) {
            deviceType = BluetoothDeviceManager.DEVICE_TYPE_HEADSET;
            audioRouteType = AudioRoute.TYPE_BLUETOOTH_SCO;
        } else {
            Log.w(LOG_TAG, "handleConnectionStateChanged: %s invalid device type", device);
            return;
        }

        Log.i(LOG_TAG, "%s device %s changed state to %d",
                BluetoothDeviceManager.getDeviceTypeString(deviceType),
                device.getAddress(), bluetoothHeadsetState);

        if (bluetoothHeadsetState == BluetoothProfile.STATE_CONNECTED) {
            if (Flags.useRefactoredAudioRouteSwitching()) {
                mCallAudioRouteAdapter.sendMessageWithSessionInfo(BT_DEVICE_ADDED,
                        audioRouteType, device);
            } else {
                mBluetoothDeviceManager.onDeviceConnected(device, deviceType);
            }
        } else if (bluetoothHeadsetState == BluetoothProfile.STATE_DISCONNECTED
                || bluetoothHeadsetState == BluetoothProfile.STATE_DISCONNECTING) {
            if (Flags.useRefactoredAudioRouteSwitching()) {
                mCallAudioRouteAdapter.sendMessageWithSessionInfo(BT_DEVICE_REMOVED,
                        audioRouteType, device);
            } else {
                mBluetoothDeviceManager.onDeviceDisconnected(device, deviceType);
            }
        }
    }

    private void handleActiveDeviceChanged(Intent intent) {
        BluetoothDevice device =
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);

        int deviceType;
        @AudioRoute.AudioRouteType int audioRouteType;
        if (BluetoothLeAudio.ACTION_LE_AUDIO_ACTIVE_DEVICE_CHANGED.equals(intent.getAction())) {
            deviceType = BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO;
            audioRouteType = AudioRoute.TYPE_BLUETOOTH_LE;
        } else if (BluetoothHearingAid.ACTION_ACTIVE_DEVICE_CHANGED.equals(intent.getAction())) {
            deviceType = BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID;
            audioRouteType = AudioRoute.TYPE_BLUETOOTH_HA;
        } else if (BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED.equals(intent.getAction())) {
            deviceType = BluetoothDeviceManager.DEVICE_TYPE_HEADSET;
            audioRouteType = AudioRoute.TYPE_BLUETOOTH_SCO;
        } else {
            Log.w(LOG_TAG, "handleActiveDeviceChanged: %s invalid device type", device);
            return;
        }

        Log.i(LOG_TAG, "Device %s is now the preferred BT device for %s", device,
                BluetoothDeviceManager.getDeviceTypeString(deviceType));

        if (Flags.useRefactoredAudioRouteSwitching()) {
            if (device == null) {
                mCallAudioRouteAdapter.sendMessageWithSessionInfo(BT_ACTIVE_DEVICE_GONE,
                        audioRouteType);
            } else {
                mCallAudioRouteAdapter.sendMessageWithSessionInfo(BT_ACTIVE_DEVICE_PRESENT,
                        audioRouteType, device.getAddress());
            }
        } else {
            mBluetoothRouteManager.onActiveDeviceChanged(device, deviceType);
            if (deviceType == BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID ||
                    deviceType == BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO) {
                Session session = Log.createSubsession();
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = session;
                if (device == null) {
                    mBluetoothRouteManager.sendMessage(BT_AUDIO_LOST, args);
                } else {
                    if (!mIsInCall) {
                        Log.i(LOG_TAG, "Ignoring audio on since we're not in a call");
                        return;
                    }
                    args.arg2 = device.getAddress();

                    boolean usePreferredAudioProfile = false;
                    BluetoothAdapter bluetoothAdapter = mBluetoothDeviceManager
                            .getBluetoothAdapter();
                    int preferredDuplexProfile = BluetoothProfile.LE_AUDIO;
                    if (bluetoothAdapter != null) {
                        Bundle preferredAudioProfiles = bluetoothAdapter.getPreferredAudioProfiles(
                                device);
                        if (preferredAudioProfiles != null && !preferredAudioProfiles.isEmpty()
                                && preferredAudioProfiles.getInt(BluetoothAdapter.AUDIO_MODE_DUPLEX)
                                != 0) {
                            Log.i(this, "Preferred duplex profile for device=" + device + " is "
                                    + preferredAudioProfiles.getInt(
                                    BluetoothAdapter.AUDIO_MODE_DUPLEX));
                            usePreferredAudioProfile = true;
                            preferredDuplexProfile =
                                    preferredAudioProfiles.getInt(
                                            BluetoothAdapter.AUDIO_MODE_DUPLEX);
                        }
                    }

                    if (deviceType == BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO) {
                        /* In Le Audio case, once device got Active, the Telecom needs to make sure
                         * it is set as communication device before we can say that BT_AUDIO_IS_ON
                         */
                        boolean isLeAudioSetForCommunication =
                                mFeatureFlags.callAudioCommunicationDeviceRefactor()
                                        ? mCommunicationDeviceTracker.setCommunicationDevice(
                                        AudioDeviceInfo.TYPE_BLE_HEADSET, device)
                                        : mBluetoothDeviceManager.setLeAudioCommunicationDevice();
                        if ((!usePreferredAudioProfile
                                || preferredDuplexProfile == BluetoothProfile.LE_AUDIO)
                                && !isLeAudioSetForCommunication) {
                            Log.w(LOG_TAG,
                                    "Device %s cannot be use as LE audio communication device.",
                                    device);
                        }
                    } else {
                        boolean isHearingAidSetForCommunication =
                                mFeatureFlags.callAudioCommunicationDeviceRefactor()
                                        ? mCommunicationDeviceTracker.setCommunicationDevice(
                                        AudioDeviceInfo.TYPE_HEARING_AID, null)
                                        : mBluetoothDeviceManager
                                        .setHearingAidCommunicationDevice();
                        /* deviceType == BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID */
                        if (!isHearingAidSetForCommunication) {
                            Log.w(LOG_TAG,
                                    "Device %s cannot be use as hearing aid communication device.",
                                    device);
                        } else {
                            mBluetoothRouteManager.sendMessage(BT_AUDIO_IS_ON, args);
                        }
                    }
                }
            }
        }
    }

    public BluetoothDeviceManager getBluetoothDeviceManager() {
        return mBluetoothDeviceManager;
    }

    public BluetoothStateReceiver(BluetoothDeviceManager deviceManager,
            BluetoothRouteManager routeManager,
            CallAudioCommunicationDeviceTracker communicationDeviceTracker,
            FeatureFlags featureFlags) {
        mBluetoothDeviceManager = deviceManager;
        mBluetoothRouteManager = routeManager;
        mCommunicationDeviceTracker = communicationDeviceTracker;
        mFeatureFlags = featureFlags;
    }

    public void setIsInCall(boolean isInCall) {
        mIsInCall = isInCall;
    }

    public void setCallAudioRouteAdapter(CallAudioRouteAdapter adapter) {
        mCallAudioRouteAdapter = adapter;
    }
}
