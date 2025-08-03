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
 * limitations under the License
 */

package com.android.server.telecom;

import static com.android.server.telecom.CallAudioRouteAdapter.BT_AUDIO_CONNECTED;
import static com.android.server.telecom.CallAudioRouteAdapter.BT_AUDIO_DISCONNECTED;
import static com.android.server.telecom.CallAudioRouteAdapter.PENDING_ROUTE_FAILED;
import static com.android.server.telecom.CallAudioRouteAdapter.SPEAKER_OFF;
import static com.android.server.telecom.CallAudioRouteAdapter.SPEAKER_ON;

import android.annotation.IntDef;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.telecom.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AudioRoute {
    public static class Factory {
        private final ScheduledExecutorService mScheduledExecutorService =
                new ScheduledThreadPoolExecutor(1);
        private final CompletableFuture<AudioRoute> mAudioRouteFuture = new CompletableFuture<>();
        public AudioRoute create(@AudioRouteType int type, String bluetoothAddress,
                                 AudioManager audioManager) throws RuntimeException {
            createRetry(type, bluetoothAddress, audioManager, MAX_CONNECTION_RETRIES);
            try {
                return mAudioRouteFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error when creating requested audio route");
            }
        }
        private void createRetry(@AudioRouteType int type, String bluetoothAddress,
                                       AudioManager audioManager, int retryCount) {
            if (retryCount == 0) {
                mAudioRouteFuture.complete(null);
            }

            Log.i(this, "creating AudioRoute with type %s and address %s, retry count %d",
                    DEVICE_TYPE_STRINGS.get(type), bluetoothAddress, retryCount);
            AudioDeviceInfo routeInfo = null;
            List<AudioDeviceInfo> infos = audioManager.getAvailableCommunicationDevices();
            List<Integer> possibleInfoTypes = AUDIO_ROUTE_TYPE_TO_DEVICE_INFO_TYPE.get(type);
            for (AudioDeviceInfo info : infos) {
                Log.i(this, "type: " + info.getType());
                if (possibleInfoTypes != null && possibleInfoTypes.contains(info.getType())) {
                    if (BT_AUDIO_ROUTE_TYPES.contains(type)) {
                        if (bluetoothAddress.equals(info.getAddress())) {
                            routeInfo = info;
                            break;
                        }
                    } else {
                        routeInfo = info;
                        break;
                    }
                }
            }
            if (routeInfo == null) {
                mScheduledExecutorService.schedule(
                        () -> createRetry(type, bluetoothAddress, audioManager, retryCount - 1),
                        RETRY_TIME_DELAY, TimeUnit.MILLISECONDS);
            } else {
                mAudioRouteFuture.complete(new AudioRoute(type, bluetoothAddress, routeInfo));
            }
        }
    }

    private static final long RETRY_TIME_DELAY = 500L;
    private static final int MAX_CONNECTION_RETRIES = 2;
    public static final int TYPE_INVALID = 0;
    public static final int TYPE_EARPIECE = 1;
    public static final int TYPE_WIRED = 2;
    public static final int TYPE_SPEAKER = 3;
    public static final int TYPE_DOCK = 4;
    public static final int TYPE_BLUETOOTH_SCO = 5;
    public static final int TYPE_BLUETOOTH_HA = 6;
    public static final int TYPE_BLUETOOTH_LE = 7;
    public static final int TYPE_STREAMING = 8;
    @IntDef(prefix = "TYPE", value = {
            TYPE_INVALID,
            TYPE_EARPIECE,
            TYPE_WIRED,
            TYPE_SPEAKER,
            TYPE_DOCK,
            TYPE_BLUETOOTH_SCO,
            TYPE_BLUETOOTH_HA,
            TYPE_BLUETOOTH_LE,
            TYPE_STREAMING
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AudioRouteType {}

    private @AudioRouteType int mAudioRouteType;
    private String mBluetoothAddress;
    private AudioDeviceInfo mInfo;
    public static final Set<Integer> BT_AUDIO_DEVICE_INFO_TYPES = Set.of(
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO
    );

    public static final Set<Integer> BT_AUDIO_ROUTE_TYPES = Set.of(
            AudioRoute.TYPE_BLUETOOTH_SCO,
            AudioRoute.TYPE_BLUETOOTH_HA,
            AudioRoute.TYPE_BLUETOOTH_LE
    );

    public static final HashMap<Integer, String> DEVICE_TYPE_STRINGS;
    static {
        DEVICE_TYPE_STRINGS = new HashMap<>();
        DEVICE_TYPE_STRINGS.put(TYPE_EARPIECE, "TYPE_EARPIECE");
        DEVICE_TYPE_STRINGS.put(TYPE_WIRED, "TYPE_WIRED_HEADSET");
        DEVICE_TYPE_STRINGS.put(TYPE_SPEAKER, "TYPE_SPEAKER");
        DEVICE_TYPE_STRINGS.put(TYPE_DOCK, "TYPE_DOCK");
        DEVICE_TYPE_STRINGS.put(TYPE_BLUETOOTH_SCO, "TYPE_BLUETOOTH_SCO");
        DEVICE_TYPE_STRINGS.put(TYPE_BLUETOOTH_HA, "TYPE_BLUETOOTH_HA");
        DEVICE_TYPE_STRINGS.put(TYPE_BLUETOOTH_LE, "TYPE_BLUETOOTH_LE");
        DEVICE_TYPE_STRINGS.put(TYPE_STREAMING, "TYPE_STREAMING");
    }

    public static final HashMap<Integer, Integer> DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE;
    static {
        DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE = new HashMap<>();
        DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE.put(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                TYPE_EARPIECE);
        DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE.put(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, TYPE_SPEAKER);
        DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE.put(AudioDeviceInfo.TYPE_WIRED_HEADSET, TYPE_WIRED);
        DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE.put(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, TYPE_WIRED);
        DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE.put(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                TYPE_BLUETOOTH_SCO);
        DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE.put(AudioDeviceInfo.TYPE_USB_DEVICE, TYPE_WIRED);
        DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE.put(AudioDeviceInfo.TYPE_USB_ACCESSORY, TYPE_WIRED);
        DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE.put(AudioDeviceInfo.TYPE_DOCK, TYPE_DOCK);
        DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE.put(AudioDeviceInfo.TYPE_USB_HEADSET, TYPE_WIRED);
        DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE.put(AudioDeviceInfo.TYPE_HEARING_AID,
                TYPE_BLUETOOTH_HA);
        DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE.put(AudioDeviceInfo.TYPE_BLE_HEADSET,
                TYPE_BLUETOOTH_LE);
        DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE.put(AudioDeviceInfo.TYPE_BLE_SPEAKER,
                TYPE_BLUETOOTH_LE);
        DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE.put(AudioDeviceInfo.TYPE_BLE_BROADCAST,
                TYPE_BLUETOOTH_LE);
        DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE.put(AudioDeviceInfo.TYPE_DOCK_ANALOG, TYPE_DOCK);
    }

    private static final HashMap<Integer, List<Integer>> AUDIO_ROUTE_TYPE_TO_DEVICE_INFO_TYPE;
    static {
        AUDIO_ROUTE_TYPE_TO_DEVICE_INFO_TYPE = new HashMap<>();
        List<Integer> earpieceDeviceInfoTypes = new ArrayList<>();
        earpieceDeviceInfoTypes.add(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);
        AUDIO_ROUTE_TYPE_TO_DEVICE_INFO_TYPE.put(TYPE_EARPIECE, earpieceDeviceInfoTypes);

        List<Integer> wiredDeviceInfoTypes = new ArrayList<>();
        wiredDeviceInfoTypes.add(AudioDeviceInfo.TYPE_WIRED_HEADSET);
        wiredDeviceInfoTypes.add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES);
        wiredDeviceInfoTypes.add(AudioDeviceInfo.TYPE_USB_DEVICE);
        wiredDeviceInfoTypes.add(AudioDeviceInfo.TYPE_USB_ACCESSORY);
        wiredDeviceInfoTypes.add(AudioDeviceInfo.TYPE_USB_HEADSET);
        AUDIO_ROUTE_TYPE_TO_DEVICE_INFO_TYPE.put(TYPE_WIRED, wiredDeviceInfoTypes);

        List<Integer> speakerDeviceInfoTypes = new ArrayList<>();
        speakerDeviceInfoTypes.add(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        AUDIO_ROUTE_TYPE_TO_DEVICE_INFO_TYPE.put(TYPE_SPEAKER, speakerDeviceInfoTypes);

        List<Integer> dockDeviceInfoTypes = new ArrayList<>();
        dockDeviceInfoTypes.add(AudioDeviceInfo.TYPE_DOCK);
        dockDeviceInfoTypes.add(AudioDeviceInfo.TYPE_DOCK_ANALOG);
        AUDIO_ROUTE_TYPE_TO_DEVICE_INFO_TYPE.put(TYPE_DOCK, dockDeviceInfoTypes);

        List<Integer> bluetoothScoDeviceInfoTypes = new ArrayList<>();
        bluetoothScoDeviceInfoTypes.add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
        bluetoothScoDeviceInfoTypes.add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
        AUDIO_ROUTE_TYPE_TO_DEVICE_INFO_TYPE.put(TYPE_BLUETOOTH_SCO, bluetoothScoDeviceInfoTypes);

        List<Integer> bluetoothHearingAidDeviceInfoTypes = new ArrayList<>();
        bluetoothHearingAidDeviceInfoTypes.add(AudioDeviceInfo.TYPE_HEARING_AID);
        AUDIO_ROUTE_TYPE_TO_DEVICE_INFO_TYPE.put(TYPE_BLUETOOTH_HA,
                bluetoothHearingAidDeviceInfoTypes);

        List<Integer> bluetoothLeDeviceInfoTypes = new ArrayList<>();
        bluetoothLeDeviceInfoTypes.add(AudioDeviceInfo.TYPE_BLE_HEADSET);
        bluetoothLeDeviceInfoTypes.add(AudioDeviceInfo.TYPE_BLE_SPEAKER);
        bluetoothLeDeviceInfoTypes.add(AudioDeviceInfo.TYPE_BLE_BROADCAST);
        AUDIO_ROUTE_TYPE_TO_DEVICE_INFO_TYPE.put(TYPE_BLUETOOTH_LE, bluetoothLeDeviceInfoTypes);
    }

    int getType() {
        return mAudioRouteType;
    }

    String getBluetoothAddress() {
        return mBluetoothAddress;
    }

    // Invoked when entered pending route whose dest route is this route
    void onDestRouteAsPendingRoute(boolean active, PendingAudioRoute pendingAudioRoute,
                                   AudioManager audioManager) {
        if (pendingAudioRoute.isActive() && !active) {
            Log.i(this, "clearCommunicationDevice");
            audioManager.clearCommunicationDevice();
        } else if (active) {
            if (mAudioRouteType == TYPE_BLUETOOTH_SCO) {
                pendingAudioRoute.addMessage(BT_AUDIO_CONNECTED);
            } else if (mAudioRouteType == TYPE_SPEAKER) {
                pendingAudioRoute.addMessage(SPEAKER_ON);
            }
            if (!audioManager.setCommunicationDevice(mInfo)) {
                pendingAudioRoute.onMessageReceived(PENDING_ROUTE_FAILED);
            }
        }
    }

    void onOrigRouteAsPendingRoute(boolean active, PendingAudioRoute pendingAudioRoute,
                                   AudioManager audioManager) {
        if (active) {
            if (mAudioRouteType == TYPE_BLUETOOTH_SCO) {
                pendingAudioRoute.addMessage(BT_AUDIO_DISCONNECTED);
            } else if (mAudioRouteType == TYPE_SPEAKER) {
                pendingAudioRoute.addMessage(SPEAKER_OFF);
            }
        }
    }

    @VisibleForTesting
    public AudioRoute(@AudioRouteType int type, String bluetoothAddress, AudioDeviceInfo info) {
        mAudioRouteType = type;
        mBluetoothAddress = bluetoothAddress;
        mInfo = info;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof AudioRoute otherRoute)) {
            return false;
        }
        if (mAudioRouteType != otherRoute.getType()) {
            return false;
        }
        return !BT_AUDIO_ROUTE_TYPES.contains(mAudioRouteType) || mBluetoothAddress.equals(
                otherRoute.getBluetoothAddress());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAudioRouteType, mBluetoothAddress);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[Type=" + DEVICE_TYPE_STRINGS.get(mAudioRouteType)
                + ", Address=" + ((mBluetoothAddress != null) ? mBluetoothAddress : "invalid")
                + "]";
    }
}
