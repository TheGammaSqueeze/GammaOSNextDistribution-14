/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.bluetooth.hearingaid;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import static com.android.bluetooth.Utils.callerIsSystemOrActiveOrManagedUser;
import static com.android.bluetooth.Utils.enforceBluetoothPrivilegedPermission;

import android.annotation.RequiresPermission;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothHearingAid.AdvertisementServiceData;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothHearingAid;
import android.content.AttributionSource;
import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.BluetoothProfileConnectionInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelUuid;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import com.android.bluetooth.BluetoothMetricsProto;
import com.android.bluetooth.BluetoothStatsLog;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.AudioRoutingManager;
import com.android.bluetooth.btservice.MetricsLogger;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ServiceFactory;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.flags.Flags;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.SynchronousResultReceiver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides Bluetooth HearingAid profile, as a service in the Bluetooth application.
 * @hide
 */
public class HearingAidService extends ProfileService {
    private static final boolean DBG = true;
    private static final String TAG = "HearingAidService";

    // Timeout for state machine thread join, to prevent potential ANR.
    private static final int SM_THREAD_JOIN_TIMEOUT_MS = 1000;

    // Upper limit of all HearingAid devices: Bonded or Connected
    private static final int MAX_HEARING_AID_STATE_MACHINES = 10;
    private static HearingAidService sHearingAidService;

    private AdapterService mAdapterService;
    private DatabaseManager mDatabaseManager;
    private HandlerThread mStateMachinesThread;
    private BluetoothDevice mActiveDevice;

    @VisibleForTesting
    HearingAidNativeInterface mHearingAidNativeInterface;
    @VisibleForTesting
    AudioManager mAudioManager;

    private final Map<BluetoothDevice, HearingAidStateMachine> mStateMachines =
            new HashMap<>();
    private final Map<BluetoothDevice, Long> mDeviceHiSyncIdMap = new ConcurrentHashMap<>();
    private final Map<BluetoothDevice, Integer> mDeviceCapabilitiesMap = new HashMap<>();
    private final Map<Long, Boolean> mHiSyncIdConnectedMap = new HashMap<>();
    private long mActiveDeviceHiSyncId = BluetoothHearingAid.HI_SYNC_ID_INVALID;

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private final AudioManagerOnAudioDevicesAddedCallback mAudioManagerOnAudioDevicesAddedCallback =
            new AudioManagerOnAudioDevicesAddedCallback();
    private final AudioManagerOnAudioDevicesRemovedCallback
            mAudioManagerOnAudioDevicesRemovedCallback =
            new AudioManagerOnAudioDevicesRemovedCallback();

    private final ServiceFactory mFactory = new ServiceFactory();

    public HearingAidService(Context ctx) {
        super(ctx);
    }

    public static boolean isEnabled() {
        return BluetoothProperties.isProfileAshaCentralEnabled().orElse(true);
    }

    @Override
    protected IProfileServiceBinder initBinder() {
        return new BluetoothHearingAidBinder(this);
    }

    @Override
    public void start() {
        if (DBG) {
            Log.d(TAG, "start()");
        }
        if (sHearingAidService != null) {
            throw new IllegalStateException("start() called twice");
        }

        mAdapterService = Objects.requireNonNull(AdapterService.getAdapterService(),
                "AdapterService cannot be null when HearingAidService starts");
        mHearingAidNativeInterface = Objects.requireNonNull(HearingAidNativeInterface.getInstance(),
                "HearingAidNativeInterface cannot be null when HearingAidService starts");
        mDatabaseManager = Objects.requireNonNull(mAdapterService.getDatabase(),
                "DatabaseManager cannot be null when HearingAidService starts");
        mAudioManager = getSystemService(AudioManager.class);
        Objects.requireNonNull(mAudioManager,
                "AudioManager cannot be null when HearingAidService starts");

        // Start handler thread for state machines
        mStateMachines.clear();
        mStateMachinesThread = new HandlerThread("HearingAidService.StateMachines");
        mStateMachinesThread.start();

        // Clear HiSyncId map, capabilities map and HiSyncId Connected map
        mDeviceHiSyncIdMap.clear();
        mDeviceCapabilitiesMap.clear();
        mHiSyncIdConnectedMap.clear();

        // Mark service as started
        setHearingAidService(this);

        // Initialize native interface
        mHearingAidNativeInterface.init();
    }

