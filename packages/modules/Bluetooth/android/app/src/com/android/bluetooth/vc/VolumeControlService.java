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

package com.android.bluetooth.vc;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import static com.android.bluetooth.Utils.enforceBluetoothPrivilegedPermission;

import android.annotation.RequiresPermission;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothCsipSetCoordinator;
import android.bluetooth.IBluetoothLeAudio;
import android.bluetooth.IBluetoothVolumeControl;
import android.bluetooth.IBluetoothVolumeControlCallback;
import android.content.AttributionSource;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ServiceFactory;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.csip.CsipSetCoordinatorService;
import com.android.bluetooth.flags.FeatureFlags;
import com.android.bluetooth.flags.FeatureFlagsImpl;
import com.android.bluetooth.le_audio.LeAudioService;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.SynchronousResultReceiver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class VolumeControlService extends ProfileService {
    private static final String TAG = "VolumeControlService";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    // Timeout for state machine thread join, to prevent potential ANR.
    private static final int SM_THREAD_JOIN_TIMEOUT_MS = 1000;

    // Upper limit of all VolumeControl devices: Bonded or Connected
    private static final int MAX_VC_STATE_MACHINES = 10;
    private static final int LE_AUDIO_MAX_VOL = 255;
    private static final int LE_AUDIO_MIN_VOL = 0;

    private static VolumeControlService sVolumeControlService;

    private AdapterService mAdapterService;
    private DatabaseManager mDatabaseManager;
    private HandlerThread mStateMachinesThread;
    private BluetoothDevice mPreviousAudioDevice;
    private Handler mHandler = null;
    private FeatureFlags mFeatureFlags;

    @VisibleForTesting RemoteCallbackList<IBluetoothVolumeControlCallback> mCallbacks;

    @VisibleForTesting
    static class VolumeControlOffsetDescriptor {
        Map<Integer, Descriptor> mVolumeOffsets;

        private class Descriptor {
            Descriptor() {
                mValue = 0;
                mLocation = 0;
                mDescription = null;
            }
            int mValue;
            int mLocation;
            String mDescription;
        };

        VolumeControlOffsetDescriptor() {
            mVolumeOffsets = new HashMap<>();
        }

        int size() {
            return mVolumeOffsets.size();
        }

        void add(int id) {
            Descriptor d = mVolumeOffsets.get(id);
            if (d == null) {
                mVolumeOffsets.put(id, new Descriptor());
            }
        }

        boolean setValue(int id, int value) {
            Descriptor d = mVolumeOffsets.get(id);
            if (d == null) {
                return false;
            }
            d.mValue = value;
            return true;
        }

        int getValue(int id) {
            Descriptor d = mVolumeOffsets.get(id);
            if (d == null) {
                return 0;
            }
            return d.mValue;
        }

        boolean setDescription(int id, String desc) {
            Descriptor d = mVolumeOffsets.get(id);
            if (d == null) {
                return false;
            }
            d.mDescription = desc;
            return true;
        }

        String getDescription(int id) {
            Descriptor d = mVolumeOffsets.get(id);
            if (d == null) {
                return null;
            }
            return d.mDescription;
        }

        boolean setLocation(int id, int location) {
            Descriptor d = mVolumeOffsets.get(id);
            if (d == null) {
                return false;
            }
            d.mLocation = location;
            return true;
        }

        int getLocation(int id) {
            Descriptor d = mVolumeOffsets.get(id);
            if (d == null) {
                return 0;
            }
            return d.mLocation;
        }

        void remove(int id) {
            mVolumeOffsets.remove(id);
        }

        void clear() {
            mVolumeOffsets.clear();
        }

        void dump(StringBuilder sb) {
            for (Map.Entry<Integer, Descriptor> entry : mVolumeOffsets.entrySet()) {
                Descriptor descriptor = entry.getValue();
                Integer id = entry.getKey();
                ProfileService.println(sb, "        Id: " + id);
                ProfileService.println(sb, "        value: " + descriptor.mValue);
                ProfileService.println(sb, "        location: " + descriptor.mLocation);
                ProfileService.println(sb, "        description: " + descriptor.mDescription);
            }
        }
    }

    VolumeControlNativeInterface mVolumeControlNativeInterface;
    @VisibleForTesting
    AudioManager mAudioManager;

    private final Map<BluetoothDevice, VolumeControlStateMachine> mStateMachines = new HashMap<>();
    private final Map<BluetoothDevice, VolumeControlOffsetDescriptor> mAudioOffsets =
                                                                            new HashMap<>();
    private final Map<Integer, Integer> mGroupVolumeCache = new HashMap<>();
    private final Map<Integer, Boolean> mGroupMuteCache = new HashMap<>();
    private final Map<BluetoothDevice, Integer> mDeviceVolumeCache = new HashMap<>();

    @VisibleForTesting
    ServiceFactory mFactory = new ServiceFactory();

    public VolumeControlService(Context ctx) {
        super(ctx);
        mFeatureFlags = new FeatureFlagsImpl();
    }

    @VisibleForTesting
    VolumeControlService(Context ctx, FeatureFlags featureFlags) {
        super(ctx);
        mFeatureFlags = featureFlags;
    }

    public static boolean isEnabled() {
        return BluetoothProperties.isProfileVcpControllerEnabled().orElse(false);
    }

    @Override
    protected IProfileServiceBinder initBinder() {
        return new BluetoothVolumeControlBinder(this);
    }

    @Override
    public void start() {
        if (DBG) {
            Log.d(TAG, "start()");
        }
        if (sVolumeControlService != null) {
            throw new IllegalStateException("start() called twice");
        }

        // Get AdapterService, VolumeControlNativeInterface, DatabaseManager, AudioManager.
        // None of them can be null.
        mAdapterService = Objects.requireNonNull(AdapterService.getAdapterService(),
                "AdapterService cannot be null when VolumeControlService starts");
        mDatabaseManager = Objects.requireNonNull(mAdapterService.getDatabase(),
                "DatabaseManager cannot be null when VolumeControlService starts");
        mVolumeControlNativeInterface = Objects.requireNonNull(
                VolumeControlNativeInterface.getInstance(),
                "VolumeControlNativeInterface cannot be null when VolumeControlService starts");
        mAudioManager =  getSystemService(AudioManager.class);
        Objects.requireNonNull(mAudioManager,
                "AudioManager cannot be null when VolumeControlService starts");

        // Start handler thread for state machines
        mHandler = new Handler(Looper.getMainLooper());
        mStateMachines.clear();
        mStateMachinesThread = new HandlerThread("VolumeControlService.StateMachines");
        mStateMachinesThread.start();

        mAudioOffsets.clear();
        mGroupVolumeCache.clear();
        mGroupMuteCache.clear();
        mDeviceVolumeCache.clear();
        mCallbacks = new RemoteCallbackList<IBluetoothVolumeControlCallback>();

        // Mark service as started
        setVolumeControlService(this);

        // Initialize native interface
        mVolumeControlNativeInterface.init();
    }

    @Override
    public void stop() {
        if (DBG) {
            Log.d(TAG, "stop()");
        }
        if (sVolumeControlService == null) {
            Log.w(TAG, "stop() called before start()");
            return;
        }

        // Mark service as stopped
        setVolumeControlService(null);

        // Destroy state machines and stop handler thread
        synchronized (mStateMachines) {
            for (VolumeControlStateMachine sm : mStateMachines.values()) {
                sm.doQuit();
                sm.cleanup();
            }
            mStateMachines.clear();
        }

        if (mStateMachinesThread != null) {
            try {
                mStateMachinesThread.quitSafely();
                mStateMachinesThread.join(SM_THREAD_JOIN_TIMEOUT_MS);
                mStateMachinesThread = null;
            } catch (InterruptedException e) {
                // Do not rethrow as we are shutting down anyway
            }
        }

        // Unregister handler and remove all queued messages.
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }

        // Cleanup native interface
        mVolumeControlNativeInterface.cleanup();
        mVolumeControlNativeInterface = null;

        mAudioOffsets.clear();
        mGroupVolumeCache.clear();
        mGroupMuteCache.clear();
        mDeviceVolumeCache.clear();

        // Clear AdapterService, VolumeControlNativeInterface
        mAudioManager = null;
        mVolumeControlNativeInterface = null;
        mAdapterService = null;

        if (mCallbacks != null) {
            mCallbacks.kill();
            mCallbacks = null;
        }
    }

    @Override
    public void cleanup() {
        if (DBG) {
            Log.d(TAG, "cleanup()");
        }
    }

    /**
     * Get the VolumeControlService instance
     * @return VolumeControlService instance
     */
    public static synchronized VolumeControlService getVolumeControlService() {
        if (sVolumeControlService == null) {
            Log.w(TAG, "getVolumeControlService(): service is NULL");
            return null;
        }

        if (!sVolumeControlService.isAvailable()) {
            Log.w(TAG, "getVolumeControlService(): service is not available");
            return null;
        }
        return sVolumeControlService;
    }

    @VisibleForTesting
    static synchronized void setVolumeControlService(VolumeControlService instance) {
        if (DBG) {
            Log.d(TAG, "setVolumeControlService(): set to: " + instance);
        }
        sVolumeControlService = instance;
    }

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
        if (!Utils.arrayContains(featureUuids, BluetoothUuid.VOLUME_CONTROL)) {
            Log.e(TAG, "Cannot connect to " + device
                    + " : Remote does not have Volume Control UUID");
            return false;
        }


        synchronized (mStateMachines) {
            VolumeControlStateMachine smConnect = getOrCreateStateMachine(device);
            if (smConnect == null) {
                Log.e(TAG, "Cannot connect to " + device + " : no state machine");
            }
            smConnect.sendMessage(VolumeControlStateMachine.CONNECT);
        }

        return true;
    }

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
        synchronized (mStateMachines) {
            VolumeControlStateMachine sm = getOrCreateStateMachine(device);
            if (sm != null) {
                sm.sendMessage(VolumeControlStateMachine.DISCONNECT);
            }
        }

        return true;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        synchronized (mStateMachines) {
            List<BluetoothDevice> devices = new ArrayList<>();
            for (VolumeControlStateMachine sm : mStateMachines.values()) {
                if (sm.isConnected()) {
                    devices.add(sm.getDevice());
                }
            }
            return devices;
        }
    }

    /**
     * Check whether can connect to a peer device.
     * The check considers a number of factors during the evaluation.
     *
     * @param device the peer device to connect to
     * @return true if connection is allowed, otherwise false
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public boolean okToConnect(BluetoothDevice device) {
        /* Make sure device is valid */
        if (device == null) {
            Log.e(TAG, "okToConnect: Invalid device");
            return false;
        }
        // Check if this is an incoming connection in Quiet mode.
        if (mAdapterService.isQuietModeEnabled()) {
            Log.e(TAG, "okToConnect: cannot connect to " + device + " : quiet mode enabled");
            return false;
        }
        // Check connectionPolicy and accept or reject the connection.
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

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED,
                "Need BLUETOOTH_PRIVILEGED permission");
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
                if (!Utils.arrayContains(featureUuids, BluetoothUuid.VOLUME_CONTROL)) {
                    continue;
                }
                int connectionState = BluetoothProfile.STATE_DISCONNECTED;
                VolumeControlStateMachine sm = mStateMachines.get(device);
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
            for (VolumeControlStateMachine sm : mStateMachines.values()) {
                devices.add(sm.getDevice());
            }
            return devices;
        }
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_CONNECT,
                "Need BLUETOOTH_CONNECT permission");
        synchronized (mStateMachines) {
            VolumeControlStateMachine sm = mStateMachines.get(device);
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
     * @param device the remote device
     * @param connectionPolicy is the connection policy to set to for this profile
     * @return true on success, otherwise false
     */
    public boolean setConnectionPolicy(BluetoothDevice device, int connectionPolicy) {
        if (DBG) {
            Log.d(TAG, "Saved connectionPolicy " + device + " = " + connectionPolicy);
        }
        mDatabaseManager.setProfileConnectionPolicy(device, BluetoothProfile.VOLUME_CONTROL,
                        connectionPolicy);
        if (connectionPolicy == BluetoothProfile.CONNECTION_POLICY_ALLOWED) {
            connect(device);
        } else if (connectionPolicy == BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            disconnect(device);
        }
        return true;
    }

    public int getConnectionPolicy(BluetoothDevice device) {
        return mDatabaseManager.getProfileConnectionPolicy(device, BluetoothProfile.VOLUME_CONTROL);
    }

    boolean isVolumeOffsetAvailable(BluetoothDevice device) {
        VolumeControlOffsetDescriptor offsets = mAudioOffsets.get(device);
        if (offsets == null) {
            Log.i(TAG, " There is no offset service for device: " + device);
            return false;
        }
        Log.i(TAG, " Offset service available for device: " + device);
        return true;
    }

    int getNumberOfVolumeOffsetInstances(BluetoothDevice device) {
        VolumeControlOffsetDescriptor offsets = mAudioOffsets.get(device);
        if (offsets == null) {
            Log.i(TAG, " There is no offset service for device: " + device);
            return 0;
        }

        int numberOfInstances = offsets.size();

        Log.i(TAG, "Number of VOCS: " + numberOfInstances + ", for device: " + device);
        return numberOfInstances;
    }

    void setVolumeOffset(BluetoothDevice device, int instanceId, int volumeOffset) {
        VolumeControlOffsetDescriptor offsets = mAudioOffsets.get(device);
        if (offsets == null) {
            Log.e(TAG, " There is no offset service for device: " + device);
            return;
        }

        int numberOfInstances = offsets.size();
        if (instanceId > numberOfInstances) {
            Log.e(
                    TAG,
                    "Selected VOCS instance ID: "
                            + instanceId
                            + ", exceed available IDs: "
                            + numberOfInstances
                            + ", for device: "
                            + device);
            return;
        }

        int value = offsets.getValue(instanceId);
        if (value == volumeOffset) {
            /* Nothing to do - offset already applied */
            return;
        }

        mVolumeControlNativeInterface.setExtAudioOutVolumeOffset(device, instanceId, volumeOffset);
    }

    void setDeviceVolume(BluetoothDevice device, int volume, boolean isGroupOp) {
        if (!mFeatureFlags.leaudioBroadcastVolumeControlForConnectedDevices()) {
            return;
        }
        if (DBG) {
            Log.d(
                    TAG,
                    "setDeviceVolume: "
                            + device
                            + ", volume: "
                            + volume
                            + ", isGroupOp: "
                            + isGroupOp);
        }

        LeAudioService leAudioService = mFactory.getLeAudioService();
        if (leAudioService == null) {
            Log.e(TAG, "leAudioService not available");
            return;
        }
        int groupId = leAudioService.getGroupId(device);
        if (groupId == IBluetoothLeAudio.LE_AUDIO_GROUP_ID_INVALID) {
            Log.e(TAG, "Device not a part of a group");
            return;
        }

        if (isGroupOp) {
            setGroupVolume(groupId, volume);
        } else {
            Log.i(TAG, "Setting individual device volume");
            mDeviceVolumeCache.put(device, volume);
            mVolumeControlNativeInterface.setVolume(device, volume);
        }
    }

    /**
     * {@hide}
     */
    public void setGroupVolume(int groupId, int volume) {
        if (volume < 0) {
            Log.w(TAG, "Tried to set invalid volume " + volume + ". Ignored.");
            return;
        }

        mGroupVolumeCache.put(groupId, volume);
        mVolumeControlNativeInterface.setGroupVolume(groupId, volume);

        // We only receive the volume change and mute state needs to be acquired manually
        Boolean isGroupMute = mGroupMuteCache.getOrDefault(groupId, false);
        Boolean isStreamMute = mAudioManager.isStreamMute(getBluetoothContextualVolumeStream());

        /* Note: AudioService keeps volume levels for each stream and for each device type,
         * however it stores the mute state only for the stream type but not for each individual
         * device type. When active device changes, it's volume level gets aplied, but mute state
         * is not, but can be either derived from the volume level or just unmuted like for A2DP.
         * Also setting volume level > 0 to audio system will implicitly unmute the stream.
         * However LeAudio devices can keep their volume level high, while keeping it mute so we
         * have to explicitly unmute the remote device.
         */
        if (!isGroupMute.equals(isStreamMute)) {
            Log.w(TAG, "Mute state mismatch, stream mute: " + isStreamMute
                    + ", device group mute: " + isGroupMute
                    + ", new volume: " + volume);
            if (isStreamMute) {
                Log.i(TAG, "Mute the group " + groupId);
                muteGroup(groupId);
            }
            if (!isStreamMute && (volume > 0)) {
                Log.i(TAG, "Unmute the group " + groupId);
                unmuteGroup(groupId);
            }
        }
    }

    /**
     * {@hide}
     * @param groupId
     */
    public int getGroupVolume(int groupId) {
        return mGroupVolumeCache.getOrDefault(groupId,
                        IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME);
    }

    /**
     * Get device cached volume.
     *
     * @param device the device
     * @return the cached volume
     * @hide
     */
    public int getDeviceVolume(BluetoothDevice device) {
        return mDeviceVolumeCache.getOrDefault(
                device, IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME);
    }

    /**
     * This should be called by LeAudioService when LE Audio group change it
     * active state.
     *
     * @param groupId   the group identifier
     * @param active    indicator if group is active or not
     */
    public void setGroupActive(int groupId, boolean active) {
        if (DBG) {
            Log.d(TAG, "setGroupActive: " + groupId + ", active: " + active);
        }
        if (!active) {
            /* For now we don't need to handle group inactivation */
            return;
        }

        int groupVolume = getGroupVolume(groupId);
        Boolean groupMute = getGroupMute(groupId);

        if (groupVolume != IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME) {
            /* Don't need to show volume when activating known device. */
            updateGroupCacheAndAudioSystem(groupId, groupVolume, groupMute, /* showInUI*/ false);
        }
    }

    /**
     * @param groupId the group identifier
     */
    public Boolean getGroupMute(int groupId) {
        return mGroupMuteCache.getOrDefault(groupId, false);
    }

    /**
     * {@hide}
     */
    public void mute(BluetoothDevice device) {
        mVolumeControlNativeInterface.mute(device);
    }

    /**
     * {@hide}
     */
    public void muteGroup(int groupId) {
        mGroupMuteCache.put(groupId, true);
        mVolumeControlNativeInterface.muteGroup(groupId);
    }

    /**
     * {@hide}
     */
    public void unmute(BluetoothDevice device) {
        mVolumeControlNativeInterface.unmute(device);
    }

    /**
     * {@hide}
     */
    public void unmuteGroup(int groupId) {
        mGroupMuteCache.put(groupId, false);
        mVolumeControlNativeInterface.unmuteGroup(groupId);
    }

    void notifyNewCallbackOfKnownVolumeInfo(IBluetoothVolumeControlCallback callback) {
        if (DBG) {
            Log.d(TAG, "notifyNewCallbackOfKnownVolumeInfo");
        }

        RemoteCallbackList<IBluetoothVolumeControlCallback> tempCallbackList =
                new RemoteCallbackList<>();
        if (tempCallbackList == null) {
            Log.w(TAG, "notifyNewCallbackOfKnownVolumeInfo: tempCallbackList not available");
            return;
        }

        /* Register callback on temporary list just to execute it now. */
        tempCallbackList.register(callback);

        int n = tempCallbackList.beginBroadcast();
        if (n != 1) {
            /* There should be only one calback in this place. */
            Log.e(TAG, "notifyNewCallbackOfKnownVolumeInfo: Shall be 1 but it is " + n);
        }

        for (int i = 0; i < n; i++) {
            // notify volume offset
            for (Map.Entry<BluetoothDevice, VolumeControlOffsetDescriptor> entry :
                    mAudioOffsets.entrySet()) {
                VolumeControlOffsetDescriptor descriptor = entry.getValue();

                for (int id = 1; id <= descriptor.size(); id++) {
                    BluetoothDevice device = entry.getKey();
                    int offset = descriptor.getValue(id);
                    int location = descriptor.getLocation(id);
                    String description = descriptor.getDescription(id);

                    if (DBG) {
                        Log.d(
                                TAG,
                                "notifyNewCallbackOfKnownVolumeInfo, device: "
                                        + device
                                        + ", id: "
                                        + id
                                        + ", offset: "
                                        + offset
                                        + ", location: "
                                        + location
                                        + ", description: "
                                        + description);
                    }

                    try {
                        tempCallbackList
                                .getBroadcastItem(i)
                                .onVolumeOffsetChanged(device, id, offset);
                    } catch (RemoteException e) {
                        // Not every callback had to be defined; just continue
                    }
                    if (mFeatureFlags.leaudioMultipleVocsInstancesApi()) {
                        try {
                            tempCallbackList
                                    .getBroadcastItem(i)
                                    .onVolumeOffsetAudioLocationChanged(device, id, location);
                        } catch (RemoteException e) {
                            // Not every callback had to be defined; just continue
                        }
                        try {
                            tempCallbackList
                                    .getBroadcastItem(i)
                                    .onVolumeOffsetAudioDescriptionChanged(device, id, description);
                        } catch (RemoteException e) {
                            // Not every callback had to be defined; just continue
                        }
                    }
                }
            }
        }

        tempCallbackList.finishBroadcast();

        if (mFeatureFlags.leaudioBroadcastVolumeControlForConnectedDevices()) {
            notifyDevicesVolumeChanged(tempCallbackList, getDevices(), Optional.empty());
        }

        /* User is notified, remove callback from temporary list */
        tempCallbackList.unregister(callback);
    }

    void registerCallback(IBluetoothVolumeControlCallback callback) {
        if (DBG) {
            Log.d(TAG, "registerCallback: " + callback);
        }
        /* Here we keep all the user callbacks */
        mCallbacks.register(callback);

        notifyNewCallbackOfKnownVolumeInfo(callback);
    }

    void notifyNewRegisteredCallback(IBluetoothVolumeControlCallback callback) {
        if (DBG) {
            Log.d(TAG, "notifyNewRegisteredCallback: " + callback);
        }
        notifyNewCallbackOfKnownVolumeInfo(callback);
    }

    /**
     * {@hide}
     */
    public void handleGroupNodeAdded(int groupId, BluetoothDevice device) {
        // Ignore disconnected device, its volume will be set once it connects
        synchronized (mStateMachines) {
            VolumeControlStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                return;
            }
            if (sm.getConnectionState() != BluetoothProfile.STATE_CONNECTED) {
                return;
            }
        }

        // Correct the volume level only if device was already reported as connected.
        boolean can_change_volume = false;
        synchronized (mStateMachines) {
            VolumeControlStateMachine sm = mStateMachines.get(device);
            if (sm != null) {
                can_change_volume =
                        (sm.getConnectionState() == BluetoothProfile.STATE_CONNECTED);
            }
        }

        // If group volume has already changed, the new group member should set it
        if (can_change_volume) {
            Integer groupVolume = mGroupVolumeCache.getOrDefault(groupId,
                    IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME);
            if (groupVolume != IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME) {
                Log.i(TAG, "Setting value:" + groupVolume + " to " + device);
                mVolumeControlNativeInterface.setVolume(device, groupVolume);
            }

            Boolean isGroupMuted = mGroupMuteCache.getOrDefault(groupId, false);
            Log.i(TAG, "Setting mute:" + isGroupMuted + " to " + device);
            if (isGroupMuted) {
                mVolumeControlNativeInterface.mute(device);
            } else {
                mVolumeControlNativeInterface.unmute(device);
            }
        }
    }

    void updateGroupCacheAndAudioSystem(int groupId, int volume, boolean mute, boolean showInUI) {
        Log.d(
                TAG,
                " updateGroupCacheAndAudioSystem: groupId: "
                        + groupId
                        + ", vol: "
                        + volume
                        + ", mute: "
                        + mute
                        + ", showInUI"
                        + showInUI);

        mGroupVolumeCache.put(groupId, volume);
        mGroupMuteCache.put(groupId, mute);

        if (mFeatureFlags.leaudioBroadcastVolumeControlForConnectedDevices()) {
            LeAudioService leAudioService = mFactory.getLeAudioService();
            if (leAudioService != null) {
                int currentlyActiveGroupId = leAudioService.getActiveGroupId();
                if (currentlyActiveGroupId == IBluetoothLeAudio.LE_AUDIO_GROUP_ID_INVALID
                        || groupId != currentlyActiveGroupId) {
                    Log.i(
                            TAG,
                            "Skip updating to audio system if not updating volume for current"
                                    + " active group");
                    return;
                }
            } else {
                Log.w(TAG, "leAudioService not available");
            }
        }

        int streamType = getBluetoothContextualVolumeStream();
        int flags = AudioManager.FLAG_BLUETOOTH_ABS_VOLUME;
        if (showInUI) {
            flags |= AudioManager.FLAG_SHOW_UI;
        }

        mAudioManager.setStreamVolume(streamType, getAudioDeviceVolume(streamType, volume), flags);

        if (mAudioManager.isStreamMute(streamType) != mute) {
            int adjustment = mute ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE;
            mAudioManager.adjustStreamVolume(streamType, adjustment, flags);
        }
    }

    void handleVolumeControlChanged(BluetoothDevice device, int groupId,
                                    int volume, boolean mute, boolean isAutonomous) {

        if (isAutonomous && device != null) {
            Log.e(TAG, "We expect only group notification for autonomous updates");
            return;
        }

        if (groupId == IBluetoothLeAudio.LE_AUDIO_GROUP_ID_INVALID) {
            LeAudioService leAudioService = mFactory.getLeAudioService();
            if (leAudioService == null) {
                Log.e(TAG, "leAudioService not available");
                return;
            }
            groupId = leAudioService.getGroupId(device);
        }

        if (groupId == IBluetoothLeAudio.LE_AUDIO_GROUP_ID_INVALID) {
            Log.e(TAG, "Device not a part of the group");
            return;
        }

        int groupVolume = getGroupVolume(groupId);
        Boolean groupMute = getGroupMute(groupId);

        if (mFeatureFlags.leaudioBroadcastVolumeControlForConnectedDevices()) {
            Log.i(TAG, "handleVolumeControlChanged: " + device + "; volume: " + volume);
            if (device == null) {
                // notify group devices volume changed
                LeAudioService leAudioService = mFactory.getLeAudioService();
                if (leAudioService != null) {
                    notifyDevicesVolumeChanged(
                            mCallbacks,
                            leAudioService.getGroupDevices(groupId),
                            Optional.of(volume));
                } else {
                    Log.w(TAG, "leAudioService not available");
                }
            } else {
                // notify device volume changed
                notifyDevicesVolumeChanged(mCallbacks, Arrays.asList(device), Optional.of(volume));
            }
        }

        if (groupVolume == IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME) {
            /* We are here, because system was just started and LeAudio device just connected.
             * In such case, we take Volume stored on remote device and apply it to our cache and
             * audio system.
             * Note, to match BR/EDR behavior, don't show volume change in UI here
             */
            updateGroupCacheAndAudioSystem(groupId, volume, mute, false);
            return;
        }

        if (!isAutonomous) {
            /* If the change is triggered by Android device, the stream is already changed.
             * However it might be called with isAutonomous, one the first read of after
             * reconnection. Make sure device has group volume. Also it might happen that
             * remote side send us wrong value - lets check it.
             */

            if ((groupVolume == volume) && (groupMute == mute)) {
                Log.i(TAG, " Volume:" + volume + ", mute:" + mute + " confirmed by remote side.");
                return;
            }

            if (device != null) {
                // Correct the volume level only if device was already reported as connected.
                boolean can_change_volume = false;
                synchronized (mStateMachines) {
                    VolumeControlStateMachine sm = mStateMachines.get(device);
                    if (sm != null) {
                        can_change_volume =
                                (sm.getConnectionState() == BluetoothProfile.STATE_CONNECTED);
                    }
                }

                if (can_change_volume && (groupVolume != volume)) {
                    Log.i(TAG, "Setting value:" + groupVolume + " to " + device);
                    mVolumeControlNativeInterface.setVolume(device, groupVolume);
                }
                if (can_change_volume && (groupMute != mute)) {
                    Log.i(TAG, "Setting mute:" + groupMute + " to " + device);
                    if (groupMute) {
                        mVolumeControlNativeInterface.mute(device);
                    } else {
                        mVolumeControlNativeInterface.unmute(device);
                    }
                }
            } else {
                Log.e(TAG, "Volume changed did not succeed. Volume: " + volume
                                + " expected volume: " + groupVolume);
            }
        } else {
            /* Received group notification for autonomous change. Update cache and audio system. */
            updateGroupCacheAndAudioSystem(groupId, volume, mute, true);
        }
    }

    /**
     * {@hide}
     */
    public int getAudioDeviceGroupVolume(int groupId) {
        int volume = getGroupVolume(groupId);
        if (getGroupMute(groupId)) {
            Log.w(TAG, "Volume level is " + volume
                    + ", but muted. Will report 0 for the audio device.");
            volume = 0;
        }

        if (volume == IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME) return -1;
        return getAudioDeviceVolume(getBluetoothContextualVolumeStream(), volume);
    }

    int getAudioDeviceVolume(int streamType, int bleVolume) {
        int deviceMaxVolume = mAudioManager.getStreamMaxVolume(streamType);

        // TODO: Investigate what happens in classic BT when BT volume is changed to zero.
        double deviceVolume = (double) (bleVolume * deviceMaxVolume) / LE_AUDIO_MAX_VOL;
        return (int) Math.round(deviceVolume);
    }

    // Copied from AudioService.getBluetoothContextualVolumeStream() and modified it.
    int getBluetoothContextualVolumeStream() {
        int mode = mAudioManager.getMode();

        if (DBG) {
            Log.d(TAG, "Volume mode: " + mode + "0: normal, 1: ring, 2,3: call");
        }

        switch (mode) {
            case AudioManager.MODE_IN_COMMUNICATION:
            case AudioManager.MODE_IN_CALL:
                return AudioManager.STREAM_VOICE_CALL;
            case AudioManager.MODE_RINGTONE:
            if (mFeatureFlags.leaudioVolumeChangeOnRingtoneFix()) {
                if (DBG) {
                    Log.d(TAG, " Update during ringtone applied to voice call");
                }
                return AudioManager.STREAM_VOICE_CALL;
            }
            // fall through
            case AudioManager.MODE_NORMAL:
            default:
                // other conditions will influence the stream type choice, read on...
                break;
        }
        return AudioManager.STREAM_MUSIC;
    }

    void handleDeviceAvailable(BluetoothDevice device, int numberOfExternalOutputs) {
        if (numberOfExternalOutputs == 0) {
            Log.i(TAG, "Volume offset not available");
            return;
        }

        VolumeControlOffsetDescriptor offsets = mAudioOffsets.get(device);
        if (offsets == null) {
            offsets = new VolumeControlOffsetDescriptor();
            mAudioOffsets.put(device, offsets);
        } else if (offsets.size() != numberOfExternalOutputs) {
            Log.i(TAG, "Number of offset changed: ");
            offsets.clear();
        }

        /* Stack delivers us number of audio outputs.
         * Offset ids a countinous from 1 to number_of_ext_outputs*/
        for (int i = 1; i <= numberOfExternalOutputs; i++) {
            offsets.add(i);
            mVolumeControlNativeInterface.getExtAudioOutVolumeOffset(device, i);
            mVolumeControlNativeInterface.getExtAudioOutLocation(device, i);
            mVolumeControlNativeInterface.getExtAudioOutDescription(device, i);
        }
    }

    void handleDeviceExtAudioOffsetChanged(BluetoothDevice device, int id, int value) {
        if (DBG) {
            Log.d(TAG, " device: " + device + " offset_id: " +  id + " value: " + value);
        }
        VolumeControlOffsetDescriptor offsets = mAudioOffsets.get(device);
        if (offsets == null) {
            Log.e(TAG, " Offsets not found for device: " + device);
            return;
        }
        offsets.setValue(id, value);

        if (mCallbacks == null) {
            return;
        }

        int n = mCallbacks.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                mCallbacks.getBroadcastItem(i).onVolumeOffsetChanged(device, id, value);
            } catch (RemoteException e) {
                continue;
            }
        }
        mCallbacks.finishBroadcast();
    }

    void handleDeviceExtAudioLocationChanged(BluetoothDevice device, int id, int location) {
        if (DBG) {
            Log.d(TAG, " device: " + device + " offset_id: "
                    + id + " location: " + location);
        }

        VolumeControlOffsetDescriptor offsets = mAudioOffsets.get(device);
        if (offsets == null) {
            Log.e(TAG, " Offsets not found for device: " + device);
            return;
        }
        offsets.setLocation(id, location);

        if (mFeatureFlags.leaudioMultipleVocsInstancesApi()) {
            if (mCallbacks == null) {
                return;
            }

            int n = mCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mCallbacks
                            .getBroadcastItem(i)
                            .onVolumeOffsetAudioLocationChanged(device, id, location);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

    void handleDeviceExtAudioDescriptionChanged(
            BluetoothDevice device, int id, String description) {
        if (DBG) {
            Log.d(TAG, " device: " + device + " offset_id: "
                    + id + " description: " + description);
        }

        VolumeControlOffsetDescriptor offsets = mAudioOffsets.get(device);
        if (offsets == null) {
            Log.e(TAG, " Offsets not found for device: " + device);
            return;
        }
        offsets.setDescription(id, description);

        if (mFeatureFlags.leaudioMultipleVocsInstancesApi()) {
            if (mCallbacks == null) {
                return;
            }

            int n = mCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mCallbacks
                            .getBroadcastItem(i)
                            .onVolumeOffsetAudioDescriptionChanged(device, id, description);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

    void messageFromNative(VolumeControlStackEvent stackEvent) {
        if (DBG) {
            Log.d(TAG, "messageFromNative: " + stackEvent);
        }

        if (stackEvent.type == VolumeControlStackEvent.EVENT_TYPE_VOLUME_STATE_CHANGED) {
            handleVolumeControlChanged(stackEvent.device, stackEvent.valueInt1,
                                       stackEvent.valueInt2, stackEvent.valueBool1,
                                       stackEvent.valueBool2);
          return;
        }

        Objects.requireNonNull(stackEvent.device,
                "Device should never be null, event: " + stackEvent);

        Intent intent = null;

        if (intent != null) {
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                    | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            sendBroadcast(intent, BLUETOOTH_CONNECT);
            return;
        }

        BluetoothDevice device = stackEvent.device;
        if (stackEvent.type == VolumeControlStackEvent.EVENT_TYPE_DEVICE_AVAILABLE) {
            handleDeviceAvailable(device, stackEvent.valueInt1);
            return;
        }

        if (stackEvent.type
                == VolumeControlStackEvent.EVENT_TYPE_EXT_AUDIO_OUT_VOL_OFFSET_CHANGED) {
            handleDeviceExtAudioOffsetChanged(device, stackEvent.valueInt1, stackEvent.valueInt2);
            return;
        }

        if (stackEvent.type
                == VolumeControlStackEvent.EVENT_TYPE_EXT_AUDIO_OUT_LOCATION_CHANGED) {
            handleDeviceExtAudioLocationChanged(device, stackEvent.valueInt1,
                                                    stackEvent.valueInt2);
            return;
        }

        if (stackEvent.type
                == VolumeControlStackEvent.EVENT_TYPE_EXT_AUDIO_OUT_DESCRIPTION_CHANGED) {
            handleDeviceExtAudioDescriptionChanged(device, stackEvent.valueInt1,
                                                    stackEvent.valueString1);
            return;
        }

        synchronized (mStateMachines) {
            VolumeControlStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                if (stackEvent.type
                        == VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED) {
                    switch (stackEvent.valueInt1) {
                        case VolumeControlStackEvent.CONNECTION_STATE_CONNECTED:
                        case VolumeControlStackEvent.CONNECTION_STATE_CONNECTING:
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
            sm.sendMessage(VolumeControlStateMachine.STACK_EVENT, stackEvent);
        }
    }

    private VolumeControlStateMachine getOrCreateStateMachine(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "getOrCreateStateMachine failed: device cannot be null");
            return null;
        }
        synchronized (mStateMachines) {
            VolumeControlStateMachine sm = mStateMachines.get(device);
            if (sm != null) {
                return sm;
            }
            // Limit the maximum number of state machines to avoid DoS attack
            if (mStateMachines.size() >= MAX_VC_STATE_MACHINES) {
                Log.e(TAG, "Maximum number of VolumeControl state machines reached: "
                        + MAX_VC_STATE_MACHINES);
                return null;
            }
            if (DBG) {
                Log.d(TAG, "Creating a new state machine for " + device);
            }
            sm = VolumeControlStateMachine.make(device, this,
                    mVolumeControlNativeInterface, mStateMachinesThread.getLooper());
            mStateMachines.put(device, sm);
            return sm;
        }
    }

    /**
     * Notify devices with volume level
     *
     * <p>In case of handleVolumeControlChanged, volume level is known from native layer caller.
     * Notify the clients with the volume level directly and update the volume cache. In case of
     * newly registered callback, volume level is unknown from caller, notify the clients with
     * cached volume level from either device or group.
     *
     * @param callbacks list of callbacks
     * @param devices list of devices to notify volume changed
     * @param volume volume level
     */
    private void notifyDevicesVolumeChanged(
            RemoteCallbackList<IBluetoothVolumeControlCallback> callbacks,
            List<BluetoothDevice> devices,
            Optional<Integer> volume) {
        if (callbacks == null) {
            Log.e(TAG, "callbacks is null");
            return;
        }

        LeAudioService leAudioService = mFactory.getLeAudioService();
        if (leAudioService == null) {
            Log.e(TAG, "leAudioService not available");
            return;
        }

        for (BluetoothDevice dev : devices) {
            int cachedVolume = IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME;
            if (!volume.isPresent()) {
                int groupId = leAudioService.getGroupId(dev);
                if (groupId == IBluetoothLeAudio.LE_AUDIO_GROUP_ID_INVALID) {
                    Log.e(TAG, "Device not a part of a group");
                    continue;
                }
                // if device volume is available, notify with device volume, otherwise group volume
                cachedVolume = getDeviceVolume(dev);
                if (cachedVolume == IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME) {
                    cachedVolume = getGroupVolume(groupId);
                }
            }
            int broadcastVolume = cachedVolume;
            if (volume.isPresent()) {
                broadcastVolume = volume.get();
                mDeviceVolumeCache.put(dev, broadcastVolume);
            }
            int n = callbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    callbacks.getBroadcastItem(i).onDeviceVolumeChanged(dev, broadcastVolume);
                } catch (RemoteException e) {
                    continue;
                }
            }
            callbacks.finishBroadcast();
        }
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

        synchronized (mStateMachines) {
            VolumeControlStateMachine sm = mStateMachines.get(device);
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
            VolumeControlStateMachine sm = mStateMachines.get(device);
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

    void handleConnectionStateChanged(BluetoothDevice device, int fromState, int toState) {
        mHandler.post(() -> connectionStateChanged(device, fromState, toState));
    }

    @VisibleForTesting
    synchronized void connectionStateChanged(BluetoothDevice device, int fromState,
                                             int toState) {
        if (!isAvailable()) {
            Log.w(TAG, "connectionStateChanged: service is not available");
            return;
        }

        if ((device == null) || (fromState == toState)) {
            Log.e(TAG, "connectionStateChanged: unexpected invocation. device=" + device
                    + " fromState=" + fromState + " toState=" + toState);
            return;
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
        } else if (toState == BluetoothProfile.STATE_CONNECTED) {
            // Restore the group volume if it was changed while the device was not yet connected.
            CsipSetCoordinatorService csipClient = mFactory.getCsipSetCoordinatorService();
            if (csipClient != null) {
                Integer groupId = csipClient.getGroupId(device, BluetoothUuid.CAP);
                if (groupId != IBluetoothCsipSetCoordinator.CSIS_GROUP_ID_INVALID) {
                    Integer groupVolume =
                            mGroupVolumeCache.getOrDefault(
                                    groupId, IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME);
                    if (groupVolume != IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME) {
                        mVolumeControlNativeInterface.setVolume(device, groupVolume);
                    }

                    Boolean groupMute = mGroupMuteCache.getOrDefault(groupId, false);
                    if (groupMute) {
                        mVolumeControlNativeInterface.mute(device);
                    } else {
                        mVolumeControlNativeInterface.unmute(device);
                    }
                }
            } else {
                /* It could happen when Bluetooth is stopping while VC is getting
                 * connection event
                 */
                Log.w(TAG, "CSIP is not available");
            }
        }
        mAdapterService.handleProfileConnectionStateChange(
                BluetoothProfile.VOLUME_CONTROL, device, fromState, toState);
    }

    /**
     * Binder object: must be a static class or memory leak may occur
     */
    @VisibleForTesting
    static class BluetoothVolumeControlBinder extends IBluetoothVolumeControl.Stub
            implements IProfileServiceBinder {
        @VisibleForTesting
        boolean mIsTesting = false;
        private VolumeControlService mService;

        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        private VolumeControlService getService(AttributionSource source) {
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

        BluetoothVolumeControlBinder(VolumeControlService svc) {
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
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.connect(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void disconnect(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.disconnect(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectedDevices(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                List<BluetoothDevice> defaultValue = new ArrayList<>();
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    defaultValue = service.getConnectedDevices();
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getDevicesMatchingConnectionStates(int[] states,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                List<BluetoothDevice> defaultValue = new ArrayList<>();
                if (service != null) {
                    defaultValue = service.getDevicesMatchingConnectionStates(states);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectionState(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                int defaultValue = BluetoothProfile.STATE_DISCONNECTED;
                if (service != null) {
                    defaultValue = service.getConnectionState(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setConnectionPolicy(BluetoothDevice device, int connectionPolicy,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    defaultValue = service.setConnectionPolicy(device, connectionPolicy);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectionPolicy(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                int defaultValue = BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    defaultValue = service.getConnectionPolicy(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void isVolumeOffsetAvailable(
                BluetoothDevice device,
                AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                boolean defaultValue = false;
                VolumeControlService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    defaultValue = service.isVolumeOffsetAvailable(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getNumberOfVolumeOffsetInstances(
                BluetoothDevice device,
                AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                int defaultValue = 0;
                VolumeControlService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    defaultValue = service.getNumberOfVolumeOffsetInstances(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setVolumeOffset(
                BluetoothDevice device,
                int instanceId,
                int volumeOffset,
                AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    service.setVolumeOffset(device, instanceId, volumeOffset);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setDeviceVolume(
                BluetoothDevice device,
                int volume,
                boolean isGroupOp,
                AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    service.setDeviceVolume(device, volume, isGroupOp);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setGroupVolume(int groupId, int volume, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service != null) {
                    service.setGroupVolume(groupId, volume);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getGroupVolume(int groupId, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                int groupVolume = 0;
                VolumeControlService service = getService(source);
                if (service != null) {
                    groupVolume = service.getGroupVolume(groupId);
                }
                receiver.send(groupVolume);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setGroupActive(
                int groupId,
                boolean active,
                AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                receiver.send(null);
                if (service != null) {
                    service.setGroupActive(groupId, active);
                }
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void mute(BluetoothDevice device,  AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service != null) {
                    service.mute(device);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void muteGroup(int groupId, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service != null) {
                    service.muteGroup(groupId);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void unmute(BluetoothDevice device,  AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service != null) {
                    service.unmute(device);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void unmuteGroup(int groupId,  AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service != null) {
                    service.unmuteGroup(groupId);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void registerCallback(IBluetoothVolumeControlCallback callback,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(callback, "callback cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service == null) {
                    throw new IllegalStateException("Service is unavailable");
                }

                enforceBluetoothPrivilegedPermission(service);
                service.mHandler.post(
                        () -> {
                            try {
                                service.registerCallback(callback);
                                receiver.send(null);
                            } catch (RuntimeException e) {
                                receiver.propagateException(e);
                            }
                        });
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void notifyNewRegisteredCallback(
                IBluetoothVolumeControlCallback callback,
                AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(callback, "callback cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service == null) {
                    throw new IllegalStateException("Service is unavailable");
                }

                enforceBluetoothPrivilegedPermission(service);
                service.mHandler.post(
                        () -> {
                            try {
                                service.notifyNewRegisteredCallback(callback);
                                receiver.send(null);
                            } catch (RuntimeException e) {
                                receiver.propagateException(e);
                            }
                        });
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void unregisterCallback(IBluetoothVolumeControlCallback callback,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(callback, "callback cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service == null) {
                    throw new IllegalStateException("Service is unavailable");
                }

                enforceBluetoothPrivilegedPermission(service);
                service.mHandler.post(
                        () -> {
                            try {
                                service.mCallbacks.unregister(callback);
                                receiver.send(null);
                            } catch (RuntimeException e) {
                                receiver.propagateException(e);
                            }
                        });
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        for (VolumeControlStateMachine sm : mStateMachines.values()) {
            sm.dump(sb);
        }

        for (Map.Entry<BluetoothDevice, VolumeControlOffsetDescriptor> entry :
                                                            mAudioOffsets.entrySet()) {
            VolumeControlOffsetDescriptor descriptor = entry.getValue();
            BluetoothDevice device = entry.getKey();
            ProfileService.println(sb, "    Device: " + device);
            ProfileService.println(sb, "    Volume offset cnt: " + descriptor.size());
            descriptor.dump(sb);
        }
        for (Map.Entry<Integer, Integer> entry : mGroupVolumeCache.entrySet()) {
            Boolean isMute = mGroupMuteCache.getOrDefault(entry.getKey(), false);
            ProfileService.println(sb, "    GroupId: " + entry.getKey() + " volume: "
                            + entry.getValue() + ", mute: " + isMute);
        }
    }
}