    @Override
    public void stop() {
        if (DBG) {
            Log.d(TAG, "stop()");
        }
        if (sHearingAidService == null) {
            Log.w(TAG, "stop() called before start()");
            return;
        }
        // Cleanup native interface
        mHearingAidNativeInterface.cleanup();
        mHearingAidNativeInterface = null;

        // Mark service as stopped
        setHearingAidService(null);

        // Destroy state machines and stop handler thread
        synchronized (mStateMachines) {
            for (HearingAidStateMachine sm : mStateMachines.values()) {
                sm.doQuit();
                sm.cleanup();
            }
            mStateMachines.clear();
        }

        // Clear HiSyncId map, capabilities map and HiSyncId Connected map
        mDeviceHiSyncIdMap.clear();
        mDeviceCapabilitiesMap.clear();
        mHiSyncIdConnectedMap.clear();

        if (mStateMachinesThread != null) {
            try {
                mStateMachinesThread.quitSafely();
                mStateMachinesThread.join(SM_THREAD_JOIN_TIMEOUT_MS);
                mStateMachinesThread = null;
            } catch (InterruptedException e) {
                // Do not rethrow as we are shutting down anyway
            }
        }

        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }

        mAudioManager.unregisterAudioDeviceCallback(mAudioManagerOnAudioDevicesAddedCallback);
        mAudioManager.unregisterAudioDeviceCallback(mAudioManagerOnAudioDevicesRemovedCallback);

        // Clear AdapterService, HearingAidNativeInterface
        mAudioManager = null;
        mHearingAidNativeInterface = null;
        mAdapterService = null;
    }

    @Override
    public void cleanup() {
        if (DBG) {
            Log.d(TAG, "cleanup()");
        }
    }

    /**
     * Get the HearingAidService instance
     * @return HearingAidService instance
     */
    public static synchronized HearingAidService getHearingAidService() {
        if (sHearingAidService == null) {
            Log.w(TAG, "getHearingAidService(): service is NULL");
            return null;
        }

        if (!sHearingAidService.isAvailable()) {
            Log.w(TAG, "getHearingAidService(): service is not available");
            return null;
        }
        return sHearingAidService;
    }

    @VisibleForTesting
    static synchronized void setHearingAidService(HearingAidService instance) {
        if (DBG) {
            Log.d(TAG, "setHearingAidService(): set to: " + instance);
        }
        sHearingAidService = instance;
    }

    /**
     * Connects the hearing aid profile to the passed in device
     *
     * @param device is the device with which we will connect the hearing aid profile
     * @return true if hearing aid profile successfully connected, false otherwise
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean connect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED,
                "Need BLUETOOTH_PRIVILEGED permission");
        if (DBG) {
            Log.d(TAG, "connect(): " + device);
        }
        if (device == null) {
            return false;
        }

        if (getConnectionPolicy(device) == BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return false;
        }
        ParcelUuid[] featureUuids = mAdapterService.getRemoteUuids(device);
        if (!Utils.arrayContains(featureUuids, BluetoothUuid.HEARING_AID)) {
            Log.e(TAG, "Cannot connect to " + device + " : Remote does not have Hearing Aid UUID");
            return false;
        }

        long hiSyncId = mDeviceHiSyncIdMap.getOrDefault(device,
                BluetoothHearingAid.HI_SYNC_ID_INVALID);

        if (hiSyncId != mActiveDeviceHiSyncId
                && hiSyncId != BluetoothHearingAid.HI_SYNC_ID_INVALID
                && mActiveDeviceHiSyncId != BluetoothHearingAid.HI_SYNC_ID_INVALID) {
            for (BluetoothDevice connectedDevice : getConnectedDevices()) {
                disconnect(connectedDevice);
            }
        }

        synchronized (mStateMachines) {
            HearingAidStateMachine smConnect = getOrCreateStateMachine(device);
            if (smConnect == null) {
                Log.e(TAG, "Cannot connect to " + device + " : no state machine");
            }
            smConnect.sendMessage(HearingAidStateMachine.CONNECT);
        }

        for (BluetoothDevice storedDevice : mDeviceHiSyncIdMap.keySet()) {
            if (device.equals(storedDevice)) {
                continue;
            }
            if (mDeviceHiSyncIdMap.getOrDefault(storedDevice,
                    BluetoothHearingAid.HI_SYNC_ID_INVALID) == hiSyncId) {
                synchronized (mStateMachines) {
                    HearingAidStateMachine sm = getOrCreateStateMachine(storedDevice);
                    if (sm == null) {
                        Log.e(TAG, "Ignored connect request for " + device + " : no state machine");
                        continue;
                    }
                    sm.sendMessage(HearingAidStateMachine.CONNECT);
                }
                if (hiSyncId == BluetoothHearingAid.HI_SYNC_ID_INVALID
                        && !device.equals(storedDevice)) {
                    break;
                }
            }
        }
        return true;
    }

    /**
     * Disconnects hearing aid profile for the passed in device
     *
     * @param device is the device with which we want to disconnected the hearing aid profile
     * @return true if hearing aid profile successfully disconnected, false otherwise
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED,
                "Need BLUETOOTH_PRIVILEGED permission");
        if (DBG) {
            Log.d(TAG, "disconnect(): " + device);
        }
        if (device == null) {
            return false;
        }
        long hiSyncId = mDeviceHiSyncIdMap.getOrDefault(device,
                BluetoothHearingAid.HI_SYNC_ID_INVALID);

        for (BluetoothDevice storedDevice : mDeviceHiSyncIdMap.keySet()) {
            if (mDeviceHiSyncIdMap.getOrDefault(storedDevice,
                    BluetoothHearingAid.HI_SYNC_ID_INVALID) == hiSyncId) {
                synchronized (mStateMachines) {
                    HearingAidStateMachine sm = mStateMachines.get(storedDevice);
                    if (sm == null) {
                        Log.e(TAG, "Ignored disconnect request for " + device
                                + " : no state machine");
                        continue;
                    }
                    sm.sendMessage(HearingAidStateMachine.DISCONNECT);
                }
                if (hiSyncId == BluetoothHearingAid.HI_SYNC_ID_INVALID
                        && !device.equals(storedDevice)) {
                    break;
                }
            }
        }
        return true;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        synchronized (mStateMachines) {
            List<BluetoothDevice> devices = new ArrayList<>();
            for (HearingAidStateMachine sm : mStateMachines.values()) {
                if (sm.isConnected()) {
                    devices.add(sm.getDevice());
                }
            }
            return devices;
        }
    }

    /**
     * Check any peer device is connected.
     * The check considers any peer device is connected.
     *
     * @param device the peer device to connect to
     * @return true if there are any peer device connected.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean isConnectedPeerDevices(BluetoothDevice device) {
        long hiSyncId = getHiSyncId(device);
        if (getConnectedPeerDevices(hiSyncId).isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * Check whether can connect to a peer device.
     * The check considers a number of factors during the evaluation.
     *
     * @param device the peer device to connect to
     * @return true if connection is allowed, otherwise false
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean okToConnect(BluetoothDevice device) {
        // Check if this is an incoming connection in Quiet mode.
        if (mAdapterService.isQuietModeEnabled()) {
            Log.e(TAG, "okToConnect: cannot connect to " + device + " : quiet mode enabled");
            return false;
        }
        // Check connection policy and accept or reject the connection.
        int connectionPolicy = getConnectionPolicy(device);
        int bondState = mAdapterService.getBondState(device);
        // Allow this connection only if the device is bonded. Any attempt to connect while
        // bonding would potentially lead to an unauthorized connection.
        if (bondState != BluetoothDevice.BOND_BONDED) {
            Log.w(TAG, "okToConnect: return false, bondState=" + bondState);
            return false;
        } else if (connectionPolicy != BluetoothProfile.CONNECTION_POLICY_UNKNOWN
                && connectionPolicy != BluetoothProfile.CONNECTION_POLICY_ALLOWED) {
            // Otherwise, reject the connection if connectionPolicy is not valid.
            Log.w(TAG, "okToConnect: return false, connectionPolicy=" + connectionPolicy);
            return false;
        }
        return true;
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        ArrayList<BluetoothDevice> devices = new ArrayList<>();
        if (states == null) {
            return devices;
        }
        final BluetoothDevice[] bondedDevices = mAdapterService.getBondedDevices();
        if (bondedDevices == null) {
            return devices;
        }
        synchronized (mStateMachines) {
            for (BluetoothDevice device : bondedDevices) {
                final ParcelUuid[] featureUuids = device.getUuids();
                if (!Utils.arrayContains(featureUuids, BluetoothUuid.HEARING_AID)) {
                    continue;
                }
                int connectionState = BluetoothProfile.STATE_DISCONNECTED;
                HearingAidStateMachine sm = mStateMachines.get(device);
                if (sm != null) {
                    connectionState = sm.getConnectionState();
                }
                for (int state : states) {
                    if (connectionState == state) {
                        devices.add(device);
                        break;
                    }
                }
            }
            return devices;
        }
    }

    /**
     * Get the list of devices that have state machines.
     *
     * @return the list of devices that have state machines
     */
    @VisibleForTesting
    List<BluetoothDevice> getDevices() {
        List<BluetoothDevice> devices = new ArrayList<>();
        synchronized (mStateMachines) {
            for (HearingAidStateMachine sm : mStateMachines.values()) {
                devices.add(sm.getDevice());
            }
            return devices;
        }
    }

    /**
     * Get the HiSyncIdMap for testing
     *
     * @return mDeviceHiSyncIdMap
     */
    @VisibleForTesting
    Map<BluetoothDevice, Long> getHiSyncIdMap() {
        return mDeviceHiSyncIdMap;
    }

    /**
     * Get the current connection state of the profile
     *
     * @param device is the remote bluetooth device
     * @return {@link BluetoothProfile#STATE_DISCONNECTED} if this profile is disconnected,
     * {@link BluetoothProfile#STATE_CONNECTING} if this profile is being connected,
     * {@link BluetoothProfile#STATE_CONNECTED} if this profile is connected, or
     * {@link BluetoothProfile#STATE_DISCONNECTING} if this profile is being disconnected
     */
    public int getConnectionState(BluetoothDevice device) {
        synchronized (mStateMachines) {
            HearingAidStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }
            return sm.getConnectionState();
        }
    }

    /**
     * Set connection policy of the profile and connects it if connectionPolicy is
     * {@link BluetoothProfile#CONNECTION_POLICY_ALLOWED} or disconnects if connectionPolicy is
     * {@link BluetoothProfile#CONNECTION_POLICY_FORBIDDEN}
     *
     * <p> The device should already be paired.
     * Connection policy can be one of:
     * {@link BluetoothProfile#CONNECTION_POLICY_ALLOWED},
     * {@link BluetoothProfile#CONNECTION_POLICY_FORBIDDEN},
     * {@link BluetoothProfile#CONNECTION_POLICY_UNKNOWN}
     *
     * @param device Paired bluetooth device
     * @param connectionPolicy is the connection policy to set to for this profile
     * @return true if connectionPolicy is set, false on error
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean setConnectionPolicy(BluetoothDevice device, int connectionPolicy) {
        enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED,
                "Need BLUETOOTH_PRIVILEGED permission");
        if (DBG) {
            Log.d(TAG, "Saved connectionPolicy " + device + " = " + connectionPolicy);
        }

        if (!mDatabaseManager.setProfileConnectionPolicy(device, BluetoothProfile.HEARING_AID,
                  connectionPolicy)) {
            return false;
        }
        if (connectionPolicy == BluetoothProfile.CONNECTION_POLICY_ALLOWED) {
            connect(device);
        } else if (connectionPolicy == BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            disconnect(device);
        }
        return true;
    }

    /**
     * Get the connection policy of the profile.
     *
     * <p> The connection policy can be any of:
     * {@link BluetoothProfile#CONNECTION_POLICY_ALLOWED},
     * {@link BluetoothProfile#CONNECTION_POLICY_FORBIDDEN},
     * {@link BluetoothProfile#CONNECTION_POLICY_UNKNOWN}
     *
     * @param device Bluetooth device
     * @return connection policy of the device
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public int getConnectionPolicy(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED,
                "Need BLUETOOTH_PRIVILEGED permission");
        return mDatabaseManager
                .getProfileConnectionPolicy(device, BluetoothProfile.HEARING_AID);
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    void setVolume(int volume) {
        enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED,
                "Need BLUETOOTH_PRIVILEGED permission");
        mHearingAidNativeInterface.setVolume(volume);
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public long getHiSyncId(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED,
                "Need BLUETOOTH_PRIVILEGED permission");
        if (device == null) {
            return BluetoothHearingAid.HI_SYNC_ID_INVALID;
        }
        return mDeviceHiSyncIdMap.getOrDefault(device, BluetoothHearingAid.HI_SYNC_ID_INVALID);
    }

    int getCapabilities(BluetoothDevice device) {
        return mDeviceCapabilitiesMap.getOrDefault(device, -1);
    }

    @RequiresPermission(
            allOf = {
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_PRIVILEGED,
            })
    private AdvertisementServiceData getAdvertisementServiceData(
            BluetoothDevice device, AttributionSource attributionSource) {
        AdapterService service = mAdapterService;
        if (service == null
                || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getAdvertisementServiceData")
                || !Utils.checkScanPermissionForDataDelivery(service, attributionSource, TAG)) {
            return null;
        }
        enforceBluetoothPrivilegedPermission(service);

        int capability = service.getAshaCapability(device);
        int id = service.getAshaTruncatedHiSyncId(device);
        if (capability < 0) {
            Log.i(TAG, "device does not have AdvertisementServiceData");
            return null;
        }
        return new AdvertisementServiceData(capability, id);
    }

    /**
     * Remove the active device.
     *
     * @param stopAudio whether to stop current media playback.
     * @return true on success, otherwise false
     */
    public boolean removeActiveDevice(boolean stopAudio) {
        if (DBG) {
            Log.d(TAG, "removeActiveDevice: stopAudio=" + stopAudio);
        }
        synchronized (mStateMachines) {
            if (mActiveDeviceHiSyncId != BluetoothHearingAid.HI_SYNC_ID_INVALID) {
                reportActiveDevice(null, stopAudio);
                mActiveDeviceHiSyncId = BluetoothHearingAid.HI_SYNC_ID_INVALID;
            }
        }
        return true;
    }

    /**
     * Set the active device.
     *
     * @param device the new active device. Should not be null.
     * @return true on success, otherwise false
     */
    public boolean setActiveDevice(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "setActiveDevice: device should not be null!");
            return removeActiveDevice(true);
        }
        if (DBG) {
            Log.d(TAG, "setActiveDevice: " + device);
        }
        synchronized (mStateMachines) {
            /* No action needed since this is the same device as previousely activated */
            if (device.equals(mActiveDevice)) {
                if (DBG) {
                    Log.d(TAG, "setActiveDevice: The device is already active. Ignoring.");
                }
                return true;
            }

            if (getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
                Log.e(TAG, "setActiveDevice(" + device + "): failed because device not connected");
                return false;
            }
            Long deviceHiSyncId = mDeviceHiSyncIdMap.getOrDefault(device,
                    BluetoothHearingAid.HI_SYNC_ID_INVALID);
            if (deviceHiSyncId != mActiveDeviceHiSyncId) {
                mActiveDeviceHiSyncId = deviceHiSyncId;
                reportActiveDevice(device, false);
            }
        }
        return true;
    }

    /**
     * Get the connected physical Hearing Aid devices that are active
     *
     * @return the list of active devices. The first element is the left active
     * device; the second element is the right active device. If either or both side
     * is not active, it will be null on that position
     */
    public List<BluetoothDevice> getActiveDevices() {
        ArrayList<BluetoothDevice> activeDevices = new ArrayList<>();
        activeDevices.add(null);
        activeDevices.add(null);
        synchronized (mStateMachines) {
            if (mActiveDeviceHiSyncId == BluetoothHearingAid.HI_SYNC_ID_INVALID) {
                return activeDevices;
            }
            for (BluetoothDevice device : mDeviceHiSyncIdMap.keySet()) {
                if (getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
                    continue;
                }
                if (mDeviceHiSyncIdMap.get(device) == mActiveDeviceHiSyncId) {
                    int deviceSide = getCapabilities(device) & 1;
                    if (deviceSide == BluetoothHearingAid.SIDE_RIGHT) {
                        activeDevices.set(1, device);
                    } else {
                        activeDevices.set(0, device);
                    }
                }
            }
        }
        return activeDevices;
    }

    void messageFromNative(HearingAidStackEvent stackEvent) {
        Objects.requireNonNull(stackEvent.device,
                "Device should never be null, event: " + stackEvent);

        if (stackEvent.type == HearingAidStackEvent.EVENT_TYPE_DEVICE_AVAILABLE) {
            BluetoothDevice device = stackEvent.device;
            int capabilities = stackEvent.valueInt1;
            long hiSyncId = stackEvent.valueLong2;
            if (DBG) {
                Log.d(TAG, "Device available: device=" + device + " capabilities="
                        + capabilities + " hiSyncId=" + hiSyncId);
            }
            mDeviceCapabilitiesMap.put(device, capabilities);
            mDeviceHiSyncIdMap.put(device, hiSyncId);
            return;
        }

        synchronized (mStateMachines) {
            BluetoothDevice device = stackEvent.device;
            HearingAidStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                if (stackEvent.type == HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED) {
                    switch (stackEvent.valueInt1) {
                        case HearingAidStackEvent.CONNECTION_STATE_CONNECTED:
                        case HearingAidStackEvent.CONNECTION_STATE_CONNECTING:
                            sm = getOrCreateStateMachine(device);
                            break;
                        default:
                            break;
                    }
                }
            }
            if (sm == null) {
                Log.e(TAG, "Cannot process stack event: no state machine: " + stackEvent);
                return;
            }
            sm.sendMessage(HearingAidStateMachine.STACK_EVENT, stackEvent);
        }
    }

    private void notifyActiveDeviceChanged() {
        mAdapterService.handleActiveDeviceChange(BluetoothProfile.HEARING_AID, mActiveDevice);
        Intent intent = new Intent(BluetoothHearingAid.ACTION_ACTIVE_DEVICE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mActiveDevice);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        sendBroadcast(intent, BLUETOOTH_CONNECT);
    }

    /* Notifications of audio device disconnection events. */
    private class AudioManagerOnAudioDevicesRemovedCallback extends AudioDeviceCallback {
        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            for (AudioDeviceInfo deviceInfo : removedDevices) {
                if (deviceInfo.getType() == AudioDeviceInfo.TYPE_HEARING_AID) {
                    if (DBG) {
                        Log.d(TAG, " onAudioDevicesRemoved: device type: " + deviceInfo.getType());
                    }
                    if (mAudioManager != null) {
                        notifyActiveDeviceChanged();
                        mAudioManager.unregisterAudioDeviceCallback(this);
                    } else {
                        Log.w(TAG, "onAudioDevicesRemoved: mAudioManager is null");
                    }
                }
            }
        }
    }

    /* Notifications of audio device connection events. */
    private class AudioManagerOnAudioDevicesAddedCallback extends AudioDeviceCallback {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            for (AudioDeviceInfo deviceInfo : addedDevices) {
                if (deviceInfo.getType() == AudioDeviceInfo.TYPE_HEARING_AID) {
                    if (DBG) {
                        Log.d(TAG, " onAudioDevicesAdded: device type: " + deviceInfo.getType());
                    }
                    if (mAudioManager != null) {
                        notifyActiveDeviceChanged();
                        mAudioManager.unregisterAudioDeviceCallback(this);
                    } else {
                        Log.w(TAG, "onAudioDevicesAdded: mAudioManager is null");
                    }
                }
            }
        }
    }

    private HearingAidStateMachine getOrCreateStateMachine(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "getOrCreateStateMachine failed: device cannot be null");
            return null;
        }
        synchronized (mStateMachines) {
            HearingAidStateMachine sm = mStateMachines.get(device);
            if (sm != null) {
                return sm;
            }
            // Limit the maximum number of state machines to avoid DoS attack
            if (mStateMachines.size() >= MAX_HEARING_AID_STATE_MACHINES) {
                Log.e(TAG, "Maximum number of HearingAid state machines reached: "
                        + MAX_HEARING_AID_STATE_MACHINES);
                return null;
            }
            if (DBG) {
                Log.d(TAG, "Creating a new state machine for " + device);
            }
            sm = HearingAidStateMachine.make(device, this,
                    mHearingAidNativeInterface, mStateMachinesThread.getLooper());
            mStateMachines.put(device, sm);
            return sm;
        }
    }

    /**
     * Report the active device change to the active device manager and the media framework.
     *
     * @param device the new active device; or null if no active device
     * @param stopAudio whether to stop audio when device is null.
     */
    private void reportActiveDevice(BluetoothDevice device, boolean stopAudio) {
        if (DBG) {
            Log.d(TAG, "reportActiveDevice: device=" + device + " stopAudio=" + stopAudio);
        }

        if (device != null && stopAudio) {
            Log.e(TAG, "Illegal arguments: stopAudio should be false when device is not null!");
            stopAudio = false;
        }

        // Note: This is just a safety check for handling illegal call - setActiveDevice(null).
        if (device == null && stopAudio
                && getConnectionState(mActiveDevice) == BluetoothProfile.STATE_CONNECTED) {
            Log.e(TAG, "Illegal arguments: stopAudio should be false when the active hearing aid "
                    + "is still connected!");
            stopAudio = false;
        }

        BluetoothDevice previousAudioDevice = mActiveDevice;

        mActiveDevice = device;

        BluetoothStatsLog.write(BluetoothStatsLog.BLUETOOTH_ACTIVE_DEVICE_CHANGED,
                BluetoothProfile.HEARING_AID, mAdapterService.obfuscateAddress(device),
                mAdapterService.getMetricId(device));

        if (DBG) {
            Log.d(TAG, "Hearing Aid audio: " + previousAudioDevice + " -> " + device
                    + ". Stop audio: " + stopAudio);
        }

        if (device != null) {
            mAudioManager.registerAudioDeviceCallback(mAudioManagerOnAudioDevicesAddedCallback,
                    mHandler);
        } else {
            mAudioManager.registerAudioDeviceCallback(mAudioManagerOnAudioDevicesRemovedCallback,
                    mHandler);
        }

        mAudioManager.handleBluetoothActiveDeviceChanged(device, previousAudioDevice,
                BluetoothProfileConnectionInfo.createHearingAidInfo(!stopAudio));
    }

    /** Process a change in the bonding state for a device */
    public void handleBondStateChanged(BluetoothDevice device, int fromState, int toState) {
        mHandler.post(() -> bondStateChanged(device, toState));
    }

    /**
     * Remove state machine if the bonding for a device is removed
     *
     * @param device the device whose bonding state has changed
     * @param bondState the new bond state for the device. Possible values are: {@link
     *     BluetoothDevice#BOND_NONE}, {@link BluetoothDevice#BOND_BONDING}, {@link
     *     BluetoothDevice#BOND_BONDED}.
     */
    @VisibleForTesting
    void bondStateChanged(BluetoothDevice device, int bondState) {
        if (DBG) {
            Log.d(TAG, "Bond state changed for device: " + device + " state: " + bondState);
        }
        // Remove state machine if the bonding for a device is removed
        if (bondState != BluetoothDevice.BOND_NONE) {
            return;
        }
        mDeviceHiSyncIdMap.remove(device);
        synchronized (mStateMachines) {
            HearingAidStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                return;
            }
            if (sm.getConnectionState() != BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnecting device because it was unbonded.");
                disconnect(device);
                return;
            }
            removeStateMachine(device);
        }
    }

    private void removeStateMachine(BluetoothDevice device) {
        synchronized (mStateMachines) {
            HearingAidStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                Log.w(TAG, "removeStateMachine: device " + device
                        + " does not have a state machine");
                return;
            }
            Log.i(TAG, "removeStateMachine: removing state machine for device: " + device);
            sm.doQuit();
            sm.cleanup();
            mStateMachines.remove(device);
        }
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public List<BluetoothDevice> getConnectedPeerDevices(long hiSyncId) {
        List<BluetoothDevice> result = new ArrayList<>();
        for (BluetoothDevice peerDevice : getConnectedDevices()) {
            if (getHiSyncId(peerDevice) == hiSyncId) {
                result.add(peerDevice);
            }
        }
        return result;
    }

    synchronized void connectionStateChanged(BluetoothDevice device, int fromState,
                                                     int toState) {
        if ((device == null) || (fromState == toState)) {
            Log.e(TAG, "connectionStateChanged: unexpected invocation. device=" + device
                    + " fromState=" + fromState + " toState=" + toState);
            return;
        }
        if (toState == BluetoothProfile.STATE_CONNECTED) {
            long myHiSyncId = getHiSyncId(device);
            if (myHiSyncId == BluetoothHearingAid.HI_SYNC_ID_INVALID
                    || getConnectedPeerDevices(myHiSyncId).size() == 1) {
                // Log hearing aid connection event if we are the first device in a set
                // Or when the hiSyncId has not been found
                MetricsLogger.logProfileConnectionEvent(
                        BluetoothMetricsProto.ProfileId.HEARING_AID);
            }
            if (!mHiSyncIdConnectedMap.getOrDefault(myHiSyncId, false)) {
                mHiSyncIdConnectedMap.put(myHiSyncId, true);
            }
        }
        if (fromState == BluetoothProfile.STATE_CONNECTED && getConnectedDevices().isEmpty()) {
            long myHiSyncId = getHiSyncId(device);
            mHiSyncIdConnectedMap.put(myHiSyncId, false);
            // ActiveDeviceManager will call removeActiveDevice().
        }
        // Check if the device is disconnected - if unbond, remove the state machine
        if (toState == BluetoothProfile.STATE_DISCONNECTED) {
            int bondState = mAdapterService.getBondState(device);
            if (bondState == BluetoothDevice.BOND_NONE) {
                if (DBG) {
                    Log.d(TAG, device + " is unbond. Remove state machine");
                }
                removeStateMachine(device);
            }
        }
        mAdapterService.notifyProfileConnectionStateChangeToGatt(
                BluetoothProfile.HEARING_AID, fromState, toState);
        mAdapterService
                .getActiveDeviceManager()
                .profileConnectionStateChanged(
                        BluetoothProfile.HEARING_AID, device, fromState, toState);
        mAdapterService.updateProfileConnectionAdapterProperties(
                device, BluetoothProfile.HEARING_AID, toState, fromState);
    }

    /**
     * Binder object: must be a static class or memory leak may occur
     */
    @VisibleForTesting
    static class BluetoothHearingAidBinder extends IBluetoothHearingAid.Stub
            implements IProfileServiceBinder {
        @VisibleForTesting
        boolean mIsTesting = false;
        private HearingAidService mService;

        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        private HearingAidService getService(AttributionSource source) {
            if (mIsTesting) {
                return mService;
            }
            if (!Utils.checkServiceAvailable(mService, TAG)
                    || !Utils.checkCallerIsSystemOrActiveOrManagedUser(mService, TAG)
                    || !Utils.checkConnectPermissionForDataDelivery(mService, source, TAG)) {
                return null;
            }
            return mService;
        }

        BluetoothHearingAidBinder(HearingAidService svc) {
            mService = svc;
        }

        @Override
        public void cleanup() {
            mService = null;
        }

        @Override
        public void connect(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HearingAidService service = getService(source);
                boolean result = false;
                if (service != null) {
                    result = service.connect(device);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void disconnect(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HearingAidService service = getService(source);
                boolean result = false;
                if (service != null) {
                    result = service.disconnect(device);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectedDevices(AttributionSource source,
               SynchronousResultReceiver receiver) {
            try {
                HearingAidService service = getService(source);
                List<BluetoothDevice> devices = new ArrayList<>();
                if (service != null) {
                    devices = service.getConnectedDevices();
                }
                receiver.send(devices);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getDevicesMatchingConnectionStates(int[] states,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                HearingAidService service = getService(source);
                List<BluetoothDevice> devices = new ArrayList<>();
                if (service != null) {
                    devices = service.getDevicesMatchingConnectionStates(states);
                }
                receiver.send(devices);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectionState(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HearingAidService service = getService(source);
                int state = BluetoothProfile.STATE_DISCONNECTED;
                if (service != null) {
                    state = service.getConnectionState(device);
                }
                receiver.send(state);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setActiveDevice(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HearingAidService service = getService(source);
                if (service != null) {
                    if (Flags.audioRoutingCentralization()) {
                        ((AudioRoutingManager) service.mAdapterService.getActiveDeviceManager())
                                .activateDeviceProfile(
                                        device, BluetoothProfile.HEARING_AID, receiver);
                    } else {
                        boolean result;
                        if (device == null) {
                            result = service.removeActiveDevice(false);
                        } else {
                            result = service.setActiveDevice(device);
                        }
                        receiver.send(result);
                    }
                } else {
                    receiver.send(false);
                }
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getActiveDevices(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HearingAidService service = getService(source);
                List<BluetoothDevice> devices = new ArrayList<>();
                if (service != null) {
                    devices = service.getActiveDevices();
                }
                receiver.send(devices);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setConnectionPolicy(BluetoothDevice device, int connectionPolicy,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                HearingAidService service = getService(source);
                boolean result = false;
                if (service != null) {
                    result = service.setConnectionPolicy(device, connectionPolicy);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectionPolicy(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HearingAidService service = getService(source);
                int policy = BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
                if (service != null) {
                    policy = service.getConnectionPolicy(device);
                }
                receiver.send(policy);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setVolume(int volume, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HearingAidService service = getService(source);
                if (service != null) {
                    service.setVolume(volume);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getHiSyncId(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HearingAidService service = getService(source);
                long id = BluetoothHearingAid.HI_SYNC_ID_INVALID;
                if (service != null) {
                    id = service.getHiSyncId(device);
                }
                receiver.send(id);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getDeviceSide(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HearingAidService service = getService(source);
                int side = BluetoothHearingAid.SIDE_UNKNOWN;
                if (service != null) {
                    side = service.getCapabilities(device);
                    if (side != BluetoothHearingAid.SIDE_UNKNOWN) {
                        side &= 1;
                    }
                }
                receiver.send(side);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getDeviceMode(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HearingAidService service = getService(source);
                int mode = BluetoothHearingAid.MODE_UNKNOWN;
                if (service != null) {
                    mode = service.getCapabilities(device);
                    if (mode != BluetoothHearingAid.MODE_UNKNOWN) {
                        mode = mode >> 1 & 1;
                    }
                }
                receiver.send(mode);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @RequiresPermission(
                allOf = {
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_PRIVILEGED,
                })
        @Override
        public void getAdvertisementServiceData(
                BluetoothDevice device,
                AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                if (!Utils.checkServiceAvailable(mService, TAG)
                        || !Utils.checkCallerIsSystemOrActiveOrManagedUser(mService, TAG)
                        || !Utils.checkScanPermissionForDataDelivery(mService, source, TAG)) {
                    receiver.send(null);
                    return;
                }
                receiver.send(mService.getAdvertisementServiceData(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        for (HearingAidStateMachine sm : mStateMachines.values()) {
            sm.dump(sb);
        }
    }
}
