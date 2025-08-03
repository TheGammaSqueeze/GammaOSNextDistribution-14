/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com.
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

package com.android.bluetooth.le_audio;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.bluetooth.IBluetoothLeAudio.LE_AUDIO_GROUP_ID_INVALID;

import static com.android.bluetooth.flags.Flags.leaudioBroadcastFeatureSupport;
import static com.android.bluetooth.Utils.enforceBluetoothPrivilegedPermission;
import static com.android.modules.utils.build.SdkLevel.isAtLeastU;

import android.annotation.RequiresPermission;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.bluetooth.BluetoothLeAudioCodecStatus;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastSettings;
import android.bluetooth.BluetoothLeBroadcastSubgroupSettings;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProtoEnums;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothLeAudio;
import android.bluetooth.IBluetoothLeAudioCallback;
import android.bluetooth.IBluetoothLeBroadcastCallback;
import android.bluetooth.IBluetoothVolumeControl;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.AttributionSource;
import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.BluetoothProfileConnectionInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.sysprop.BluetoothProperties;
import android.util.Log;
import android.util.Pair;

import com.android.bluetooth.Utils;
import com.android.bluetooth.bass_client.BassClientService;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.AudioRoutingManager;
import com.android.bluetooth.btservice.MetricsLogger;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ServiceFactory;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.csip.CsipSetCoordinatorService;
import com.android.bluetooth.flags.Flags;
import com.android.bluetooth.hap.HapClientService;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.mcp.McpService;
import com.android.bluetooth.tbs.TbsGatt;
import com.android.bluetooth.tbs.TbsService;
import com.android.bluetooth.vc.VolumeControlService;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.SynchronousResultReceiver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Provides Bluetooth LeAudio profile, as a service in the Bluetooth application.
 * @hide
 */
public class LeAudioService extends ProfileService {
    private static final boolean DBG = true;
    private static final String TAG = "LeAudioService";

    // Timeout for state machine thread join, to prevent potential ANR.
    private static final int SM_THREAD_JOIN_TIMEOUT_MS = 1000;

    private static LeAudioService sLeAudioService;

    /**
     * Indicates group audio support for none direction
     */
    private static final int AUDIO_DIRECTION_NONE = 0x00;

    /**
     * Indicates group audio support for output direction
     */
    private static final int AUDIO_DIRECTION_OUTPUT_BIT = 0x01;

    /**
     * Indicates group audio support for input direction
     */
    private static final int AUDIO_DIRECTION_INPUT_BIT = 0x02;

    /**
     * This is used by application read-only for checking the fallback active group id.
     *
     */
    public static final String BLUETOOTH_LE_BROADCAST_FALLBACK_ACTIVE_GROUP_ID =
            "bluetooth_le_broadcast_fallback_active_group_id";

    /**
     * Per PBP 1.0 4.3. High Quality Public Broadcast Audio, Broadcast HIGH quality audio configs
     * are with sampling frequency 48khz
     */
    private static final BluetoothLeAudioCodecConfig BROADCAST_HIGH_QUALITY_CONFIG =
            new BluetoothLeAudioCodecConfig.Builder()
                    .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                    .setSampleRate(BluetoothLeAudioCodecConfig.SAMPLE_RATE_48000)
                    .build();

    /* 5 seconds timeout for Broadcast streaming state transition */
    private static final int DIALING_OUT_TIMEOUT_MS = 5000;

    private AdapterService mAdapterService;
    private DatabaseManager mDatabaseManager;
    private HandlerThread mStateMachinesThread;
    private volatile BluetoothDevice mActiveAudioOutDevice;
    private volatile BluetoothDevice mActiveAudioInDevice;
    private volatile BluetoothDevice mActiveBroadcastAudioDevice;
    private BluetoothDevice mExposedActiveDevice;
    private LeAudioCodecConfig mLeAudioCodecConfig;
    private final ReentrantLock mGroupLock = new ReentrantLock();
    private final ReentrantReadWriteLock mGroupReadWriteLock = new ReentrantReadWriteLock();
    private final Lock mGroupReadLock = mGroupReadWriteLock.readLock();
    private final Lock mGroupWriteLock = mGroupReadWriteLock.writeLock();
    ServiceFactory mServiceFactory = new ServiceFactory();

    LeAudioNativeInterface mLeAudioNativeInterface;
    boolean mLeAudioNativeIsInitialized = false;
    boolean mLeAudioInbandRingtoneSupportedByPlatform = true;
    boolean mBluetoothEnabled = false;
    BluetoothDevice mHfpHandoverDevice = null;
    LeAudioBroadcasterNativeInterface mLeAudioBroadcasterNativeInterface = null;
    private DialingOutTimeoutEvent mDialingOutTimeoutEvent = null;
    @VisibleForTesting
    AudioManager mAudioManager;
    LeAudioTmapGattServer mTmapGattServer;
    int mTmapRoleMask;
    int mUnicastGroupIdDeactivatedForBroadcastTransition = LE_AUDIO_GROUP_ID_INVALID;
    Optional<Integer> mBroadcastIdDeactivatedForUnicastTransition = Optional.empty();
    Optional<Boolean> mQueuedInCallValue = Optional.empty();
    boolean mTmapStarted = false;
    private boolean mAwaitingBroadcastCreateResponse = false;
    private final LinkedList<BluetoothLeBroadcastSettings> mCreateBroadcastQueue =
            new LinkedList<>();
    boolean mIsSourceStreamMonitorModeEnabled = false;

    @VisibleForTesting
    TbsService mTbsService;

    @VisibleForTesting
    McpService mMcpService;

    @VisibleForTesting
    VolumeControlService mVolumeControlService;

    @VisibleForTesting
    HapClientService mHapClientService;

    @VisibleForTesting
    CsipSetCoordinatorService mCsipSetCoordinatorService;

    @VisibleForTesting BassClientService mBassClientService;

    @VisibleForTesting
    RemoteCallbackList<IBluetoothLeBroadcastCallback> mBroadcastCallbacks;

    @VisibleForTesting
    RemoteCallbackList<IBluetoothLeAudioCallback> mLeAudioCallbacks;

    BluetoothLeScanner mAudioServersScanner;
    /* When mScanCallback is not null, it means scan is started. */
    ScanCallback mScanCallback;

    public LeAudioService(Context ctx) {
        super(ctx);
    }

    @VisibleForTesting
    LeAudioService(Context ctx, LeAudioNativeInterface nativeInterface) {
        super(ctx);
        mLeAudioNativeInterface = nativeInterface;
    }

    private class LeAudioGroupDescriptor {
        LeAudioGroupDescriptor(boolean isInbandRingtonEnabled) {
            mIsConnected = false;
            mIsActive = false;
            mHasFallbackDeviceWhenGettingInactive = false;
            mDirection = AUDIO_DIRECTION_NONE;
            mCodecStatus = null;
            mLostLeadDeviceWhileStreaming = null;
            mCurrentLeadDevice = null;
            mInbandRingtoneEnabled = isInbandRingtonEnabled;
            mAvailableContexts = 0;
            mInputSelectableConfig = new ArrayList<>();
            mOutputSelectableConfig = new ArrayList<>();
            mInactivatedDueToContextType = false;
        }

        Boolean mIsConnected;
        Boolean mIsActive;
        Boolean mHasFallbackDeviceWhenGettingInactive;
        Integer mDirection;
        BluetoothLeAudioCodecStatus mCodecStatus;
        /* This can be non empty only for the streaming time */
        BluetoothDevice mLostLeadDeviceWhileStreaming;
        BluetoothDevice mCurrentLeadDevice;
        Boolean mInbandRingtoneEnabled;
        Integer mAvailableContexts;
        List<BluetoothLeAudioCodecConfig> mInputSelectableConfig;
        List<BluetoothLeAudioCodecConfig> mOutputSelectableConfig;
        Boolean mInactivatedDueToContextType;
    }

    private static class LeAudioDeviceDescriptor {
        LeAudioDeviceDescriptor(boolean isInbandRingtonEnabled) {
            mAclConnected = false;
            mStateMachine = null;
            mGroupId = LE_AUDIO_GROUP_ID_INVALID;
            mSinkAudioLocation = BluetoothLeAudio.AUDIO_LOCATION_INVALID;
            mDirection = AUDIO_DIRECTION_NONE;
            mDevInbandRingtoneEnabled = isInbandRingtonEnabled;
        }

        public boolean mAclConnected;
        public LeAudioStateMachine mStateMachine;
        public Integer mGroupId;
        public Integer mSinkAudioLocation;
        public Integer mDirection;
        Boolean mDevInbandRingtoneEnabled;
    }

    private static class LeAudioBroadcastDescriptor {
        LeAudioBroadcastDescriptor() {
            mState = LeAudioStackEvent.BROADCAST_STATE_STOPPED;
            mMetadata = null;
            mRequestedForDetails = false;
        }

        public Integer mState;
        public BluetoothLeBroadcastMetadata mMetadata;
        public Boolean mRequestedForDetails;
    }

    List<BluetoothLeAudioCodecConfig> mInputLocalCodecCapabilities = new ArrayList<>();
    List<BluetoothLeAudioCodecConfig> mOutputLocalCodecCapabilities = new ArrayList<>();

    private final Map<Integer, LeAudioGroupDescriptor> mGroupDescriptors = new LinkedHashMap<>();
    private final Map<BluetoothDevice, LeAudioDeviceDescriptor> mDeviceDescriptors =
            new LinkedHashMap<>();
    private final Map<Integer, LeAudioBroadcastDescriptor> mBroadcastDescriptors =
            new LinkedHashMap<>();

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private final AudioManagerAudioDeviceCallback mAudioManagerAudioDeviceCallback =
            new AudioManagerAudioDeviceCallback();

    @Override
    protected IProfileServiceBinder initBinder() {
        return new BluetoothLeAudioBinder(this);
    }

    public static boolean isEnabled() {
        return BluetoothProperties.isProfileBapUnicastClientEnabled().orElse(false);
    }

    public static boolean isBroadcastEnabled() {
        return leaudioBroadcastFeatureSupport()
                && BluetoothProperties.isProfileBapBroadcastSourceEnabled().orElse(false);
    }

    private boolean registerTmap() {
        if (mTmapGattServer != null) {
            throw new IllegalStateException("TMAP GATT server started before start() is called");
        }
        mTmapGattServer = LeAudioObjectsFactory.getInstance().getTmapGattServer(this);

        try {
            mTmapGattServer.start(mTmapRoleMask);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Fail to start TmapGattServer", e);
            mTmapGattServer = null;
            return false;
        }

        return true;
    }

    @Override
    public void start() {
        Log.i(TAG, "start()");
        if (sLeAudioService != null) {
            throw new IllegalStateException("start() called twice");
        }

        mAdapterService = Objects.requireNonNull(AdapterService.getAdapterService(),
                "AdapterService cannot be null when LeAudioService starts");
        if (mLeAudioNativeInterface == null) {
            mLeAudioNativeInterface =
                    Objects.requireNonNull(
                            LeAudioNativeInterface.getInstance(),
                            "LeAudioNativeInterface cannot be null when LeAudioService starts");
        }
        mDatabaseManager = Objects.requireNonNull(mAdapterService.getDatabase(),
                "DatabaseManager cannot be null when LeAudioService starts");

        mAudioManager = getSystemService(AudioManager.class);
        Objects.requireNonNull(mAudioManager,
                "AudioManager cannot be null when LeAudioService starts");

        // Start handler thread for state machines
        mStateMachinesThread = new HandlerThread("LeAudioService.StateMachines");
        mStateMachinesThread.start();

        mBroadcastDescriptors.clear();

        groupMutexLock(/* isReadOnly */ false);
        try {
            mDeviceDescriptors.clear();
            mGroupDescriptors.clear();
        } finally {
            groupMutexUnlock(/* isReadOnly */ false);
        }

        // Setup broadcast callbacks
        mLeAudioCallbacks = new RemoteCallbackList<IBluetoothLeAudioCallback>();

        mTmapRoleMask =
                LeAudioTmapGattServer.TMAP_ROLE_FLAG_CG | LeAudioTmapGattServer.TMAP_ROLE_FLAG_UMS;

        // Initialize Broadcast native interface
        if ((mAdapterService.getSupportedProfilesBitMask()
                    & (1 << BluetoothProfile.LE_AUDIO_BROADCAST)) != 0) {
            Log.i(TAG, "Init Le Audio broadcaster");
            mBroadcastCallbacks = new RemoteCallbackList<IBluetoothLeBroadcastCallback>();
            mLeAudioBroadcasterNativeInterface = Objects.requireNonNull(
                    LeAudioBroadcasterNativeInterface.getInstance(),
                    "LeAudioBroadcasterNativeInterface cannot be null when LeAudioService starts");
            mLeAudioBroadcasterNativeInterface.init();
            mTmapRoleMask |= LeAudioTmapGattServer.TMAP_ROLE_FLAG_BMS;
        } else {
            Log.w(TAG, "Le Audio Broadcasts not supported.");
        }

        mTmapStarted = registerTmap();

        mLeAudioInbandRingtoneSupportedByPlatform =
                        BluetoothProperties.isLeAudioInbandRingtoneSupported().orElse(true);

        mAudioManager.registerAudioDeviceCallback(mAudioManagerAudioDeviceCallback,
                       mHandler);

        // Mark service as started
        setLeAudioService(this);

        // Setup codec config
        mLeAudioCodecConfig = new LeAudioCodecConfig(this);

        // Delay the call to init by posting it. This ensures TBS and MCS are fully initialized
        // before we start accepting connections
        mHandler.post(this::init);
    }

    private void init() {
        if (!isAvailable()) {
            Log.e(TAG, " Service disabled before init");
            return;
        }

        if (!mTmapStarted) {
            mTmapStarted = registerTmap();
        }

        LeAudioNativeInterface nativeInterface = mLeAudioNativeInterface;
        if (nativeInterface == null) {
            Log.w(TAG, "the service is stopped. ignore init()");
            return;
        }
        nativeInterface.init(mLeAudioCodecConfig.getCodecConfigOffloading());
    }

    @Override
    public void stop() {
        Log.i(TAG, "stop()");
        if (sLeAudioService == null) {
            Log.w(TAG, "stop() called before start()");
            return;
        }

        mQueuedInCallValue = Optional.empty();
        mCreateBroadcastQueue.clear();
        mAwaitingBroadcastCreateResponse = false;
        mIsSourceStreamMonitorModeEnabled = false;

        clearBroadcastTimeoutCallback();

        mHandler.removeCallbacks(this::init);
        removeActiveDevice(false);

        if (mTmapGattServer == null) {
            Log.w(TAG, "TMAP GATT server should never be null before stop() is called");
        } else {
            mTmapGattServer.stop();
            mTmapGattServer = null;
            mTmapStarted = false;
        }

        stopAudioServersBackgroundScan();
        mAudioServersScanner = null;

        // Don't wait for async call with INACTIVE group status, clean active
        // device for active group.
        groupMutexLock(/* isReadOnly */ true);
        try {
            try {
                for (Map.Entry<Integer, LeAudioGroupDescriptor> entry :
                        mGroupDescriptors.entrySet()) {
                    LeAudioGroupDescriptor descriptor = entry.getValue();
                    Integer group_id = entry.getKey();
                    if (descriptor.mIsActive) {
                        descriptor.mIsActive = false;
                        updateActiveDevices(
                                group_id,
                                descriptor.mDirection,
                                AUDIO_DIRECTION_NONE,
                                descriptor.mIsActive,
                                false,
                                false);
                        break;
                    }
                }

                // Destroy state machines and stop handler thread
                for (LeAudioDeviceDescriptor descriptor : mDeviceDescriptors.values()) {
                    LeAudioStateMachine sm = descriptor.mStateMachine;
                    if (sm == null) {
                        continue;
                    }
                    sm.quit();
                    sm.cleanup();
                }
            } finally {
                if (Flags.leaudioApiSynchronizedBlockFix()) {
                    // Upgrade to write lock
                    groupMutexUnlock(/* isReadOnly */ true);
                    groupMutexLock(/* isReadOnly */ false);
                }
            }
            mDeviceDescriptors.clear();
            mGroupDescriptors.clear();
        } finally {
            groupMutexUnlock(/* isReadOnly */ false);
        }

        // Cleanup native interfaces
        mLeAudioNativeInterface.cleanup();
        mLeAudioNativeInterface = null;
        mLeAudioNativeIsInitialized = false;
        mBluetoothEnabled = false;
        mHfpHandoverDevice = null;

        mActiveAudioOutDevice = null;
        mActiveAudioInDevice = null;
        mExposedActiveDevice = null;
        mLeAudioCodecConfig = null;

        // Set the service and BLE devices as inactive
        setLeAudioService(null);

        // Unregister broadcast callbacks
        if (mBroadcastCallbacks != null) {
            mBroadcastCallbacks.kill();
        }

        if (mLeAudioCallbacks != null) {
            mLeAudioCallbacks.kill();
        }

        mBroadcastDescriptors.clear();

        if (mLeAudioBroadcasterNativeInterface != null) {
            mLeAudioBroadcasterNativeInterface.cleanup();
            mLeAudioBroadcasterNativeInterface = null;
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

        mAudioManager.unregisterAudioDeviceCallback(mAudioManagerAudioDeviceCallback);

        mAdapterService = null;
        mAudioManager = null;
        mMcpService = null;
        mTbsService = null;
        mVolumeControlService = null;
        mCsipSetCoordinatorService = null;
        mBassClientService = null;
    }

    @Override
    public void cleanup() {
        Log.i(TAG, "cleanup()");
    }

    public static synchronized LeAudioService getLeAudioService() {
        if (sLeAudioService == null) {
            Log.w(TAG, "getLeAudioService(): service is NULL");
            return null;
        }
        if (!sLeAudioService.isAvailable()) {
            Log.w(TAG, "getLeAudioService(): service is not available");
            return null;
        }
        return sLeAudioService;
    }

    @VisibleForTesting
    static synchronized void setLeAudioService(LeAudioService instance) {
        if (DBG) {
            Log.d(TAG, "setLeAudioService(): set to: " + instance);
        }
        sLeAudioService = instance;
    }

    VolumeControlService getVolumeControlService() {
        if (mVolumeControlService == null) {
            mVolumeControlService = mServiceFactory.getVolumeControlService();
            if (mVolumeControlService == null) {
                Log.e(TAG, "Volume control service is not available");
            }
        }
        return mVolumeControlService;
    }

    BassClientService getBassClientService() {
        if (mBassClientService == null) {
            mBassClientService = mServiceFactory.getBassClientService();
            if (mBassClientService == null) {
                Log.e(TAG, "BASS service is not available");
            }
        }
        return mBassClientService;
    }

    @VisibleForTesting
    int getAudioDeviceGroupVolume(int groupId) {
        VolumeControlService volumeControlService = getVolumeControlService();
        if (volumeControlService == null) {
            return IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME;
        }
        return volumeControlService.getAudioDeviceGroupVolume(groupId);
    }

    LeAudioDeviceDescriptor createDeviceDescriptor(BluetoothDevice device,
            boolean isInbandRingtoneEnabled) {
        LeAudioDeviceDescriptor descriptor = mDeviceDescriptors.get(device);
        if (descriptor == null) {
            mDeviceDescriptors.put(device, new LeAudioDeviceDescriptor(isInbandRingtoneEnabled));
            descriptor = mDeviceDescriptors.get(device);
            Log.d(TAG, "Created descriptor for device: " + device);
        } else {
            Log.w(TAG, "Device: " + device + ", already exists");
        }

        return descriptor;
    }

    private void setEnabledState(BluetoothDevice device, boolean enabled) {
        if (DBG) {
            Log.d(TAG, "setEnabledState: address:" + device + " enabled: " + enabled);
        }
        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "setEnabledState, mLeAudioNativeIsInitialized is not initialized");
            return;
        }
        mLeAudioNativeInterface.setEnableState(device, enabled);
    }

    public boolean connect(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "connect(): " + device);
        }

        if (getConnectionPolicy(device) == BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.e(TAG, "Cannot connect to " + device + " : CONNECTION_POLICY_FORBIDDEN");
            return false;
        }
        ParcelUuid[] featureUuids = mAdapterService.getRemoteUuids(device);
        if (!Utils.arrayContains(featureUuids, BluetoothUuid.LE_AUDIO)) {
            Log.e(TAG, "Cannot connect to " + device + " : Remote does not have LE_AUDIO UUID");
            return false;
        }

        LeAudioStateMachine sm = null;

        groupMutexLock(/* isReadOnly */ false);
        try {
            boolean isInbandRingtoneEnabled = false;
            int groupId = getGroupId(device);
            if (groupId != LE_AUDIO_GROUP_ID_INVALID) {
                isInbandRingtoneEnabled = getGroupDescriptor(groupId).mInbandRingtoneEnabled;
            }

            if (createDeviceDescriptor(device, isInbandRingtoneEnabled) == null) {
                return false;
            }

            sm = getOrCreateStateMachine(device);
            if (sm == null) {
                Log.e(TAG, "Ignored connect request for " + device + " : no state machine");
                return false;
            }

            if (!Flags.leaudioApiSynchronizedBlockFix()) {
                sm.sendMessage(LeAudioStateMachine.CONNECT);
            }

        } finally {
            groupMutexUnlock(/* isReadOnly */ false);
        }

        if (Flags.leaudioApiSynchronizedBlockFix()) {
            sm.sendMessage(LeAudioStateMachine.CONNECT);
        }

        return true;
    }

    /**
     * Disconnects LE Audio for the remote bluetooth device
     *
     * @param device is the device with which we would like to disconnect LE Audio
     * @return true if profile disconnected, false if device not connected over LE Audio
     */
    boolean disconnectV2(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "disconnectV2(): " + device);
        }

        LeAudioStateMachine sm = null;

        groupMutexLock(/* isReadOnly */ true);
        try {
            LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
            if (descriptor == null) {
                Log.e(TAG, "disconnect: No valid descriptor for device: " + device);
                return false;
            }
            sm = descriptor.mStateMachine;
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }

        if (sm == null) {
            Log.e(TAG, "Ignored disconnect request for " + device + " : no state machine");
            return false;
        }

        sm.sendMessage(LeAudioStateMachine.DISCONNECT);

        return true;
    }

    /**
     * Disconnects LE Audio for the remote bluetooth device
     *
     * @param device is the device with which we would like to disconnect LE Audio
     * @return true if profile disconnected, false if device not connected over LE Audio
     */
    public boolean disconnect(BluetoothDevice device) {
        if (Flags.leaudioApiSynchronizedBlockFix()) {
            return disconnectV2(device);
        }

        if (DBG) {
            Log.d(TAG, "disconnect(): " + device);
        }

        groupMutexLock(/* isReadOnly */ true);
        try {
            LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
            if (descriptor == null) {
                Log.e(TAG, "disconnect: No valid descriptor for device: " + device);
                return false;
            }

            LeAudioStateMachine sm = descriptor.mStateMachine;
            if (sm == null) {
                Log.e(TAG, "Ignored disconnect request for " + device
                        + " : no state machine");
                return false;
            }

            sm.sendMessage(LeAudioStateMachine.DISCONNECT);
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }

        return true;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        groupMutexLock(/* isReadOnly */ true);
        try {
            List<BluetoothDevice> devices = new ArrayList<>();
            for (LeAudioDeviceDescriptor descriptor : mDeviceDescriptors.values()) {
                LeAudioStateMachine sm = descriptor.mStateMachine;
                if (sm != null && sm.isConnected()) {
                    devices.add(sm.getDevice());
                }
            }
            return devices;
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
    }

    BluetoothDevice getConnectedGroupLeadDevice(int groupId) {
        if (getGroupId(mActiveAudioOutDevice) == groupId) {
            return mActiveAudioOutDevice;
        }

        return getLeadDeviceForTheGroup(groupId);
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
        groupMutexLock(/* isReadOnly */ true);
        try {
            for (BluetoothDevice device : bondedDevices) {
                final ParcelUuid[] featureUuids = device.getUuids();
                if (!Utils.arrayContains(featureUuids, BluetoothUuid.LE_AUDIO)) {
                    continue;
                }
                int connectionState = BluetoothProfile.STATE_DISCONNECTED;
                LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
                if (descriptor == null) {
                    Log.e(TAG, "getDevicesMatchingConnectionStates: "
                            + "No valid descriptor for device: " + device);
                    return null;
                }

                LeAudioStateMachine sm = descriptor.mStateMachine;
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
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
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
        groupMutexLock(/* isReadOnly */ true);
        try {
            for (LeAudioDeviceDescriptor descriptor : mDeviceDescriptors.values()) {
                if (descriptor.mStateMachine != null) {
                    devices.add(descriptor.mStateMachine.getDevice());
                }
            }
            return devices;
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
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
        groupMutexLock(/* isReadOnly */ true);
        try {
            LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
            if (descriptor == null) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }

            LeAudioStateMachine sm = descriptor.mStateMachine;
            if (sm == null) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }
            return sm.getConnectionState();
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
    }

    /**
     * Add device to the given group.
     * @param groupId group ID the device is being added to
     * @param device the active device
     * @return true on success, otherwise false
     */
    boolean groupAddNode(int groupId, BluetoothDevice device) {
        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "Le Audio not initialized properly.");
            return false;
        }
        return mLeAudioNativeInterface.groupAddNode(groupId, device);
    }

    /**
     * Remove device from a given group.
     * @param groupId group ID the device is being removed from
     * @param device the active device
     * @return true on success, otherwise false
     */
    boolean groupRemoveNode(int groupId, BluetoothDevice device) {
        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "Le Audio not initialized properly.");
            return false;
        }
        return mLeAudioNativeInterface.groupRemoveNode(groupId, device);
    }

    /**
     * Checks if given group exists.
     * @param group_id group Id to verify
     * @return true given group exists, otherwise false
     */
    public boolean isValidDeviceGroup(int groupId) {
        groupMutexLock(/* isReadOnly */ true);
        try {
            return groupId != LE_AUDIO_GROUP_ID_INVALID && mGroupDescriptors.containsKey(groupId);
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
    }

    /**
     * Get all the devices within a given group.
     * @param groupId group id to get devices
     * @return all devices within a given group or empty list
     */
    public List<BluetoothDevice> getGroupDevices(int groupId) {
        List<BluetoothDevice> result = new ArrayList<>();

        if (groupId == LE_AUDIO_GROUP_ID_INVALID) {
            return result;
        }

        groupMutexLock(/* isReadOnly */ true);
        try {
            for (Map.Entry<BluetoothDevice, LeAudioDeviceDescriptor> entry
                    : mDeviceDescriptors.entrySet()) {
                if (entry.getValue().mGroupId == groupId) {
                    result.add(entry.getKey());
                }
            }
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
        return result;
    }

    /**
     * Get all the devices within a given group.
     * @param device the device for which we want to get all devices in its group
     * @return all devices within a given group or empty list
     */
    public List<BluetoothDevice> getGroupDevices(BluetoothDevice device) {
        List<BluetoothDevice> result = new ArrayList<>();
        int groupId = getGroupId(device);

        if (groupId == LE_AUDIO_GROUP_ID_INVALID) {
            return result;
        }

        groupMutexLock(/* isReadOnly */ true);
        try {
            for (Map.Entry<BluetoothDevice, LeAudioDeviceDescriptor> entry
                    : mDeviceDescriptors.entrySet()) {
                if (entry.getValue().mGroupId == groupId) {
                    result.add(entry.getKey());
                }
            }
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
        return result;
    }

    /**
     * Get the active device group id
     */
    public Integer getActiveGroupId() {
        groupMutexLock(/* isReadOnly */ true);
        try {
            for (Map.Entry<Integer, LeAudioGroupDescriptor> entry : mGroupDescriptors.entrySet()) {
                LeAudioGroupDescriptor descriptor = entry.getValue();
                if (descriptor.mIsActive) {
                    return entry.getKey();
                }
            }
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
        return LE_AUDIO_GROUP_ID_INVALID;
    }

    /**
     * Creates LeAudio Broadcast instance with BluetoothLeBroadcastSettings.
     *
     * @param broadcastSettings broadcast settings for this broadcast source
     */
    public void createBroadcast(BluetoothLeBroadcastSettings broadcastSettings) {
        if (mBroadcastDescriptors.size() >= getMaximumNumberOfBroadcasts()) {
            Log.w(
                    TAG,
                    "createBroadcast reached maximum allowed broadcasts number: "
                            + getMaximumNumberOfBroadcasts());
            notifyBroadcastStartFailed(null, BluetoothStatusCodes.ERROR_LOCAL_NOT_ENOUGH_RESOURCES);
            return;
        }

        if (mLeAudioBroadcasterNativeInterface == null) {
            Log.w(TAG, "Native interface not available.");
            return;
        }

        if (mAwaitingBroadcastCreateResponse) {
            mCreateBroadcastQueue.add(broadcastSettings);
            Log.i(TAG, "Broadcast creation queued due to waiting for a previous request response.");
            return;
        }

        if (getActiveGroupId() != LE_AUDIO_GROUP_ID_INVALID) {
            /* Broadcast would be created once unicast group became inactive */
            Log.i(TAG, "Unicast group is active, queueing Broadcast creation, while the Unicast"
                        + " group is deactivated.");
            mCreateBroadcastQueue.add(broadcastSettings);
            if (Flags.leaudioBroadcastAudioHandoverPolicies()) {
                mLeAudioNativeInterface.setUnicastMonitorMode(LeAudioStackEvent.DIRECTION_SINK,
                        true);
            }
            removeActiveDevice(true);

            return;
        }

        byte[] broadcastCode = broadcastSettings.getBroadcastCode();
        boolean isEncrypted = (broadcastCode != null) && (broadcastCode.length != 0);
        if (isEncrypted) {
            if ((broadcastCode.length > 16) || (broadcastCode.length < 4)) {
                Log.e(TAG, "Invalid broadcast code length. Should be from 4 to 16 octets long.");
                return;
            }
        }

        List<BluetoothLeBroadcastSubgroupSettings> settingsList =
                broadcastSettings.getSubgroupSettings();
        if (settingsList == null || settingsList.size() < 1) {
            Log.d(TAG, "subgroup settings is not valid value");
            return;
        }

        BluetoothLeAudioContentMetadata publicMetadata =
                broadcastSettings.getPublicBroadcastMetadata();

        Log.i(TAG, "createBroadcast: isEncrypted=" + (isEncrypted ? "true" : "false"));

        mAwaitingBroadcastCreateResponse = true;
        mLeAudioBroadcasterNativeInterface.createBroadcast(
                broadcastSettings.isPublicBroadcast(),
                broadcastSettings.getBroadcastName(),
                broadcastCode,
                publicMetadata == null ? null : publicMetadata.getRawMetadata(),
                getBroadcastAudioQualityPerSinkCapabilities(settingsList),
                settingsList.stream()
                        .map(s -> s.getContentMetadata().getRawMetadata())
                        .toArray(byte[][]::new));
    }

    private int[] getBroadcastAudioQualityPerSinkCapabilities(
            List<BluetoothLeBroadcastSubgroupSettings> settingsList) {
        int[] preferredQualityArray =
                settingsList.stream().mapToInt(s -> s.getPreferredQuality()).toArray();

        BassClientService bassClientService = getBassClientService();
        if (bassClientService == null) {
            return preferredQualityArray;
        }

        for (BluetoothDevice sink : bassClientService.getConnectedDevices()) {
            int groupId = getGroupId(sink);
            if (groupId == LE_AUDIO_GROUP_ID_INVALID) {
                continue;
            }

            BluetoothLeAudioCodecStatus codecStatus = getCodecStatus(groupId);
            if (codecStatus != null
                    && !codecStatus.isInputCodecConfigSelectable(BROADCAST_HIGH_QUALITY_CONFIG)) {
                // If any sink device does not support high quality audio config,
                // set all subgroup audio quality to standard quality for now before multi codec
                // config support is ready
                Log.i(
                        TAG,
                        "Sink device doesn't support HIGH broadcast audio quality, use STANDARD"
                                + " quality");
                Arrays.fill(
                        preferredQualityArray,
                        BluetoothLeBroadcastSubgroupSettings.QUALITY_STANDARD);
                break;
            }
        }
        return preferredQualityArray;
    }

    /**
     * Start LeAudio Broadcast instance.
     * @param broadcastId broadcast instance identifier
     */
    public void startBroadcast(int broadcastId) {
        if (mLeAudioBroadcasterNativeInterface == null) {
            Log.w(TAG, "Native interface not available.");
            return;
        }
        if (DBG) Log.d(TAG, "startBroadcast");

        /* Start timeout to recover from stucked/error start Broadcast operation */
        mDialingOutTimeoutEvent = new DialingOutTimeoutEvent();
        mHandler.postDelayed(mDialingOutTimeoutEvent, DIALING_OUT_TIMEOUT_MS);

        mLeAudioBroadcasterNativeInterface.startBroadcast(broadcastId);
    }

    /**
     * Updates LeAudio broadcast instance metadata.
     *
     * @param broadcastId broadcast instance identifier
     * @param broadcastSettings broadcast settings for this broadcast source
     */
    public void updateBroadcast(int broadcastId, BluetoothLeBroadcastSettings broadcastSettings) {
        if (mLeAudioBroadcasterNativeInterface == null) {
            Log.w(TAG, "Native interface not available.");
            return;
        }

        LeAudioBroadcastDescriptor descriptor = mBroadcastDescriptors.get(broadcastId);
        if (descriptor == null) {
            notifyBroadcastUpdateFailed(
                    broadcastId, BluetoothStatusCodes.ERROR_LE_BROADCAST_INVALID_BROADCAST_ID);
            Log.e(TAG, "updateBroadcast: No valid descriptor for broadcastId: " + broadcastId);
            return;
        }

        List<BluetoothLeBroadcastSubgroupSettings> settingsList =
                broadcastSettings.getSubgroupSettings();
        if (settingsList == null || settingsList.size() < 1) {
            Log.d(TAG, "subgroup settings is not valid value");
            return;
        }

        BluetoothLeAudioContentMetadata publicMetadata =
                broadcastSettings.getPublicBroadcastMetadata();

        if (DBG) Log.d(TAG, "updateBroadcast");
        mLeAudioBroadcasterNativeInterface.updateMetadata(broadcastId,
                broadcastSettings.getBroadcastName(),
                publicMetadata == null ? null : publicMetadata.getRawMetadata(),
                settingsList.stream()
                        .map(s -> s.getContentMetadata().getRawMetadata())
                        .toArray(byte[][]::new));
    }

    /**
     * Pause LeAudio Broadcast instance.
     *
     * @param broadcastId broadcast instance identifier
     */
    public void pauseBroadcast(Integer broadcastId) {
        if (mLeAudioBroadcasterNativeInterface == null) {
            Log.w(TAG, "Native interface not available.");
            return;
        }

        LeAudioBroadcastDescriptor descriptor = mBroadcastDescriptors.get(broadcastId);
        if (descriptor == null) {
            Log.e(TAG, "pauseBroadcast: No valid descriptor for broadcastId: " + broadcastId);
            return;
        }

        if (DBG) Log.d(TAG, "pauseBroadcast");
        mLeAudioBroadcasterNativeInterface.pauseBroadcast(broadcastId);
    }

    /**
     * Stop LeAudio Broadcast instance.
     * @param broadcastId broadcast instance identifier
     */
    public void stopBroadcast(Integer broadcastId) {
        if (mLeAudioBroadcasterNativeInterface == null) {
            Log.w(TAG, "Native interface not available.");
            return;
        }

        LeAudioBroadcastDescriptor descriptor = mBroadcastDescriptors.get(broadcastId);
        if (descriptor == null) {
            notifyOnBroadcastStopFailed(
                    BluetoothStatusCodes.ERROR_LE_BROADCAST_INVALID_BROADCAST_ID);
            Log.e(TAG, "stopBroadcast: No valid descriptor for broadcastId: " + broadcastId);
            return;
        }

        if (DBG) Log.d(TAG, "stopBroadcast");
        mLeAudioBroadcasterNativeInterface.stopBroadcast(broadcastId);
    }

    /**
     * Destroy LeAudio Broadcast instance.
     * @param broadcastId broadcast instance identifier
     */
    public void destroyBroadcast(int broadcastId) {
        if (mLeAudioBroadcasterNativeInterface == null) {
            Log.w(TAG, "Native interface not available.");
            return;
        }

        LeAudioBroadcastDescriptor descriptor = mBroadcastDescriptors.get(broadcastId);
        if (descriptor == null) {
            notifyOnBroadcastStopFailed(
                    BluetoothStatusCodes.ERROR_LE_BROADCAST_INVALID_BROADCAST_ID);
            Log.e(TAG, "destroyBroadcast: No valid descriptor for broadcastId: " + broadcastId);
            return;
        }

        if (DBG) Log.d(TAG, "destroyBroadcast");
        if (Flags.leaudioBroadcastAudioHandoverPolicies()) {
            mLeAudioNativeInterface.setUnicastMonitorMode(LeAudioStackEvent.DIRECTION_SINK, false);
        }
        mLeAudioBroadcasterNativeInterface.destroyBroadcast(broadcastId);
    }

    /**
     * Checks if Broadcast instance is playing.
     * @param broadcastId broadcast instance identifier
     * @return true if if broadcast is playing, false otherwise
     */
    public boolean isPlaying(int broadcastId) {
        LeAudioBroadcastDescriptor descriptor = mBroadcastDescriptors.get(broadcastId);
        if (descriptor == null) {
            Log.e(TAG, "isPlaying: No valid descriptor for broadcastId: " + broadcastId);
            return false;
        }

        return descriptor.mState.equals(LeAudioStackEvent.BROADCAST_STATE_STREAMING);
    }

    /**
     * Get all broadcast metadata.
     * @return list of all know Broadcast metadata
     */
    public List<BluetoothLeBroadcastMetadata> getAllBroadcastMetadata() {
        return mBroadcastDescriptors.values().stream()
                .map(s -> s.mMetadata)
                .collect(Collectors.toList());
    }

    /**
     * Check if broadcast is active
     *
     * @return true if there is active broadcast, false otherwise
     */
    public boolean isBroadcastActive() {
        return !mBroadcastDescriptors.isEmpty();
    }

    /**
     * Get the maximum number of supported simultaneous broadcasts.
     * @return number of supported simultaneous broadcasts
     */
    public int getMaximumNumberOfBroadcasts() {
        /* TODO: This is currently fixed to 1 */
        return 1;
    }

    /**
     * Get the maximum number of supported streams per broadcast.
     *
     * @return number of supported streams per broadcast
     */
    public int getMaximumStreamsPerBroadcast() {
        /* TODO: This is currently fixed to 1 */
        return 1;
    }

    /**
     * Get the maximum number of supported subgroups per broadcast.
     *
     * @return number of supported subgroups per broadcast
     */
    public int getMaximumSubgroupsPerBroadcast() {
        /* TODO: This is currently fixed to 1 */
        return 1;
    }

    /**
     * Active Broadcast Assistant notification handler
     */
    public void activeBroadcastAssistantNotification(boolean active) {
        if (getBassClientService() == null) {
            Log.w(TAG, "Ignore active Broadcast Assistant notification");
            return;
        }

        if (active) {
            mIsSourceStreamMonitorModeEnabled = true;
            mLeAudioNativeInterface
                    .setUnicastMonitorMode(LeAudioStackEvent.DIRECTION_SOURCE, true);
        } else {
            if (mIsSourceStreamMonitorModeEnabled) {
                mLeAudioNativeInterface
                        .setUnicastMonitorMode(LeAudioStackEvent.DIRECTION_SOURCE, false);
            }

            mIsSourceStreamMonitorModeEnabled = false;
        }
    }

    /** Return true if device is primary - is active or was active before switch to broadcast */
    public boolean isPrimaryDevice(BluetoothDevice device) {
        LeAudioDeviceDescriptor descriptor = mDeviceDescriptors.get(device);
        if (descriptor == null) {
            return false;
        }

        return descriptor.mGroupId == mUnicastGroupIdDeactivatedForBroadcastTransition;
    }

    private boolean areBroadcastsAllStopped() {
        if (mBroadcastDescriptors == null) {
            Log.e(TAG, "areBroadcastsAllStopped: Invalid Broadcast Descriptors");
            return false;
        }

        return mBroadcastDescriptors.values().stream()
                .allMatch(d -> d.mState.equals(LeAudioStackEvent.BROADCAST_STATE_STOPPED));
    }

    private Optional<Integer> getFirstNotStoppedBroadcastId() {
        if (mBroadcastDescriptors == null) {
            Log.e(TAG, "getFirstNotStoppedBroadcastId: Invalid Broadcast Descriptors");
            return Optional.empty();
        }

        for (Map.Entry<Integer, LeAudioBroadcastDescriptor> entry :
                mBroadcastDescriptors.entrySet()) {
            if (!entry.getValue().mState.equals(LeAudioStackEvent.BROADCAST_STATE_STOPPED)) {
                return Optional.of(entry.getKey());
            }
        }

        return Optional.empty();
    }

    private boolean areAllGroupsInNotActiveState() {
        groupMutexLock(/* isReadOnly */ true);
        try {
            for (Map.Entry<Integer, LeAudioGroupDescriptor> entry : mGroupDescriptors.entrySet()) {
                LeAudioGroupDescriptor descriptor = entry.getValue();
                if (descriptor.mIsActive) {
                    return false;
                }
            }
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
        return true;
    }

    private BluetoothDevice getLeadDeviceForTheGroup(Integer groupId) {
        if (groupId == LE_AUDIO_GROUP_ID_INVALID) {
            return null;
        }
        groupMutexLock(/* isReadOnly */ true);
        try {
            LeAudioGroupDescriptor groupDescriptor = getGroupDescriptor(groupId);
            if (groupDescriptor == null) {
                Log.e(TAG, "Group " + groupId + " does not exist");
                return null;
            }

            if (groupDescriptor.mCurrentLeadDevice != null
                    && getConnectionState(groupDescriptor.mCurrentLeadDevice)
                            == BluetoothProfile.STATE_CONNECTED) {
                return groupDescriptor.mCurrentLeadDevice;
            }

            for (LeAudioDeviceDescriptor descriptor : mDeviceDescriptors.values()) {
                if (!descriptor.mGroupId.equals(groupId)) {
                    continue;
                }

                LeAudioStateMachine sm = descriptor.mStateMachine;
                if (sm == null || sm.getConnectionState() != BluetoothProfile.STATE_CONNECTED) {
                    continue;
                }
                groupDescriptor.mCurrentLeadDevice = sm.getDevice();
                return groupDescriptor.mCurrentLeadDevice;
            }
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
        return null;
    }

    private boolean updateActiveInDevice(BluetoothDevice device, Integer groupId,
            Integer oldSupportedAudioDirections, Integer newSupportedAudioDirections) {
        boolean oldSupportedByDeviceInput = (oldSupportedAudioDirections
                & AUDIO_DIRECTION_INPUT_BIT) != 0;
        boolean newSupportedByDeviceInput = (newSupportedAudioDirections
                & AUDIO_DIRECTION_INPUT_BIT) != 0;

        /*
         * Do not update input if neither previous nor current device support input
         */
        if (!oldSupportedByDeviceInput && !newSupportedByDeviceInput) {
            Log.d(TAG, "updateActiveInDevice: Device does not support input.");
            return false;
        }

        if (device != null && mActiveAudioInDevice != null) {
            LeAudioDeviceDescriptor deviceDescriptor = getDeviceDescriptor(device);
            if (deviceDescriptor == null) {
                Log.e(TAG, "updateActiveInDevice: No valid descriptor for device: " + device);
                return false;
            }

            if (deviceDescriptor.mGroupId.equals(groupId)) {
                /* This is thes same group as aleady notified to the system.
                 * Therefore do not change the device we have connected to the group,
                 * unless, previous one is disconnected now
                 */
                if (mActiveAudioInDevice.isConnected()) {
                    device = mActiveAudioInDevice;
                }
            } else if (deviceDescriptor.mGroupId != LE_AUDIO_GROUP_ID_INVALID) {
                /* Mark old group as no active */
                LeAudioGroupDescriptor descriptor = getGroupDescriptor(deviceDescriptor.mGroupId);
                if (descriptor != null) {
                    descriptor.mIsActive = false;
                }
            }
        }

        BluetoothDevice previousInDevice = mActiveAudioInDevice;

        /*
         * Update input if:
         * - Device changed
         *     OR
         * - Device stops / starts supporting input
         */
        if (!Objects.equals(device, previousInDevice)
                || (oldSupportedByDeviceInput != newSupportedByDeviceInput)) {
            mActiveAudioInDevice = newSupportedByDeviceInput ? device : null;
            if (DBG) {
                Log.d(TAG, " handleBluetoothActiveDeviceChanged previousInDevice: "
                        + previousInDevice + ", mActiveAudioInDevice: " + mActiveAudioInDevice
                        + " isLeOutput: false");
            }

            return true;
        }
        Log.d(TAG, "updateActiveInDevice: Nothing to do.");
        return false;
    }

    private boolean updateActiveOutDevice(BluetoothDevice device, Integer groupId,
            Integer oldSupportedAudioDirections, Integer newSupportedAudioDirections) {
        boolean oldSupportedByDeviceOutput = (oldSupportedAudioDirections
                & AUDIO_DIRECTION_OUTPUT_BIT) != 0;
        boolean newSupportedByDeviceOutput = (newSupportedAudioDirections
                & AUDIO_DIRECTION_OUTPUT_BIT) != 0;

        /*
         * Do not update output if neither previous nor current device support output
         */
        if (!oldSupportedByDeviceOutput && !newSupportedByDeviceOutput) {
            Log.d(TAG, "updateActiveOutDevice: Device does not support output.");
            return false;
        }

        if (device != null && mActiveAudioOutDevice != null) {
            LeAudioDeviceDescriptor deviceDescriptor = getDeviceDescriptor(device);
            if (deviceDescriptor == null) {
                Log.e(TAG, "updateActiveOutDevice: No valid descriptor for device: " + device);
                return false;
            }

            if (deviceDescriptor.mGroupId.equals(groupId)) {
                /* This is the same group as already notified to the system.
                 * Therefore do not change the device we have connected to the group,
                 * unless, previous one is disconnected now
                 */
                if (mActiveAudioOutDevice.isConnected()) {
                    device = mActiveAudioOutDevice;
                }
            } else if (deviceDescriptor.mGroupId != LE_AUDIO_GROUP_ID_INVALID) {
                Log.i(TAG, " Switching active group from " + deviceDescriptor.mGroupId + " to "
                        + groupId);
                /* Mark old group as no active */
                LeAudioGroupDescriptor descriptor = getGroupDescriptor(deviceDescriptor.mGroupId);
                if (descriptor != null) {
                    descriptor.mIsActive = false;
                }
            }
        }

        BluetoothDevice previousOutDevice = mActiveAudioOutDevice;

        /*
         * Update output if:
         * - Device changed
         *     OR
         * - Device stops / starts supporting output
         */
        if (!Objects.equals(device, previousOutDevice)
                || (oldSupportedByDeviceOutput != newSupportedByDeviceOutput)) {
            mActiveAudioOutDevice = newSupportedByDeviceOutput ? device : null;
            if (DBG) {
                Log.d(TAG, " handleBluetoothActiveDeviceChanged previousOutDevice: "
                        + previousOutDevice + ", mActiveAudioOutDevice: " + mActiveAudioOutDevice
                        + " isLeOutput: true");
            }
            return true;
        }
        Log.d(TAG, "updateActiveOutDevice: Nothing to do.");
        return false;
    }

    /**
     * Send broadcast intent about LeAudio connection state changed. This is called by
     * LeAudioStateMachine.
     */
    void notifyConnectionStateChanged(BluetoothDevice device, int newState, int prevState) {
        if (DBG) {
            Log.d(
                    TAG,
                    "Notify connection state changed."
                            + device
                            + "("
                            + prevState
                            + " -> "
                            + newState
                            + ")");
        }

        mAdapterService.notifyProfileConnectionStateChangeToGatt(
                BluetoothProfile.LE_AUDIO, prevState, newState);
        mAdapterService.handleProfileConnectionStateChange(
                BluetoothProfile.LE_AUDIO, device, prevState, newState);
        mAdapterService
                .getActiveDeviceManager()
                .profileConnectionStateChanged(
                        BluetoothProfile.LE_AUDIO, device, prevState, newState);
        mAdapterService.updateProfileConnectionAdapterProperties(
                device, BluetoothProfile.LE_AUDIO, newState, prevState);

        Intent intent = new Intent(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, newState);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.addFlags(
                Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                        | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        Utils.sendBroadcast(
                this, intent, BLUETOOTH_CONNECT, Utils.getTempAllowlistBroadcastOptions());
    }

    void sentActiveDeviceChangeIntent(BluetoothDevice device) {
        Intent intent = new Intent(BluetoothLeAudio.ACTION_LE_AUDIO_ACTIVE_DEVICE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.addFlags(
                Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                        | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        sendBroadcast(intent, BLUETOOTH_CONNECT);
    }

    void notifyVolumeControlServiceAboutActiveGroup(BluetoothDevice device) {
        VolumeControlService volumeControlService = getVolumeControlService();
        if (volumeControlService == null) {
            return;
        }

        if (mExposedActiveDevice != null) {
            volumeControlService.setGroupActive(getGroupId(mExposedActiveDevice), false);
        }

        if (device != null) {
            volumeControlService.setGroupActive(getGroupId(device), true);
        }
    }

    /**
     * Send broadcast intent about LeAudio active device.
     * This is called when AudioManager confirms, LeAudio device
     * is added or removed.
     */
    @VisibleForTesting
    void notifyActiveDeviceChanged(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "Notify Active device changed." + device
                    + ". Currently active device is " + mActiveAudioOutDevice);
        }

        mAdapterService.handleActiveDeviceChange(BluetoothProfile.LE_AUDIO, device);
        sentActiveDeviceChangeIntent(device);
        notifyVolumeControlServiceAboutActiveGroup(device);
        mExposedActiveDevice = device;
    }

    boolean isScannerNeeded() {
        if (mDeviceDescriptors.isEmpty() || !mBluetoothEnabled) {
            if (DBG) {
                Log.d(TAG, "isScannerNeeded: false, mBluetoothEnabled: " + mBluetoothEnabled);
            }
            return false;
        }

        if (allLeAudioDevicesConnected()) {
            Log.d(TAG, "isScannerNeeded: all devices connected, scanner not needed");
            return false;
        }

        if (DBG) {
            Log.d(TAG, "isScannerNeeded: true");
        }
        return true;
    }

    boolean allLeAudioDevicesConnected() {
        groupMutexLock(/* isReadOnly */ true);
        try {
            for (Map.Entry<BluetoothDevice, LeAudioDeviceDescriptor> deviceEntry :
                    mDeviceDescriptors.entrySet()) {
                LeAudioDeviceDescriptor deviceDescriptor = deviceEntry.getValue();

                if (deviceDescriptor.mStateMachine == null) {
                    /* Lack of state machine means device is not connected */
                    return false;
                }

                if (!deviceDescriptor.mStateMachine.isConnected()
                        || !deviceDescriptor.mAclConnected) {
                    return false;
                }
            }
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
        return true;
    }

    private class AudioServerScanCallback extends ScanCallback {
        int mMaxScanRetires = 10;
        int mScanRetries = 0;

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            /* Filter is set in the way, that there will be no results found.
             * We just need a scanner to be running for the APCF filtering defined in native
             */
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            /* Filter is set in the way, that there will be no results found.
             * We just need a scanner to be running for the APCF filtering defined in native
             */
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "Scan failed err: " + errorCode + " scan retries: " + mScanRetries);
            switch(errorCode) {
                case SCAN_FAILED_INTERNAL_ERROR:
                case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    if (mScanRetries < mMaxScanRetires) {
                        mScanRetries++;
                        Log.w(TAG, "Failed to start. Let's retry");
                        mHandler.post(() -> startAudioServersBackgroundScan(/* retry = */ true));
                    }
                    break;
                default:
                    /* Indicate scan is no running */
                    mScanCallback = null;
                    break;
            }
        }
    }

    /* Notifications of audio device connection/disconn events. */
    private class AudioManagerAudioDeviceCallback extends AudioDeviceCallback {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            if (mAudioManager == null || mAdapterService == null)  {
                Log.e(TAG, "Callback called when LeAudioService is stopped");
                return;
            }

            for (AudioDeviceInfo deviceInfo : addedDevices) {
                if ((deviceInfo.getType() != AudioDeviceInfo.TYPE_BLE_HEADSET)
                        && (deviceInfo.getType() != AudioDeviceInfo.TYPE_BLE_SPEAKER)) {
                    continue;
                }

                String address = deviceInfo.getAddress();
                if (address.equals("00:00:00:00:00:00")) {
                    continue;
                }

                byte[] addressBytes = Utils.getBytesFromAddress(address);
                BluetoothDevice device = mAdapterService.getDeviceFromByte(addressBytes);

                if (DBG) {
                    Log.d(TAG, " onAudioDevicesAdded: " + device + ", device type: "
                            + deviceInfo.getType() + ", isSink: " + deviceInfo.isSink()
                            + " isSource: " + deviceInfo.isSource());
                }

                /* Don't expose already exposed active device */
                if (device.equals(mExposedActiveDevice)) {
                    if (DBG) {
                        Log.d(TAG, " onAudioDevicesAdded: " + device + " is already exposed");
                    }
                    return;
                }


                if ((deviceInfo.isSink() && !device.equals(mActiveAudioOutDevice))
                        || (deviceInfo.isSource() && !device.equals(mActiveAudioInDevice))) {
                    Log.e(TAG, "Added device does not match to the one activated here. ("
                            + device + " != " + mActiveAudioOutDevice
                            + " / " + mActiveAudioInDevice + ")");
                    continue;
                }

                notifyActiveDeviceChanged(device);
                return;
            }
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            if (mAudioManager == null || mAdapterService == null) {
                Log.e(TAG, "Callback called when LeAudioService is stopped");
                return;
            }

            for (AudioDeviceInfo deviceInfo : removedDevices) {
                if ((deviceInfo.getType() != AudioDeviceInfo.TYPE_BLE_HEADSET)
                        && (deviceInfo.getType() != AudioDeviceInfo.TYPE_BLE_SPEAKER)) {
                    continue;
                }

                String address = deviceInfo.getAddress();
                if (address.equals("00:00:00:00:00:00")) {
                    continue;
                }

                byte[] addressBytes = Utils.getBytesFromAddress(address);
                BluetoothDevice device = mAdapterService.getDeviceFromByte(addressBytes);

                if (DBG) {
                    Log.d(TAG, " onAudioDevicesRemoved: " + address + ", device type: "
                            + deviceInfo.getType() + ", isSink: " + deviceInfo.isSink()
                            + " isSource: " + deviceInfo.isSource()
                            + ", mActiveAudioInDevice: " + mActiveAudioInDevice
                            + ", mActiveAudioOutDevice: " +  mActiveAudioOutDevice);
                }

                if (device != mExposedActiveDevice) {
                    continue;
                }

                Log.i(TAG, "Audio manager disactivate LeAudio device " + mExposedActiveDevice);
                mExposedActiveDevice = null;
                setActiveDevice(null);
            }
        }
    }

    /*
     * Report the active broadcast device change to the active device manager and the media
     * framework.
     * @param newDevice new supported broadcast audio device
     * @param previousDevice previous no longer supported broadcast audio device
     */
    private void updateBroadcastActiveDevice(
            BluetoothDevice newDevice,
            BluetoothDevice previousDevice,
            boolean suppressNoisyIntent) {
        mActiveBroadcastAudioDevice = newDevice;
        if (DBG) {
            Log.d(
                    TAG,
                    "updateBroadcastActiveDevice: newDevice: "
                            + newDevice
                            + ", previousDevice: "
                            + previousDevice);
        }
        mAudioManager.handleBluetoothActiveDeviceChanged(
                newDevice, previousDevice, getBroadcastProfile(suppressNoisyIntent));
    }

    /*
     * Listen mode is set when broadcast is queued, waiting for create response notification or
     * descriptor was created - idicate that create notification was received.
     */
    private boolean wasSetSinkListeningMode() {
        return !mCreateBroadcastQueue.isEmpty() || mAwaitingBroadcastCreateResponse
                || !mBroadcastDescriptors.isEmpty();
    }

    /**
     * Report the active devices change to the active device manager and the media framework.
     *
     * @param groupId id of group which devices should be updated
     * @param newSupportedAudioDirections new supported audio directions for group of devices
     * @param oldSupportedAudioDirections old supported audio directions for group of devices
     * @param isActive if there is new active group
     * @param hasFallbackDevice whether any fallback device exists when deactivating the current
     *     active device.
     * @param notifyAndUpdateInactiveOutDeviceOnly if only output device should be updated to
     *     inactive devices (if new out device would be null device).
     * @return true if group is active after change false otherwise.
     */
    private boolean updateActiveDevices(
            Integer groupId,
            Integer oldSupportedAudioDirections,
            Integer newSupportedAudioDirections,
            boolean isActive,
            boolean hasFallbackDevice,
            boolean notifyAndUpdateInactiveOutDeviceOnly) {
        BluetoothDevice newOutDevice = null;
        BluetoothDevice newInDevice = null;
        BluetoothDevice previousActiveOutDevice = mActiveAudioOutDevice;
        BluetoothDevice previousActiveInDevice = mActiveAudioInDevice;

        if (isActive) {
            newOutDevice = getLeadDeviceForTheGroup(groupId);
            newInDevice = newOutDevice;
        } else {
            /* While broadcasting a input device needs to be connected to track Audio Framework
             * streaming requests. This would allow native to make a fallback to Unicast decision.
             */
            if (notifyAndUpdateInactiveOutDeviceOnly
                    && ((newSupportedAudioDirections & AUDIO_DIRECTION_INPUT_BIT) != 0)) {
                newInDevice = getLeadDeviceForTheGroup(groupId);
            } else if (Flags.leaudioBroadcastAudioHandoverPolicies() && wasSetSinkListeningMode()) {
                mLeAudioNativeInterface.setUnicastMonitorMode(LeAudioStackEvent.DIRECTION_SINK,
                        false);
            }
        }

        boolean isNewActiveOutDevice = updateActiveOutDevice(newOutDevice, groupId,
                oldSupportedAudioDirections, newSupportedAudioDirections);
        boolean isNewActiveInDevice = updateActiveInDevice(newInDevice, groupId,
                oldSupportedAudioDirections, newSupportedAudioDirections);

        if (DBG) {
            Log.d(TAG, " isNewActiveOutDevice: " + isNewActiveOutDevice + ", "
                    + mActiveAudioOutDevice + ", isNewActiveInDevice: " + isNewActiveInDevice
                    + ", " + mActiveAudioInDevice + ", notifyAndUpdateInactiveOutDeviceOnly: "
                    + notifyAndUpdateInactiveOutDeviceOnly);
        }

        if (isNewActiveOutDevice) {
            int volume = IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME;

            if (mActiveAudioOutDevice != null) {
                volume = getAudioDeviceGroupVolume(groupId);
            }

            final boolean suppressNoisyIntent = hasFallbackDevice || mActiveAudioOutDevice != null;

            Log.d(
                    TAG,
                    "suppressNoisyIntent: "
                            + suppressNoisyIntent
                            + ", hasFallbackDevice: "
                            + hasFallbackDevice);
            final BluetoothProfileConnectionInfo connectionInfo;
            if (isAtLeastU()) {
                connectionInfo =
                        BluetoothProfileConnectionInfo.createLeAudioOutputInfo(
                                suppressNoisyIntent, volume);
            } else {
                connectionInfo =
                        BluetoothProfileConnectionInfo.createLeAudioInfo(suppressNoisyIntent, true);
            }
            mAudioManager.handleBluetoothActiveDeviceChanged(
                    mActiveAudioOutDevice, previousActiveOutDevice, connectionInfo);
        }

        if (isNewActiveInDevice) {
            mAudioManager.handleBluetoothActiveDeviceChanged(mActiveAudioInDevice,
                    previousActiveInDevice, BluetoothProfileConnectionInfo.createLeAudioInfo(
                            false, false));
        }

        if ((mActiveAudioOutDevice == null)
                && (notifyAndUpdateInactiveOutDeviceOnly || (mActiveAudioInDevice == null))) {
            /* Notify about inactive device as soon as possible.
             * When adding new device, wait with notification until AudioManager is ready
             * with adding the device.
             */
            notifyActiveDeviceChanged(null);
        }

        return mActiveAudioOutDevice != null;
    }

    private void clearInactiveDueToContextTypeFlags() {
        groupMutexLock(/* isReadOnly */ true);
        try {
            for (Map.Entry<Integer, LeAudioGroupDescriptor> groupEntry :
                    mGroupDescriptors.entrySet()) {
                LeAudioGroupDescriptor groupDescriptor = groupEntry.getValue();
                if (groupDescriptor.mInactivatedDueToContextType) {
                    if (DBG) {
                        Log.d(TAG, "clearInactiveDueToContextTypeFlags " + groupEntry.getKey());
                    }
                    groupDescriptor.mInactivatedDueToContextType = false;
                }
            }
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
    }

    /**
     * Set the active device group.
     *
     * @param hasFallbackDevice hasFallbackDevice whether any fallback device exists when
     *                          {@code device} is null.
     */
    private void setActiveGroupWithDevice(BluetoothDevice device, boolean hasFallbackDevice) {
        int groupId = LE_AUDIO_GROUP_ID_INVALID;

        if (device != null) {
            LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
            if (descriptor == null) {
                Log.e(TAG, "setActiveGroupWithDevice: No valid descriptor for device: " + device);
                return;
            }

            groupId = descriptor.mGroupId;
            clearInactiveDueToContextTypeFlags();
        }

        int currentlyActiveGroupId = getActiveGroupId();
        if (DBG) {
            Log.d(
                    TAG,
                    "setActiveGroupWithDevice = "
                            + groupId
                            + ", currentlyActiveGroupId = "
                            + currentlyActiveGroupId
                            + ", device: "
                            + device
                            + ", hasFallbackDevice: "
                            + hasFallbackDevice
                            + ", mExposedActiveDevice: "
                            + mExposedActiveDevice);
        }

        if (isBroadcastActive()
                && currentlyActiveGroupId == LE_AUDIO_GROUP_ID_INVALID
                && mUnicastGroupIdDeactivatedForBroadcastTransition != LE_AUDIO_GROUP_ID_INVALID
                && groupId != LE_AUDIO_GROUP_ID_INVALID) {
            // If broadcast is ongoing and need to update unicast fallback active group
            // we need to update the cached group id and skip changing the active device
            updateFallbackUnicastGroupIdForBroadcast(groupId);
            return;
        }

        LeAudioGroupDescriptor groupDescriptor = getGroupDescriptor(currentlyActiveGroupId);
        if (groupDescriptor != null && groupId == currentlyActiveGroupId) {
            /* Make sure active group is already exposed to audio framework.
             * If not, lets wait for it and don't sent additional intent.
             */
            if (groupDescriptor.mCurrentLeadDevice == mExposedActiveDevice) {
                Log.w(
                        TAG,
                        "group is already active: device="
                                + device
                                + ", groupId = "
                                + groupId
                                + ", exposedDevice: "
                                + mExposedActiveDevice);
                sentActiveDeviceChangeIntent(mExposedActiveDevice);
            }
            return;
        }

        if (currentlyActiveGroupId != LE_AUDIO_GROUP_ID_INVALID
                && (groupId != LE_AUDIO_GROUP_ID_INVALID || hasFallbackDevice)) {
            Log.i(TAG, "Remember that device has FallbackDevice when become inactive active");
            groupDescriptor.mHasFallbackDeviceWhenGettingInactive = true;
        }

        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "Le Audio not initialized properly.");
            return;
        }
        mLeAudioNativeInterface.groupSetActive(groupId);
        if (groupId == LE_AUDIO_GROUP_ID_INVALID) {
            /* Native will clear its states and send us group Inactive.
             * However we would like to notify audio framework that LeAudio is not
             * active anymore and does not want to get more audio data.
             */
            handleGroupTransitToInactive(currentlyActiveGroupId);
        }
    }

    /**
     * Remove the current active group.
     *
     * @param hasFallbackDevice whether any fallback device exists when deactivating
     *                          the current active device.
     * @return true on success, otherwise false
     */
    public boolean removeActiveDevice(boolean hasFallbackDevice) {
        /* Clear active group */
        if (DBG) {
            Log.d(TAG, "removeActiveDevice, hasFallbackDevice " + hasFallbackDevice);
        }
        setActiveGroupWithDevice(null, hasFallbackDevice);
        return true;
    }

    /**
     * Set the active group represented by device.
     *
     * @param device the new active device. Should not be null.
     * @return true on success, otherwise false
     */
    public boolean setActiveDevice(BluetoothDevice device) {
        Log.i(TAG, "setActiveDevice: device=" + device + ", current out="
                + mActiveAudioOutDevice + ", current in=" + mActiveAudioInDevice);
        /* Clear active group */
        if (device == null) {
            Log.e(TAG, "device should not be null!");
            return removeActiveDevice(false);
        }
        if (getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
            Log.e(TAG, "setActiveDevice(" + device + "): failed because group device is not "
                    + "connected");
            return false;
        }

        if (!Flags.audioRoutingCentralization()) {
            // If AUDIO_ROUTING_CENTRALIZATION, this will be checked inside AudioRoutingManager.
            if (Utils.isDualModeAudioEnabled()) {
                if (!mAdapterService.isAllSupportedClassicAudioProfilesActive(device)) {
                    Log.e(
                            TAG,
                            "setActiveDevice("
                                    + device
                                    + "): failed because the device is not "
                                    + "active for all supported classic audio profiles");
                    return false;
                }
            }
        }
        setActiveGroupWithDevice(device, false);
        return true;
    }

    /**
     * Get the active LE audio devices.
     *
     * Note: When LE audio group is active, one of the Bluetooth device address
     * which belongs to the group, represents the active LE audio group - it is called
     * Lead device.
     * Internally, this address is translated to LE audio group id.
     *
     * @return List of active group members. First element is a Lead device.
     */
    public List<BluetoothDevice> getActiveDevices() {
        if (DBG) {
            Log.d(TAG, "getActiveDevices");
        }
        ArrayList<BluetoothDevice> activeDevices = new ArrayList<>(2);
        activeDevices.add(null);
        activeDevices.add(null);

        int currentlyActiveGroupId = getActiveGroupId();
        if (currentlyActiveGroupId == LE_AUDIO_GROUP_ID_INVALID) {
            return activeDevices;
        }

        BluetoothDevice leadDevice = getConnectedGroupLeadDevice(currentlyActiveGroupId);
        activeDevices.set(0, leadDevice);

        int i = 1;
        for (BluetoothDevice dev : getGroupDevices(currentlyActiveGroupId)) {
            if (Objects.equals(dev, leadDevice)) {
                continue;
            }
            if (i == 1) {
                /* Already has a spot for first member */
                activeDevices.set(i++, dev);
            } else {
                /* Extend list with other members */
                activeDevices.add(dev);
            }
        }
        return activeDevices;
    }

    void connectSet(BluetoothDevice device) {
        LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
        if (descriptor == null) {
            Log.e(TAG, "connectSet: No valid descriptor for device: " + device);
            return;
        }
        if (descriptor.mGroupId == LE_AUDIO_GROUP_ID_INVALID) {
            return;
        }

        if (DBG) {
            Log.d(TAG, "connect() others from group id: " + descriptor.mGroupId);
        }

        Integer setGroupId = descriptor.mGroupId;

        for (Map.Entry<BluetoothDevice, LeAudioDeviceDescriptor> entry
                : mDeviceDescriptors.entrySet()) {
            BluetoothDevice storedDevice = entry.getKey();
            descriptor = entry.getValue();
            if (device.equals(storedDevice)) {
                continue;
            }

            if (!descriptor.mGroupId.equals(setGroupId)) {
                continue;
            }

            if (DBG) {
                Log.d(TAG, "connect(): " + storedDevice);
            }

            groupMutexLock(/* isReadOnly */ true);
            try {
                LeAudioStateMachine sm = getOrCreateStateMachine(storedDevice);
                if (sm == null) {
                    Log.e(TAG, "Ignored connect request for " + storedDevice
                            + " : no state machine");
                    continue;
                }
                sm.sendMessage(LeAudioStateMachine.CONNECT);
            } finally {
                groupMutexUnlock(/* isReadOnly */ true);
            }
        }
    }

    BluetoothProfileConnectionInfo getBroadcastProfile(boolean suppressNoisyIntent) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(BluetoothProfile.LE_AUDIO_BROADCAST);
        parcel.writeBoolean(suppressNoisyIntent);
        parcel.writeInt(-1 /* mVolume */);
        parcel.writeBoolean(true /* mIsLeOutput */);
        parcel.setDataPosition(0);

        BluetoothProfileConnectionInfo profileInfo =
                BluetoothProfileConnectionInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return profileInfo;
    }

    private void clearLostDevicesWhileStreaming(LeAudioGroupDescriptor descriptor) {
        groupMutexLock(/* isReadOnly */ true);
        try {
            if (DBG) {
                Log.d(TAG, "Clearing lost dev: " + descriptor.mLostLeadDeviceWhileStreaming);
            }

            LeAudioDeviceDescriptor deviceDescriptor =
                    getDeviceDescriptor(descriptor.mLostLeadDeviceWhileStreaming);
            if (deviceDescriptor == null) {
                Log.e(TAG, "clearLostDevicesWhileStreaming: No valid descriptor for device: "
                        + descriptor.mLostLeadDeviceWhileStreaming);
                return;
            }

            LeAudioStateMachine sm = deviceDescriptor.mStateMachine;
            if (sm != null) {
                LeAudioStackEvent stackEvent =
                        new LeAudioStackEvent(
                                LeAudioStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
                stackEvent.device = descriptor.mLostLeadDeviceWhileStreaming;
                stackEvent.valueInt1 = LeAudioStackEvent.CONNECTION_STATE_DISCONNECTED;
                sm.sendMessage(LeAudioStateMachine.STACK_EVENT, stackEvent);
            }
            descriptor.mLostLeadDeviceWhileStreaming = null;
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
    }

    private void handleDeviceHealthAction(BluetoothDevice device, int action) {
        if (DBG) {
            Log.d(
                    TAG,
                    "handleDeviceHealthAction: device: "
                            + device
                            + " action: "
                            + action
                            + ", not implemented");
        }
        if (action == LeAudioStackEvent.HEALTH_RECOMMENDATION_ACTION_DISABLE) {
            MetricsLogger.getInstance()
                    .count(
                            mAdapterService.isLeAudioAllowed(device)
                                    ? BluetoothProtoEnums
                                            .LE_AUDIO_ALLOWLIST_DEVICE_HEALTH_STATUS_BAD
                                    : BluetoothProtoEnums
                                            .LE_AUDIO_NONALLOWLIST_DEVICE_HEALTH_STATUS_BAD,
                            1);
        }
    }

    private void handleGroupHealthAction(int groupId, int action) {
        if (DBG) {
            Log.d(
                    TAG,
                    "handleGroupHealthAction: groupId: "
                            + groupId
                            + " action: "
                            + action
                            + ", not implemented");
        }
        BluetoothDevice device = getLeadDeviceForTheGroup(groupId);
        switch (action) {
            case LeAudioStackEvent.HEALTH_RECOMMENDATION_ACTION_DISABLE:
                MetricsLogger.getInstance()
                        .count(
                                mAdapterService.isLeAudioAllowed(device)
                                        ? BluetoothProtoEnums
                                                .LE_AUDIO_ALLOWLIST_GROUP_HEALTH_STATUS_BAD
                                        : BluetoothProtoEnums
                                                .LE_AUDIO_NONALLOWLIST_GROUP_HEALTH_STATUS_BAD,
                                1);
                break;
            case LeAudioStackEvent.HEALTH_RECOMMENDATION_ACTION_CONSIDER_DISABLING:
                MetricsLogger.getInstance()
                        .count(
                                mAdapterService.isLeAudioAllowed(device)
                                        ? BluetoothProtoEnums
                                                .LE_AUDIO_ALLOWLIST_GROUP_HEALTH_STATUS_TRENDING_BAD
                                        : BluetoothProtoEnums
                                                .LE_AUDIO_NONALLOWLIST_GROUP_HEALTH_STATUS_TRENDING_BAD,
                                1);
                break;
            case LeAudioStackEvent.HEALTH_RECOMMENDATION_ACTION_INACTIVATE_GROUP:
                if (Flags.leaudioUnicastInactivateDeviceBasedOnContext()) {
                    LeAudioGroupDescriptor groupDescriptor = getGroupDescriptor(groupId);
                    if (groupDescriptor != null && groupDescriptor.mIsActive) {
                        Log.i(
                                TAG,
                                "Group "
                                        + groupId
                                        + " is inactivated due to blocked media context");
                        groupDescriptor.mInactivatedDueToContextType = true;
                        setActiveGroupWithDevice(null, true);
                    }
                }
            default:
                break;
        }
    }

    private void handleGroupTransitToActive(int groupId) {
        groupMutexLock(/* isReadOnly */ true);
        try {
            LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
            if (descriptor == null || descriptor.mIsActive) {
                Log.e(TAG, "handleGroupTransitToActive: no descriptors for group: " + groupId
                        + " or group already active");
                return;
            }

            descriptor.mIsActive = updateActiveDevices(groupId, AUDIO_DIRECTION_NONE,
                    descriptor.mDirection, true, false, false);

            if (descriptor.mIsActive) {
                notifyGroupStatusChanged(groupId, LeAudioStackEvent.GROUP_STATUS_ACTIVE);
                updateInbandRingtoneForTheGroup(groupId);
            }
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
    }

    private void handleGroupTransitToInactive(int groupId) {
        groupMutexLock(/* isReadOnly */ true);
        try {
            LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
            if (descriptor == null || !descriptor.mIsActive) {
                Log.e(TAG, "handleGroupTransitToInactive: no descriptors for group: " + groupId
                        + " or group already inactive");
                return;
            }

            /* Group became inactive due to broadcast creation, check if input device should remain
             * connected to track streaming request on Unicast
             */
            boolean leaveConnectedInputDevice = false;
            Integer newDirections = AUDIO_DIRECTION_NONE;
            if (Flags.leaudioBroadcastAudioHandoverPolicies()
                    && (!mCreateBroadcastQueue.isEmpty()
                            || mBroadcastIdDeactivatedForUnicastTransition.isPresent())) {
                leaveConnectedInputDevice = true;
                newDirections |= AUDIO_DIRECTION_INPUT_BIT;

                /* Update Broadcast device before streaming state in handover case to avoid switch
                 * to non LE Audio device in Audio Manager e.g. Phone Speaker.
                 */
                BluetoothDevice device =
                        mAdapterService.getDeviceFromByte(
                                Utils.getBytesFromAddress("FF:FF:FF:FF:FF:FF"));
                if (!device.equals(mActiveBroadcastAudioDevice)) {
                    updateBroadcastActiveDevice(device, mActiveBroadcastAudioDevice, true);
                }
            }

            descriptor.mIsActive = false;
            updateActiveDevices(
                    groupId,
                    descriptor.mDirection,
                    newDirections,
                    descriptor.mIsActive,
                    descriptor.mHasFallbackDeviceWhenGettingInactive,
                    leaveConnectedInputDevice);
            /* Clear lost devices */
            if (DBG) Log.d(TAG, "Clear for group: " + groupId);
            descriptor.mHasFallbackDeviceWhenGettingInactive = false;
            clearLostDevicesWhileStreaming(descriptor);
            notifyGroupStatusChanged(groupId, LeAudioStackEvent.GROUP_STATUS_INACTIVE);
            updateInbandRingtoneForTheGroup(groupId);
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
    }

    private void handleSinkStreamStatusChange(int status) {
        if (DBG) {
            Log.d(TAG, "status: " + status);
        }

        /* Straming request of Unicast Sink stream should result in pausing broadcast and activating
         * Unicast group.
         *
         * When stream is suspended there should be a reverse handover. Active Unicast group should
         * become inactive and broadcast should be resumed grom paused state.
         */
        if (status == LeAudioStackEvent.STATUS_LOCAL_STREAM_REQUESTED) {
            Optional<Integer> broadcastId = getFirstNotStoppedBroadcastId();
            if (broadcastId.isEmpty() || (mBroadcastDescriptors.get(broadcastId.get()) == null)) {
                Log.e(TAG, "handleUnicastStreamStatusChange: Broadcast to Unicast handover not"
                        + " possible");
                return;
            }

            mBroadcastIdDeactivatedForUnicastTransition = Optional.of(broadcastId.get());
            pauseBroadcast(broadcastId.get());
        } else if (status == LeAudioStackEvent.STATUS_LOCAL_STREAM_SUSPENDED) {
            if (!areAllGroupsInNotActiveState()) {
                removeActiveDevice(true);
            }
        }
    }

    private void handleSourceStreamStatusChange(int status) {
        BassClientService bassClientService = getBassClientService();
        if (bassClientService == null) {
            Log.e(TAG, "handleSourceStreamStatusChange: BASS Client service is not available");

            mLeAudioNativeInterface.setUnicastMonitorMode(
                    LeAudioStackEvent.DIRECTION_SOURCE, false);
        }

        bassClientService.handleUnicastSourceStreamStatusChange(status);
    }

    private void handleUnicastStreamStatusChange(int direction, int status) {
        if (direction == LeAudioStackEvent.DIRECTION_SINK) {
            handleSinkStreamStatusChange(status);
        } else if (direction == LeAudioStackEvent.DIRECTION_SOURCE) {
            handleSourceStreamStatusChange(status);
        } else {
            Log.e(TAG, "handleUnicastStreamStatusChange: invalid direction: " + direction);
        }
    }

    private void notifyGroupStreamStatusChanged(int groupId, int groupStreamStatus) {
        if (mLeAudioCallbacks != null) {
            int n = mLeAudioCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mLeAudioCallbacks
                            .getBroadcastItem(i)
                            .onGroupStreamStatusChanged(groupId, groupStreamStatus);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mLeAudioCallbacks.finishBroadcast();
        }
    }

    @VisibleForTesting
    void handleGroupIdleDuringCall() {
        if (mHfpHandoverDevice == null) {
            if (DBG) {
                Log.d(TAG, "There is no HFP handover");
            }
            return;
        }
        HeadsetService headsetService = mServiceFactory.getHeadsetService();
        if (headsetService == null) {
            if (DBG) {
                Log.d(TAG, "There is no HFP service available");
            }
            return;
        }

        BluetoothDevice activeHfpDevice = headsetService.getActiveDevice();
        if (activeHfpDevice == null) {
            if (DBG) {
                Log.d(TAG, "Make " + mHfpHandoverDevice + " active again ");
            }
            headsetService.setActiveDevice(mHfpHandoverDevice);
        } else {
            if (DBG) {
                Log.d(TAG, "Connect audio to " + activeHfpDevice);
            }
            headsetService.connectAudio();
        }
        mHfpHandoverDevice = null;
    }

    void updateInbandRingtoneForTheGroup(int groupId) {
        if (!mLeAudioInbandRingtoneSupportedByPlatform) {
            if (DBG) {
                Log.d(TAG, "Platform does not support inband ringtone");
            }
            return;
        }

        groupMutexLock(/* isReadOnly */ true);
        try {
            LeAudioGroupDescriptor groupDescriptor = getGroupDescriptor(groupId);
            if (groupDescriptor == null) {
                Log.e(TAG, "group descriptor for " + groupId + " does not exist");
                return;
            }

            boolean ringtoneContextAvailable =
                    ((groupDescriptor.mAvailableContexts
                            & BluetoothLeAudio.CONTEXT_TYPE_RINGTONE) != 0);
            if (DBG) {
                Log.d(TAG, "groupId active: " + groupDescriptor.mIsActive
                        + " ringtone supported: " + ringtoneContextAvailable);
            }

            boolean isRingtoneEnabled = (groupDescriptor.mIsActive && ringtoneContextAvailable);

            if (DBG) {
                Log.d(TAG, "updateInbandRingtoneForTheGroup old: "
                        + groupDescriptor.mInbandRingtoneEnabled + " new: " + isRingtoneEnabled);
            }

            /* If at least one device from the group removes the Ringtone from available
            * context types, the inband ringtone will be removed
            */
            groupDescriptor.mInbandRingtoneEnabled = isRingtoneEnabled;
            TbsService tbsService = getTbsService();
            if (tbsService == null) {
                Log.w(TAG, "updateInbandRingtoneForTheGroup, tbsService not available");
                return;
            }

            for (Map.Entry<BluetoothDevice, LeAudioDeviceDescriptor> entry :
                                                    mDeviceDescriptors.entrySet()) {
                if (entry.getValue().mGroupId == groupId) {
                    BluetoothDevice device = entry.getKey();
                    LeAudioDeviceDescriptor deviceDescriptor = entry.getValue();
                    Log.i(TAG, "updateInbandRingtoneForTheGroup, setting inband ringtone to: "
                                + groupDescriptor.mInbandRingtoneEnabled + " for " + device
                                + " " + deviceDescriptor.mDevInbandRingtoneEnabled);
                    if (groupDescriptor.mInbandRingtoneEnabled
                                    == deviceDescriptor.mDevInbandRingtoneEnabled) {
                        if (DBG) {
                            Log.d(TAG, "Device " + device + " has already set inband ringtone to "
                                            + groupDescriptor.mInbandRingtoneEnabled);
                        }
                        continue;
                    }

                    deviceDescriptor.mDevInbandRingtoneEnabled =
                            groupDescriptor.mInbandRingtoneEnabled;
                    if (deviceDescriptor.mDevInbandRingtoneEnabled) {
                        tbsService.setInbandRingtoneSupport(device);
                    } else {
                        tbsService.clearInbandRingtoneSupport(device);
                    }
                }
            }
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
    }

    void stopAudioServersBackgroundScan() {
        if (DBG) {
            Log.d(TAG, "stopAudioServersBackgroundScan");
        }

        if (mAudioServersScanner == null || mScanCallback == null) {
            if (DBG) {
                Log.d(TAG, "stopAudioServersBackgroundScan: already stopped");
            }
            return;
        }

        try {
            mAudioServersScanner.stopScan(mScanCallback);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Fail to stop scanner, consider it stopped", e);
        }

        /* Callback is the indicator for scanning being enabled */
        mScanCallback = null;
    }

    void startAudioServersBackgroundScan(boolean retry) {
        if (DBG) {
            Log.d(TAG, "startAudioServersBackgroundScan, retry: " + retry);
        }

        if (!isScannerNeeded()) {
            return;
        }

        if (mAudioServersScanner == null) {
            mAudioServersScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
            if (mAudioServersScanner == null) {
                Log.e(TAG, "startAudioServersBackgroundScan: Could not get scanner");
                return;
            }
        }

        if (!retry) {
            if (mScanCallback != null) {
                if (DBG) {
                    Log.d(TAG, "startAudioServersBackgroundScan: Scanning already enabled");
                }
                return;
            }
            mScanCallback = new AudioServerScanCallback();
        }

        /* Filter we are building here will not match to anything.
         * Eventually we should be able to start scan from native when
         * b/276350722 is done
         */
        byte[] serviceData = new byte[]{0x11};

        ArrayList filterList = new ArrayList<ScanFilter>();
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceData(BluetoothUuid.LE_AUDIO, serviceData)
                .build();
        filterList.add(filter);

        ScanSettings settings = new ScanSettings.Builder()
                .setLegacy(false)
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();

        try {
            mAudioServersScanner.startScan(filterList, settings, mScanCallback);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Fail to start scanner, consider it stopped", e);
            mScanCallback = null;
        }
    }

    void transitionFromBroadcastToUnicast() {
        if (mUnicastGroupIdDeactivatedForBroadcastTransition == LE_AUDIO_GROUP_ID_INVALID) {
            Log.d(TAG, "No deactivated group due for broadcast transmission");
            return;
        }

        if (mQueuedInCallValue.isPresent()) {
            mLeAudioNativeInterface.setInCall(mQueuedInCallValue.get());
            mQueuedInCallValue = Optional.empty();
        }

        BluetoothDevice unicastDevice =
                getLeadDeviceForTheGroup(mUnicastGroupIdDeactivatedForBroadcastTransition);
        if (unicastDevice == null) {
            /* All devices from group were disconnected in meantime */
            Log.w(
                    TAG,
                    "transitionFromBroadcastToUnicast: No valid unicast device for group ID: "
                            + mUnicastGroupIdDeactivatedForBroadcastTransition);
            updateFallbackUnicastGroupIdForBroadcast(LE_AUDIO_GROUP_ID_INVALID);
            updateBroadcastActiveDevice(null, mActiveBroadcastAudioDevice, false);
            return;
        }

        if (DBG) {
            Log.d(TAG, "Transitionaing to Unicast stream for group: "
                    + mUnicastGroupIdDeactivatedForBroadcastTransition
                    + ", with device: "
                    + unicastDevice);
        }

        updateFallbackUnicastGroupIdForBroadcast(LE_AUDIO_GROUP_ID_INVALID);
        setActiveDevice(unicastDevice);
    }

    void clearBroadcastTimeoutCallback() {
        if (mHandler == null) {
            Log.e(TAG, "No callback handler");
            return;
        }

        /* Timeout callback already cleared */
        if (mDialingOutTimeoutEvent == null) {
            return;
        }

        mHandler.removeCallbacks(mDialingOutTimeoutEvent);
        mDialingOutTimeoutEvent = null;
    }

    // Suppressed since this is part of a local process
    @SuppressLint("AndroidFrameworkRequiresPermission")
    void messageFromNative(LeAudioStackEvent stackEvent) {
        Log.d(TAG, "Message from native: " + stackEvent);
        BluetoothDevice device = stackEvent.device;

        if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED) {
            // Some events require device state machine
            groupMutexLock(/* isReadOnly */ true);
            try {
                LeAudioDeviceDescriptor deviceDescriptor = getDeviceDescriptor(device);
                if (deviceDescriptor == null) {
                    Log.e(TAG, "messageFromNative: No valid descriptor for device: " + device);
                    return;
                }

                LeAudioStateMachine sm = deviceDescriptor.mStateMachine;
                if (sm != null) {
                    /*
                     * To improve scenario when lead Le Audio device is disconnected for the
                     * streaming group, while there are still other devices streaming,
                     * LeAudioService will not notify audio framework or other users about
                     * Le Audio lead device disconnection. Instead we try to reconnect under
                     * the hood and keep using lead device as a audio device indetifier in
                     * the audio framework in order to not stop the stream.
                     */
                    int groupId = deviceDescriptor.mGroupId;
                    LeAudioGroupDescriptor descriptor = mGroupDescriptors.get(groupId);
                    switch (stackEvent.valueInt1) {
                        case LeAudioStackEvent.CONNECTION_STATE_DISCONNECTING:
                        case LeAudioStackEvent.CONNECTION_STATE_DISCONNECTED:
                            deviceDescriptor.mAclConnected = false;
                            startAudioServersBackgroundScan(/* retry = */ false);

                            boolean disconnectDueToUnbond =
                                    (BluetoothDevice.BOND_NONE
                                            == mAdapterService.getBondState(device));
                            if (descriptor != null && (Objects.equals(device,
                                    mActiveAudioOutDevice)
                                    || Objects.equals(device, mActiveAudioInDevice))
                                    && (getConnectedPeerDevices(groupId).size() > 1)
                                    && !disconnectDueToUnbond) {

                                if (DBG) Log.d(TAG, "Adding to lost devices : " + device);
                                descriptor.mLostLeadDeviceWhileStreaming = device;
                                return;
                            }
                            break;
                        case LeAudioStackEvent.CONNECTION_STATE_CONNECTED:
                        case LeAudioStackEvent.CONNECTION_STATE_CONNECTING:
                            deviceDescriptor.mAclConnected = true;
                            if (descriptor != null
                                    && Objects.equals(
                                            descriptor.mLostLeadDeviceWhileStreaming,
                                            device)) {
                                if (DBG) {
                                    Log.d(TAG, "Removing from lost devices : " + device);
                                }
                                descriptor.mLostLeadDeviceWhileStreaming = null;
                                /* Try to connect other devices from the group */
                                connectSet(device);
                            }
                            break;
                    }
                } else {
                    /* state machine does not exist yet */
                    switch (stackEvent.valueInt1) {
                        case LeAudioStackEvent.CONNECTION_STATE_CONNECTED:
                        case LeAudioStackEvent.CONNECTION_STATE_CONNECTING:
                            deviceDescriptor.mAclConnected = true;
                            sm = getOrCreateStateMachine(device);
                            /* Incoming connection try to connect other devices from the group */
                            connectSet(device);
                            break;
                        default:
                            break;
                    }

                    if (sm == null) {
                        Log.e(TAG, "Cannot process stack event: no state machine: " + stackEvent);
                        return;
                    }
                }

                sm.sendMessage(LeAudioStateMachine.STACK_EVENT, stackEvent);
                return;
            } finally {
                groupMutexUnlock(/* isReadOnly */ true);
            }
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_GROUP_NODE_STATUS_CHANGED) {
            int groupId = stackEvent.valueInt1;
            int nodeStatus = stackEvent.valueInt2;

            Objects.requireNonNull(stackEvent.device,
                    "Device should never be null, event: " + stackEvent);

            switch (nodeStatus) {
                case LeAudioStackEvent.GROUP_NODE_ADDED:
                    handleGroupNodeAdded(device, groupId);
                    break;
                case LeAudioStackEvent.GROUP_NODE_REMOVED:
                    handleGroupNodeRemoved(device, groupId);
                    break;
                default:
                    break;
            }
        } else if (stackEvent.type
                == LeAudioStackEvent.EVENT_TYPE_AUDIO_LOCAL_CODEC_CONFIG_CAPA_CHANGED) {
            mInputLocalCodecCapabilities = stackEvent.valueCodecList1;
            mOutputLocalCodecCapabilities = stackEvent.valueCodecList2;
        } else if (stackEvent.type
                == LeAudioStackEvent.EVENT_TYPE_AUDIO_GROUP_SELECTABLE_CODEC_CONFIG_CHANGED) {
            int groupId = stackEvent.valueInt1;
            LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
            if (descriptor == null) {
                Log.e(TAG, " Group not found " + groupId);
                return;
            }

            descriptor.mInputSelectableConfig = stackEvent.valueCodecList1;
            descriptor.mOutputSelectableConfig = stackEvent.valueCodecList2;

        } else if (stackEvent.type
                == LeAudioStackEvent.EVENT_TYPE_AUDIO_GROUP_CURRENT_CODEC_CONFIG_CHANGED) {
            int groupId = stackEvent.valueInt1;
            LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
            if (descriptor == null) {
                Log.e(TAG, " Group not found " + groupId);
                return;
            }

            BluetoothLeAudioCodecStatus status =
                    new BluetoothLeAudioCodecStatus(
                            stackEvent.valueCodec1,
                            stackEvent.valueCodec2,
                            mInputLocalCodecCapabilities,
                            mOutputLocalCodecCapabilities,
                            descriptor.mInputSelectableConfig,
                            descriptor.mOutputSelectableConfig);

            if (DBG) {
                if (descriptor.mCodecStatus != null) {
                    Log.d(TAG, " Replacing codec status for group: " + groupId);
                } else {
                    Log.d(TAG, " New codec status for group: " + groupId);
                }
            }

            descriptor.mCodecStatus = status;
            notifyUnicastCodecConfigChanged(groupId, status);
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_AUDIO_CONF_CHANGED) {
            int direction = stackEvent.valueInt1;
            int groupId = stackEvent.valueInt2;
            int snk_audio_location = stackEvent.valueInt3;
            int src_audio_location = stackEvent.valueInt4;
            int available_contexts = stackEvent.valueInt5;

            groupMutexLock(/* isReadOnly */ true);
            try {
                LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
                if (descriptor != null) {
                    if (descriptor.mIsActive) {
                        descriptor.mIsActive =
                                updateActiveDevices(groupId, descriptor.mDirection, direction,
                                descriptor.mIsActive, false, false);
                        if (!descriptor.mIsActive) {
                            notifyGroupStatusChanged(groupId,
                                    BluetoothLeAudio.GROUP_STATUS_INACTIVE);
                        }
                    }
                    descriptor.mDirection = direction;
                    descriptor.mAvailableContexts = available_contexts;
                    updateInbandRingtoneForTheGroup(groupId);

                    boolean mediaIsAvailable =
                            ((descriptor.mAvailableContexts & BluetoothLeAudio.CONTEXT_TYPE_MEDIA)
                                    != 0);

                    if (descriptor.mInactivatedDueToContextType && mediaIsAvailable) {
                        Log.i(TAG, " Media context type again available for " + groupId);
                        setActiveGroupWithDevice(getLeadDeviceForTheGroup(groupId), true);
                    }
                } else {
                    Log.e(TAG, "messageFromNative: no descriptors for group: " + groupId);
                }
            } finally {
                groupMutexUnlock(/* isReadOnly */ true);
            }
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_SINK_AUDIO_LOCATION_AVAILABLE) {
            Objects.requireNonNull(stackEvent.device,
                    "Device should never be null, event: " + stackEvent);

            int sink_audio_location = stackEvent.valueInt1;

            LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
            if (descriptor == null) {
                Log.e(TAG, "messageFromNative: No valid descriptor for device: " + device);
                return;
            }

            descriptor.mSinkAudioLocation = sink_audio_location;

            if (DBG) {
                Log.i(TAG, "EVENT_TYPE_SINK_AUDIO_LOCATION_AVAILABLE:" + device
                        + " audio location:" + sink_audio_location);
            }
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_GROUP_STATUS_CHANGED) {
            int groupId = stackEvent.valueInt1;
            int groupStatus = stackEvent.valueInt2;

            switch (groupStatus) {
                case LeAudioStackEvent.GROUP_STATUS_ACTIVE: {
                    handleGroupTransitToActive(groupId);

                    /* Clear possible exposed broadcast device after activating unicast */
                    if (mActiveBroadcastAudioDevice != null) {
                        updateBroadcastActiveDevice(null, mActiveBroadcastAudioDevice, true);
                    }
                    break;
                }
                case LeAudioStackEvent.GROUP_STATUS_INACTIVE: {
                    handleGroupTransitToInactive(groupId);

                    /* Check if broadcast was deactivated due to unicast */
                    if (mBroadcastIdDeactivatedForUnicastTransition.isPresent()) {
                        updateFallbackUnicastGroupIdForBroadcast(groupId);
                        mQueuedInCallValue = Optional.empty();
                        startBroadcast(mBroadcastIdDeactivatedForUnicastTransition.get());
                        mBroadcastIdDeactivatedForUnicastTransition = Optional.empty();
                    }

                    if (!mCreateBroadcastQueue.isEmpty()) {
                        updateFallbackUnicastGroupIdForBroadcast(groupId);
                        BluetoothLeBroadcastSettings settings = mCreateBroadcastQueue.remove();
                        createBroadcast(settings);
                    }
                    break;
                }
                case LeAudioStackEvent.GROUP_STATUS_TURNED_IDLE_DURING_CALL: {
                    handleGroupIdleDuringCall();
                    break;
                }
                default:
                    break;
            }
        } else if (stackEvent.type
                == LeAudioStackEvent.EVENT_TYPE_HEALTH_BASED_DEV_RECOMMENDATION) {
            handleDeviceHealthAction(stackEvent.device, stackEvent.valueInt1);
        } else if (stackEvent.type
                == LeAudioStackEvent.EVENT_TYPE_HEALTH_BASED_GROUP_RECOMMENDATION) {
            handleGroupHealthAction(stackEvent.valueInt1, stackEvent.valueInt2);
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_BROADCAST_CREATED) {
            int broadcastId = stackEvent.valueInt1;
            boolean success = stackEvent.valueBool1;
            if (success) {
                Log.d(TAG, "Broadcast broadcastId: " + broadcastId + " created.");
                mBroadcastDescriptors.put(broadcastId, new LeAudioBroadcastDescriptor());
                notifyBroadcastStarted(broadcastId, BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);
                // Start sending the actual stream
                startBroadcast(broadcastId);

            } else {
                // TODO: Improve reason reporting or extend the native stack event with reason code
                Log.e(
                        TAG,
                        "EVENT_TYPE_BROADCAST_CREATED: Failed to create broadcast: " + broadcastId);

                /* Disconnect Broadcast device which was connected to avoid non LE Audio sound
                 * leak in handover scenario.
                 */
                if ((mUnicastGroupIdDeactivatedForBroadcastTransition != LE_AUDIO_GROUP_ID_INVALID)
                        && mCreateBroadcastQueue.isEmpty()
                        && (!Objects.equals(device, mActiveBroadcastAudioDevice))) {
                    clearBroadcastTimeoutCallback();
                    updateBroadcastActiveDevice(null, mActiveBroadcastAudioDevice, false);
                }

                notifyBroadcastStartFailed(broadcastId, BluetoothStatusCodes.ERROR_UNKNOWN);
            }

            mAwaitingBroadcastCreateResponse = false;

            // In case if there were additional calls to create broadcast
            if (!mCreateBroadcastQueue.isEmpty()) {
                BluetoothLeBroadcastSettings settings = mCreateBroadcastQueue.remove();
                createBroadcast(settings);
            }

        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_BROADCAST_DESTROYED) {
            Integer broadcastId = stackEvent.valueInt1;
            LeAudioBroadcastDescriptor descriptor = mBroadcastDescriptors.get(broadcastId);
            if (descriptor == null) {
                Log.e(
                        TAG,
                        "EVENT_TYPE_BROADCAST_DESTROYED: No valid descriptor for broadcastId: "
                                + broadcastId);
            } else {
                mBroadcastDescriptors.remove(broadcastId);
            }

            // TODO: Improve reason reporting or extend the native stack event with reason code
            notifyOnBroadcastStopped(broadcastId, BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);
            BassClientService bassClientService = getBassClientService();
            if (bassClientService != null) {
                bassClientService.stopReceiversSourceSynchronization(broadcastId);
            }
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_BROADCAST_STATE) {
            int broadcastId = stackEvent.valueInt1;
            int state = stackEvent.valueInt2;
            int previousState;

            LeAudioBroadcastDescriptor descriptor = mBroadcastDescriptors.get(broadcastId);
            if (descriptor == null) {
                Log.e(TAG, "EVENT_TYPE_BROADCAST_STATE: No valid descriptor for broadcastId: "
                        + broadcastId);
                return;
            }

            /* Request broadcast details if not known yet */
            if (!descriptor.mRequestedForDetails) {
                mLeAudioBroadcasterNativeInterface.getBroadcastMetadata(broadcastId);
                descriptor.mRequestedForDetails = true;
            }
            previousState = descriptor.mState;
            descriptor.mState = state;
            BassClientService bassClientService = getBassClientService();

            switch (descriptor.mState) {
                case LeAudioStackEvent.BROADCAST_STATE_STOPPED:
                    if (DBG) Log.d(TAG, "Broadcast broadcastId: " + broadcastId + " stopped.");

                    // Playback stopped
                    notifyPlaybackStopped(broadcastId,
                            BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);


                    /* Restore the Unicast stream from before the Broadcast was started. */
                    if (mUnicastGroupIdDeactivatedForBroadcastTransition
                            != LE_AUDIO_GROUP_ID_INVALID) {
                        transitionFromBroadcastToUnicast();
                    } else {
                        // Notify audio manager
                        if (mBroadcastDescriptors.values().stream()
                                .noneMatch(
                                        d ->
                                                d.mState.equals(
                                                        LeAudioStackEvent
                                                                .BROADCAST_STATE_STREAMING))) {
                            updateBroadcastActiveDevice(null, mActiveBroadcastAudioDevice, false);
                        }
                    }
                    destroyBroadcast(broadcastId);
                    break;
                case LeAudioStackEvent.BROADCAST_STATE_CONFIGURING:
                    if (DBG) Log.d(TAG, "Broadcast broadcastId: " + broadcastId + " configuring.");
                    break;
                case LeAudioStackEvent.BROADCAST_STATE_PAUSED:
                    if (DBG) Log.d(TAG, "Broadcast broadcastId: " + broadcastId + " paused.");

                    /* Stop here if Broadcast was not in Streaming state before */
                    if (previousState != LeAudioStackEvent.BROADCAST_STATE_STREAMING) {
                        return;
                    }

                    // Playback paused
                    notifyPlaybackStopped(broadcastId,
                            BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST);

                    if (bassClientService != null) {
                        bassClientService.suspendReceiversSourceSynchronization(broadcastId);
                    }

                    /* Restore the Unicast stream from before the Broadcast was started. */
                    if (mUnicastGroupIdDeactivatedForBroadcastTransition
                            != LE_AUDIO_GROUP_ID_INVALID) {
                        transitionFromBroadcastToUnicast();
                    } else {
                        // Notify audio manager
                        if (mBroadcastDescriptors.values().stream()
                                .noneMatch(
                                        d ->
                                                d.mState.equals(
                                                        LeAudioStackEvent
                                                                .BROADCAST_STATE_STREAMING))) {
                            updateBroadcastActiveDevice(null, mActiveBroadcastAudioDevice, false);
                        }
                    }
                    break;
                case LeAudioStackEvent.BROADCAST_STATE_STOPPING:
                    if (DBG) Log.d(TAG, "Broadcast broadcastId: " + broadcastId + " stopping.");
                    break;
                case LeAudioStackEvent.BROADCAST_STATE_STREAMING:
                    if (DBG) Log.d(TAG, "Broadcast broadcastId: " + broadcastId + " streaming.");

                    // Stream resumed
                    notifyPlaybackStarted(broadcastId,
                            BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST);

                    clearBroadcastTimeoutCallback();

                    if (previousState == LeAudioStackEvent.BROADCAST_STATE_PAUSED) {
                        if (bassClientService != null) {
                            bassClientService.resumeReceiversSourceSynchronization();
                        }
                    }

                    // Notify audio manager
                    if (mBroadcastDescriptors.values().stream()
                            .anyMatch(
                                    d ->
                                            d.mState.equals(
                                                    LeAudioStackEvent.BROADCAST_STATE_STREAMING))) {
                        if (!Objects.equals(device, mActiveBroadcastAudioDevice)) {
                            updateBroadcastActiveDevice(device, mActiveBroadcastAudioDevice, true);
                        }
                    }
                    break;
                default:
                    Log.e(TAG, "Invalid state of broadcast: " + descriptor.mState);
                    break;
            }

            // Notify broadcast assistant
            if (Flags.leaudioBroadcastAudioHandoverPolicies()) {
                if (bassClientService != null) {
                    bassClientService.notifyBroadcastStateChanged(descriptor.mState, broadcastId);
                }
            }
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_BROADCAST_METADATA_CHANGED) {
            int broadcastId = stackEvent.valueInt1;
            if (stackEvent.broadcastMetadata == null) {
                Log.e(TAG, "Missing Broadcast metadata for broadcastId: " + broadcastId);
            } else {
                LeAudioBroadcastDescriptor descriptor = mBroadcastDescriptors.get(broadcastId);
                if (descriptor == null) {
                    Log.e(TAG, "EVENT_TYPE_BROADCAST_METADATA_CHANGED: No valid descriptor for "
                            + "broadcastId: " + broadcastId);
                    return;
                }
                descriptor.mMetadata = stackEvent.broadcastMetadata;
                notifyBroadcastMetadataChanged(broadcastId, stackEvent.broadcastMetadata);
            }
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_NATIVE_INITIALIZED) {
            mLeAudioNativeIsInitialized = true;
            for (Map.Entry<ParcelUuid, Pair<Integer, Integer>> entry :
                    ContentControlIdKeeper.getUuidToCcidContextPairMap().entrySet()) {
                ParcelUuid userUuid = entry.getKey();
                Pair<Integer, Integer> ccidInformation = entry.getValue();
                setCcidInformation(userUuid, ccidInformation.first, ccidInformation.second);
            }
            if (!mTmapStarted) {
                mTmapStarted = registerTmap();
            }
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_UNICAST_MONITOR_MODE_STATUS) {
            handleUnicastStreamStatusChange(stackEvent.valueInt1, stackEvent.valueInt2);
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_GROUP_STREAM_STATUS_CHANGED) {
            notifyGroupStreamStatusChanged(stackEvent.valueInt1, stackEvent.valueInt2);
        }
    }

    private LeAudioStateMachine getOrCreateStateMachine(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "getOrCreateStateMachine failed: device cannot be null");
            return null;
        }

        LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
        if (descriptor == null) {
            Log.e(TAG, "getOrCreateStateMachine: No valid descriptor for device: " + device);
            return null;
        }

        LeAudioStateMachine sm = descriptor.mStateMachine;
        if (sm != null) {
            return sm;
        }

        if (DBG) {
            Log.d(TAG, "Creating a new state machine for " + device);
        }

        sm =
                LeAudioStateMachine.make(
                        device, this, mLeAudioNativeInterface, mStateMachinesThread.getLooper());
        descriptor.mStateMachine = sm;
        return sm;
    }

    public void handleBondStateChanged(BluetoothDevice device, int fromState, int toState) {
        mHandler.post(() -> bondStateChanged(device, toState));
    }

    /**
     * Process a change in the bonding state for a device.
     *
     * @param device the device whose bonding state has changed
     * @param bondState the new bond state for the device. Possible values are:
     * {@link BluetoothDevice#BOND_NONE},
     * {@link BluetoothDevice#BOND_BONDING},
     * {@link BluetoothDevice#BOND_BONDED}.
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

        groupMutexLock(/* isReadOnly */ true);
        try {
            try {
                LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
                if (descriptor == null) {
                    Log.e(TAG, "bondStateChanged: No valid descriptor for device: " + device);
                    return;
                }

                if (descriptor.mGroupId != LE_AUDIO_GROUP_ID_INVALID) {
                    /* In case device is still in the group, let's remove it */
                    mLeAudioNativeInterface.groupRemoveNode(descriptor.mGroupId, device);
                }

                descriptor.mGroupId = LE_AUDIO_GROUP_ID_INVALID;
                descriptor.mSinkAudioLocation = BluetoothLeAudio.AUDIO_LOCATION_INVALID;
                descriptor.mDirection = AUDIO_DIRECTION_NONE;

                LeAudioStateMachine sm = descriptor.mStateMachine;
                if (sm == null) {
                    return;
                }
                if (sm.getConnectionState() != BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w(TAG, "Device is not disconnected yet.");
                    disconnect(device);
                    return;
                }
            } finally {
                // Reduce size of critical section when this feature is enabled
                if (Flags.leaudioApiSynchronizedBlockFix()) {
                    groupMutexUnlock(/* isReadOnly */ true);
                }
            }
            removeStateMachine(device);
            removeAuthorizationInfoForRelatedProfiles(device);
        } finally {
            if (!Flags.leaudioApiSynchronizedBlockFix()) {
                groupMutexUnlock(/* isReadOnly */ true);
            }
        }
    }

    private void removeStateMachine(BluetoothDevice device) {
        groupMutexLock(/* isReadOnly */ true);
        try {
            try {
                LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
                if (descriptor == null) {
                    Log.e(TAG, "removeStateMachine: No valid descriptor for device: " + device);
                    return;
                }

                LeAudioStateMachine sm = descriptor.mStateMachine;
                if (sm == null) {
                    Log.w(
                            TAG,
                            "removeStateMachine: device "
                                    + device
                                    + " does not have a state machine");
                    return;
                }
                Log.i(TAG, "removeStateMachine: removing state machine for device: " + device);
                sm.quit();
                sm.cleanup();
                descriptor.mStateMachine = null;
            } finally {
                if (Flags.leaudioApiSynchronizedBlockFix()) {
                    // Upgrade to write lock
                    groupMutexUnlock(/* isReadOnly */ true);
                    groupMutexLock(/* isReadOnly */ false);
                }
            }
            mDeviceDescriptors.remove(device);
            if (!isScannerNeeded()) {
                stopAudioServersBackgroundScan();
            }
        } finally {
            /* Note, when flag is disabled, isReadyOnly param have no impact */
            groupMutexUnlock(/* isReadOnly */ false);
        }
    }

    @VisibleForTesting
    List<BluetoothDevice> getConnectedPeerDevices(int groupId) {
        List<BluetoothDevice> result = new ArrayList<>();
        for (BluetoothDevice peerDevice : getConnectedDevices()) {
            if (getGroupId(peerDevice) == groupId) {
                result.add(peerDevice);
            }
        }
        return result;
    }

    /**
     * Process a change for connection of a device.
     */
    public synchronized void deviceConnected(BluetoothDevice device) {
        LeAudioDeviceDescriptor deviceDescriptor = getDeviceDescriptor(device);
        if (deviceDescriptor == null) {
            Log.e(TAG, "deviceConnected: No valid descriptor for device: " + device);
            return;
        }

        if (deviceDescriptor.mGroupId == LE_AUDIO_GROUP_ID_INVALID
                || getConnectedPeerDevices(deviceDescriptor.mGroupId).size() == 1) {
            // Log LE Audio connection event if we are the first device in a set
            // Or when the GroupId has not been found
            // MetricsLogger.logProfileConnectionEvent(
            //         BluetoothMetricsProto.ProfileId.LE_AUDIO);
        }

        LeAudioGroupDescriptor descriptor = getGroupDescriptor(deviceDescriptor.mGroupId);
        if (descriptor != null) {
            descriptor.mIsConnected = true;
        } else {
            Log.e(TAG, "deviceConnected: no descriptors for group: "
                    + deviceDescriptor.mGroupId);
        }

        if (!isScannerNeeded()) {
            stopAudioServersBackgroundScan();
        }
    }

    /** Process a change for disconnection of a device. */
    synchronized void deviceDisconnectedV2(BluetoothDevice device, boolean hasFallbackDevice) {
        if (DBG) {
            Log.d(TAG, "deviceDisconnectedV2 " + device);
        }

        int groupId = LE_AUDIO_GROUP_ID_INVALID;
        groupMutexLock(/* isReadOnly */ true);
        try {
            LeAudioDeviceDescriptor deviceDescriptor = getDeviceDescriptor(device);
            if (deviceDescriptor == null) {
                Log.e(TAG, "deviceDisconnected: No valid descriptor for device: " + device);
                return;
            }
            groupId = deviceDescriptor.mGroupId;
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }

        int bondState = mAdapterService.getBondState(device);
        if (bondState == BluetoothDevice.BOND_NONE) {
            if (DBG) {
                Log.d(TAG, device + " is unbond. Remove state machine");
            }

            removeStateMachine(device);
            removeAuthorizationInfoForRelatedProfiles(device);
        }

        if (!isScannerNeeded()) {
            stopAudioServersBackgroundScan();
        }

        groupMutexLock(/* isReadOnly */ true);
        try {
            LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
            if (descriptor == null) {
                Log.e(TAG, "deviceDisconnected: no descriptors for group: " + groupId);
                return;
            }

            List<BluetoothDevice> connectedDevices = getConnectedPeerDevices(groupId);
            /* Let's check if the last connected device is really connected */
            if (connectedDevices.size() == 1
                    && Objects.equals(
                            connectedDevices.get(0), descriptor.mLostLeadDeviceWhileStreaming)) {
                clearLostDevicesWhileStreaming(descriptor);
                return;
            }

            if (getConnectedPeerDevices(groupId).isEmpty()) {
                descriptor.mIsConnected = false;
                if (descriptor.mIsActive) {
                    /* Notify Native layer */
                    removeActiveDevice(hasFallbackDevice);
                    descriptor.mIsActive = false;
                    /* Update audio framework */
                    updateActiveDevices(
                            groupId,
                            descriptor.mDirection,
                            descriptor.mDirection,
                            descriptor.mIsActive,
                            hasFallbackDevice,
                            false);
                    return;
                }
            }

            if (descriptor.mIsActive
                    || Objects.equals(mActiveAudioOutDevice, device)
                    || Objects.equals(mActiveAudioInDevice, device)) {
                updateActiveDevices(
                        groupId,
                        descriptor.mDirection,
                        descriptor.mDirection,
                        descriptor.mIsActive,
                        hasFallbackDevice,
                        false);
            }
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
    }

    /**
     * Process a change for disconnection of a device.
     */
    public synchronized void deviceDisconnected(BluetoothDevice device, boolean hasFallbackDevice) {
        if (Flags.leaudioApiSynchronizedBlockFix()) {
            deviceDisconnectedV2(device, hasFallbackDevice);
            return;
        }

        if (DBG) {
            Log.d(TAG, "deviceDisconnected " + device);
        }

        groupMutexLock(/* isReadOnly */ true);
        try {
            LeAudioDeviceDescriptor deviceDescriptor = getDeviceDescriptor(device);
            if (deviceDescriptor == null) {
                Log.e(TAG, "deviceDisconnected: No valid descriptor for device: " + device);
                return;
            }

            int bondState = mAdapterService.getBondState(device);
            if (bondState == BluetoothDevice.BOND_NONE) {
                if (DBG) {
                    Log.d(TAG, device + " is unbond. Remove state machine");
                }
                removeStateMachine(device);
                removeAuthorizationInfoForRelatedProfiles(device);
            }

            if (!isScannerNeeded()) {
                stopAudioServersBackgroundScan();
            }

            LeAudioGroupDescriptor descriptor = getGroupDescriptor(deviceDescriptor.mGroupId);
            if (descriptor == null) {
                Log.e(TAG, "deviceDisconnected: no descriptors for group: "
                        + deviceDescriptor.mGroupId);
                return;
            }

            List<BluetoothDevice> connectedDevices =
                    getConnectedPeerDevices(deviceDescriptor.mGroupId);
            /* Let's check if the last connected device is really connected */
            if (connectedDevices.size() == 1 && Objects.equals(
                    connectedDevices.get(0), descriptor.mLostLeadDeviceWhileStreaming)) {
                clearLostDevicesWhileStreaming(descriptor);
                return;
            }

            if (getConnectedPeerDevices(deviceDescriptor.mGroupId).isEmpty()) {
                descriptor.mIsConnected = false;
                if (descriptor.mIsActive) {
                    /* Notify Native layer */
                    removeActiveDevice(hasFallbackDevice);
                    descriptor.mIsActive = false;
                    /* Update audio framework */
                    updateActiveDevices(deviceDescriptor.mGroupId,
                            descriptor.mDirection,
                            descriptor.mDirection,
                            descriptor.mIsActive,
                            hasFallbackDevice,
                            false);
                    return;
                }
            }

            if (descriptor.mIsActive || Objects.equals(mActiveAudioOutDevice, device)
                    || Objects.equals(mActiveAudioInDevice, device)) {
                updateActiveDevices(deviceDescriptor.mGroupId,
                        descriptor.mDirection,
                        descriptor.mDirection,
                        descriptor.mIsActive,
                        hasFallbackDevice,
                        false);
            }
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
    }

    /**
     * Check whether can connect to a peer device.
     * The check considers a number of factors during the evaluation.
     *
     * @param device the peer device to connect to
     * @return true if connection is allowed, otherwise false
     */
    public boolean okToConnect(BluetoothDevice device) {
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

    /**
     * Get device audio location.
     * @param device LE Audio capable device
     * @return the sink audioi location that this device currently exposed
     */
    public int getAudioLocation(BluetoothDevice device) {
        if (device == null) {
            return BluetoothLeAudio.AUDIO_LOCATION_INVALID;
        }

        LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
        if (descriptor == null) {
            Log.e(TAG, "getAudioLocation: No valid descriptor for device: " + device);
            return BluetoothLeAudio.AUDIO_LOCATION_INVALID;
        }

        return descriptor.mSinkAudioLocation;
    }

    /**
     * Check if inband ringtone is enabled by the LE Audio group.
     * Group id for the device can be found with {@link BluetoothLeAudio#getGroupId}.
     * @param groupId LE Audio group id
     * @return true if inband ringtone is enabled, false otherwise
     */
    public boolean isInbandRingtoneEnabled(int groupId) {
        if (!mLeAudioInbandRingtoneSupportedByPlatform) {
            return mLeAudioInbandRingtoneSupportedByPlatform;
        }

        LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
        if (descriptor == null) {
            return false;
        }

        return descriptor.mInbandRingtoneEnabled;
    }

    /**
     * Set In Call state
     * @param inCall True if device in call (any state), false otherwise.
     */
    public void setInCall(boolean inCall) {
        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "Le Audio not initialized properly.");
            return;
        }

        /* For setting inCall mode */
        if (Flags.leaudioBroadcastAudioHandoverPolicies() && inCall && !areBroadcastsAllStopped()) {
            mQueuedInCallValue = Optional.of(true);

            /* Request activation of unicast group */
            handleUnicastStreamStatusChange(
                    LeAudioStackEvent.DIRECTION_SINK,
                    LeAudioStackEvent.STATUS_LOCAL_STREAM_REQUESTED);
            return;
        }

        mLeAudioNativeInterface.setInCall(inCall);

        /* For clearing inCall mode */
        if (Flags.leaudioBroadcastAudioHandoverPolicies()
                && !inCall
                && mBroadcastIdDeactivatedForUnicastTransition.isPresent()) {
            handleUnicastStreamStatusChange(
                    LeAudioStackEvent.DIRECTION_SINK,
                    LeAudioStackEvent.STATUS_LOCAL_STREAM_SUSPENDED);
        }
    }

    /**
     * Sends the preferred audio profiles for a dual mode audio device to the native stack.
     *
     * @param groupId is the group id of the device which had a preference change
     * @param isOutputPreferenceLeAudio {@code true} if {@link BluetoothProfile#LE_AUDIO} is
     * preferred for {@link BluetoothAdapter#AUDIO_MODE_OUTPUT_ONLY}, {@code false} if it is
     * {@link BluetoothProfile#A2DP}
     * @param isDuplexPreferenceLeAudio {@code true} if {@link BluetoothProfile#LE_AUDIO} is
     * preferred for {@link BluetoothAdapter#AUDIO_MODE_DUPLEX}, {@code false} if it is
     * {@link BluetoothProfile#HEADSET}
     */
    public void sendAudioProfilePreferencesToNative(int groupId, boolean isOutputPreferenceLeAudio,
            boolean isDuplexPreferenceLeAudio) {
        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "Le Audio not initialized properly.");
            return;
        }
        mLeAudioNativeInterface.sendAudioProfilePreferences(groupId, isOutputPreferenceLeAudio,
                isDuplexPreferenceLeAudio);
    }

    /**
     * Set Inactive by HFP during handover
     */
    public void setInactiveForHfpHandover(BluetoothDevice hfpHandoverDevice) {
        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "Le Audio not initialized properly.");
            return;
        }
        if (getActiveGroupId() != LE_AUDIO_GROUP_ID_INVALID) {
            mHfpHandoverDevice = hfpHandoverDevice;
            removeActiveDevice(true);
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
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean setConnectionPolicy(BluetoothDevice device, int connectionPolicy) {
        enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED,
                "Need BLUETOOTH_PRIVILEGED permission");
        if (DBG) {
            Log.d(TAG, "Saved connectionPolicy " + device + " = " + connectionPolicy);
        }

        if (!mDatabaseManager.setProfileConnectionPolicy(device, BluetoothProfile.LE_AUDIO,
                connectionPolicy)) {
            return false;
        }

        if (connectionPolicy == BluetoothProfile.CONNECTION_POLICY_ALLOWED) {
            setEnabledState(device, /* enabled = */ true);
            // Authorizes LEA GATT server services if already assigned to a group
            int groupId = getGroupId(device);
            if (groupId != LE_AUDIO_GROUP_ID_INVALID) {
                setAuthorizationForRelatedProfiles(device, true);
            }
            connect(device);
        } else if (connectionPolicy == BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            setEnabledState(device, /* enabled = */ false);
            // Remove authorization for LEA GATT server services
            setAuthorizationForRelatedProfiles(device, false);
            disconnect(device);
        }
        setLeAudioGattClientProfilesPolicy(device, connectionPolicy);
        return true;
    }

    /**
     * Sets the connection policy for LE Audio GATT client profiles
     * @param device is the remote device
     * @param connectionPolicy is the connection policy we wish to set
     */
    private void setLeAudioGattClientProfilesPolicy(BluetoothDevice device, int connectionPolicy) {
        if (DBG) {
            Log.d(TAG, "setLeAudioGattClientProfilesPolicy for device " + device + " to policy="
                    + connectionPolicy);
        }
        VolumeControlService volumeControlService = getVolumeControlService();
        if (volumeControlService != null) {
            volumeControlService.setConnectionPolicy(device, connectionPolicy);
        }

        if (mHapClientService == null) {
            mHapClientService = mServiceFactory.getHapClientService();
        }
        if (mHapClientService != null) {
            mHapClientService.setConnectionPolicy(device, connectionPolicy);
        }

        if (mCsipSetCoordinatorService == null) {
            mCsipSetCoordinatorService = mServiceFactory.getCsipSetCoordinatorService();
        }

        // Disallow setting CSIP to forbidden until characteristic reads are complete
        if (mCsipSetCoordinatorService != null) {
            mCsipSetCoordinatorService.setConnectionPolicy(device, connectionPolicy);
        }
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
    public int getConnectionPolicy(BluetoothDevice device) {
        int connection_policy = mDatabaseManager
                .getProfileConnectionPolicy(device, BluetoothProfile.LE_AUDIO);
        if (DBG) {
            Log.d(TAG, device + " connection policy = " + connection_policy);
        }
        return connection_policy;
    }

    /**
     * Get device group id. Devices with same group id belong to same group (i.e left and right
     * earbud)
     * @param device LE Audio capable device
     * @return group id that this device currently belongs to
     */
    public int getGroupId(BluetoothDevice device) {
        if (device == null) {
            return LE_AUDIO_GROUP_ID_INVALID;
        }

        groupMutexLock(/* isReadOnly */ true);
        try {
            LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
            if (descriptor == null) {
                Log.e(TAG, "getGroupId: No valid descriptor for device: " + device);
                return LE_AUDIO_GROUP_ID_INVALID;
            }

            return descriptor.mGroupId;
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
    }

    /**
     * Set the user application ccid along with used context type
     * @param userUuid user uuid
     * @param ccid content control id
     * @param contextType context type
     */
    public void setCcidInformation(ParcelUuid userUuid, int ccid, int contextType) {
        /* for the moment we care only for GMCS and GTBS */
        if (userUuid != BluetoothUuid.GENERIC_MEDIA_CONTROL
                && userUuid.getUuid() != TbsGatt.UUID_GTBS) {
            return;
        }
        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "Le Audio not initialized properly.");
            return;
        }
        mLeAudioNativeInterface.setCcidInformation(ccid, contextType);
    }

    /**
     * Set volume for streaming devices
     * @param volume volume to set
     */
    public void setVolume(int volume) {
        if (DBG) {
            Log.d(TAG, "SetVolume " + volume);
        }

        int currentlyActiveGroupId = getActiveGroupId();
        if (currentlyActiveGroupId == LE_AUDIO_GROUP_ID_INVALID) {
            Log.e(TAG, "There is no active group ");
            return;
        }

        VolumeControlService volumeControlService = getVolumeControlService();
        if (volumeControlService != null) {
            volumeControlService.setGroupVolume(currentlyActiveGroupId, volume);
        }
    }

    TbsService getTbsService() {
        if (mTbsService != null) {
            return mTbsService;
        }

        mTbsService = mServiceFactory.getTbsService();
        return mTbsService;
    }

    McpService getMcpService() {
        if (mMcpService != null) {
            return mMcpService;
        }

        mMcpService = mServiceFactory.getMcpService();
        return mMcpService;
    }

    void setAuthorizationForRelatedProfiles(BluetoothDevice device, boolean authorize) {
        McpService mcpService = getMcpService();
        if (mcpService != null) {
            mcpService.setDeviceAuthorized(device, authorize);
        }

        TbsService tbsService = getTbsService();
        if (tbsService != null) {
            tbsService.setDeviceAuthorized(device, authorize);
        }
    }

    void removeAuthorizationInfoForRelatedProfiles(BluetoothDevice device) {
        if (!Flags.leaudioMcsTbsAuthorizationRebondFix()) {
            Log.i(TAG, "leaudio_mcs_tbs_authorization_rebond_fix is disabled");
            return;
        }

        McpService mcpService = getMcpService();
        if (mcpService != null) {
            mcpService.removeDeviceAuthorizationInfo(device);
        }

        TbsService tbsService = getTbsService();
        if (tbsService != null) {
            tbsService.removeDeviceAuthorizationInfo(device);
        }
    }

    /**
     * This function is called when the framework registers a callback with the service for this
     * first time. This is used as an indication that Bluetooth has been enabled.
     *
     * <p>It is used to authorize all known LeAudio devices in the services which requires that e.g.
     * GMCS
     */
    @VisibleForTesting
    void handleBluetoothEnabled() {
        if (DBG) {
            Log.d(TAG, "handleBluetoothEnabled ");
        }

        mBluetoothEnabled = true;

        groupMutexLock(/* isReadOnly */ true);
        try {
            try {
                if (mDeviceDescriptors.isEmpty()) {
                    return;
                }
            } finally {
                if (!Flags.leaudioApiSynchronizedBlockFix()) {
                    // Keep previous behavior where a lock is released and acquired immediately
                    groupMutexUnlock(/* isReadOnly */ true);
                    groupMutexLock(/* isReadOnly */ true);
                }
            }
            for (BluetoothDevice device : mDeviceDescriptors.keySet()) {
                if (getConnectionPolicy(device) != BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
                    setAuthorizationForRelatedProfiles(device, true);
                }
            }
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }

        startAudioServersBackgroundScan(/* retry = */ false);
    }

    private LeAudioGroupDescriptor getGroupDescriptor(int groupId) {
        groupMutexLock(/* isReadOnly */ true);
        try {
            return mGroupDescriptors.get(groupId);
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
    }

    private LeAudioDeviceDescriptor getDeviceDescriptor(BluetoothDevice device) {
        groupMutexLock(/* isReadOnly */ true);
        try {
            return mDeviceDescriptors.get(device);
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }
    }

    private void handleGroupNodeAdded(BluetoothDevice device, int groupId) {
        groupMutexLock(/* isReadOnly */ false);
        try {
            if (DBG) {
                Log.d(TAG, "Device " + device + " added to group " + groupId);
            }

            LeAudioGroupDescriptor groupDescriptor = getGroupDescriptor(groupId);
            if (groupDescriptor == null) {
                mGroupDescriptors.put(groupId,
                        new LeAudioGroupDescriptor(false));
            }
            groupDescriptor = getGroupDescriptor(groupId);
            if (groupDescriptor == null) {
                Log.e(TAG, "Could not create group description");
                return;
            }
            LeAudioDeviceDescriptor deviceDescriptor = getDeviceDescriptor(device);
            if (deviceDescriptor == null) {
                deviceDescriptor = createDeviceDescriptor(device,
                        groupDescriptor.mInbandRingtoneEnabled);
                if (deviceDescriptor == null) {
                    Log.e(TAG, "handleGroupNodeAdded: Can't create descriptor for added from"
                            + " storage device: " + device);
                    return;
                }

                LeAudioStateMachine sm = getOrCreateStateMachine(device);
                if (getOrCreateStateMachine(device) == null) {
                    Log.e(TAG, "Can't get state machine for device: " + device);
                    return;
                }
            }
            deviceDescriptor.mGroupId = groupId;

            notifyGroupNodeAdded(device, groupId);
        } finally {
            groupMutexUnlock(/* isReadOnly */ false);
        }

        if (mBluetoothEnabled) {
            setAuthorizationForRelatedProfiles(device, true);
            startAudioServersBackgroundScan(/* retry = */ false);
        }
    }

    private void notifyGroupNodeAdded(BluetoothDevice device, int groupId) {
        VolumeControlService volumeControlService = getVolumeControlService();
        if (volumeControlService != null) {
            volumeControlService.handleGroupNodeAdded(groupId, device);
        }

        if (mLeAudioCallbacks != null) {
            int n = mLeAudioCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mLeAudioCallbacks.getBroadcastItem(i).onGroupNodeAdded(device, groupId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mLeAudioCallbacks.finishBroadcast();
        }
    }

    private void handleGroupNodeRemoved(BluetoothDevice device, int groupId) {
        if (DBG) {
            Log.d(TAG, "Removing device " + device + " grom group " + groupId);
        }

        boolean isGroupEmpty = true;
        groupMutexLock(/* isReadOnly */ true);
        try {
            LeAudioGroupDescriptor groupDescriptor = getGroupDescriptor(groupId);
            if (groupDescriptor == null) {
                Log.e(TAG, "handleGroupNodeRemoved: No valid descriptor for group: " + groupId);
                return;
            }
            if (DBG) {
                Log.d(TAG, "Lost lead device is " + groupDescriptor.mLostLeadDeviceWhileStreaming);
            }
            if (Objects.equals(device, groupDescriptor.mLostLeadDeviceWhileStreaming)) {
                clearLostDevicesWhileStreaming(groupDescriptor);
            }

            LeAudioDeviceDescriptor deviceDescriptor = getDeviceDescriptor(device);
            if (deviceDescriptor == null) {
                Log.e(TAG, "handleGroupNodeRemoved: No valid descriptor for device: " + device);
                return;
            }
            deviceDescriptor.mGroupId = LE_AUDIO_GROUP_ID_INVALID;

            for (LeAudioDeviceDescriptor descriptor : mDeviceDescriptors.values()) {
                if (descriptor.mGroupId == groupId) {
                    isGroupEmpty = false;
                    break;
                }
            }

            if (isGroupEmpty) {
                /* Device is currently an active device. Group needs to be inactivated before
                 * removing
                 */
                if (Objects.equals(device, mActiveAudioOutDevice)
                        || Objects.equals(device, mActiveAudioInDevice)) {
                    handleGroupTransitToInactive(groupId);
                }
                if (!Flags.leaudioApiSynchronizedBlockFix()) {
                    mGroupDescriptors.remove(groupId);
                }

                if (mUnicastGroupIdDeactivatedForBroadcastTransition == groupId) {
                    updateFallbackUnicastGroupIdForBroadcast(LE_AUDIO_GROUP_ID_INVALID);
                }
            }
            notifyGroupNodeRemoved(device, groupId);
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }

        if (isGroupEmpty && Flags.leaudioApiSynchronizedBlockFix()) {
            groupMutexLock(/* isReadOnly */ false);
            try {
                mGroupDescriptors.remove(groupId);
            } finally {
                groupMutexUnlock(/* isReadOnly */ false);
            }
        }

        setAuthorizationForRelatedProfiles(device, false);
        removeAuthorizationInfoForRelatedProfiles(device);
    }

    private void notifyGroupNodeRemoved(BluetoothDevice device, int groupId) {
        if (mLeAudioCallbacks != null) {
            int n = mLeAudioCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mLeAudioCallbacks.getBroadcastItem(i).onGroupNodeRemoved(device, groupId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mLeAudioCallbacks.finishBroadcast();
        }
    }

    private void notifyGroupStatusChanged(int groupId, int status) {
        if (mLeAudioCallbacks != null) {
            int n = mLeAudioCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mLeAudioCallbacks.getBroadcastItem(i).onGroupStatusChanged(groupId, status);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mLeAudioCallbacks.finishBroadcast();
        }
    }

    private void notifyUnicastCodecConfigChanged(int groupId, BluetoothLeAudioCodecStatus status) {
        if (mLeAudioCallbacks != null) {
            int n = mLeAudioCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mLeAudioCallbacks.getBroadcastItem(i).onCodecConfigChanged(groupId, status);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mLeAudioCallbacks.finishBroadcast();
        }
    }

    private void notifyBroadcastStarted(Integer broadcastId, int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i).onBroadcastStarted(reason, broadcastId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyBroadcastStartFailed(Integer broadcastId, int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i).onBroadcastStartFailed(reason);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyOnBroadcastStopped(Integer broadcastId, int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i).onBroadcastStopped(reason, broadcastId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyOnBroadcastStopFailed(int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i).onBroadcastStopFailed(reason);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyPlaybackStarted(Integer broadcastId, int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i).onPlaybackStarted(reason, broadcastId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyPlaybackStopped(Integer broadcastId, int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i).onPlaybackStopped(reason, broadcastId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyBroadcastUpdated(int broadcastId, int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i).onBroadcastUpdated(reason, broadcastId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyBroadcastUpdateFailed(int broadcastId, int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i)
                            .onBroadcastUpdateFailed(reason, broadcastId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyBroadcastMetadataChanged(int broadcastId,
            BluetoothLeBroadcastMetadata metadata) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i)
                            .onBroadcastMetadataChanged(broadcastId, metadata);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    /**
     * Update the fallback unicast group id during the handover to broadcast Also store the fallback
     * group id in Settings store.
     *
     * @param groupId group id to update
     */
    private void updateFallbackUnicastGroupIdForBroadcast(int groupId) {
        Log.i(
                TAG,
                "Update unicast fallback active group from: "
                        + mUnicastGroupIdDeactivatedForBroadcastTransition
                        + " to : "
                        + groupId);
        mUnicastGroupIdDeactivatedForBroadcastTransition = groupId;

        // waive WRITE_SECURE_SETTINGS permission check
        final long callingIdentity = Binder.clearCallingIdentity();
        try {
            Context userContext =
                    getApplicationContext()
                            .createContextAsUser(
                                    UserHandle.of(ActivityManager.getCurrentUser()), 0);
            Settings.Secure.putInt(
                    userContext.getContentResolver(),
                    BLUETOOTH_LE_BROADCAST_FALLBACK_ACTIVE_GROUP_ID,
                    groupId);
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    /**
     * Gets the current codec status (configuration and capability).
     *
     * @param groupId the group id
     * @return the current codec status
     * @hide
     */
    public BluetoothLeAudioCodecStatus getCodecStatus(int groupId) {
        if (DBG) {
            Log.d(TAG, "getCodecStatus(" + groupId + ")");
        }
        LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
        if (descriptor != null) {
            return descriptor.mCodecStatus;
        }
        return null;
    }

    /**
     * Sets the codec configuration preference.
     *
     * @param groupId the group id
     * @param inputCodecConfig the input codec configuration preference
     * @param outputCodecConfig the output codec configuration preference
     * @hide
     */
    public void setCodecConfigPreference(int groupId,
            BluetoothLeAudioCodecConfig inputCodecConfig,
            BluetoothLeAudioCodecConfig outputCodecConfig) {
        if (DBG) {
            Log.d(TAG, "setCodecConfigPreference(" + groupId + "): "
                    + Objects.toString(inputCodecConfig)
                    + Objects.toString(outputCodecConfig));
        }
        LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
        if (descriptor == null) {
            Log.e(TAG, "setCodecConfigPreference: Invalid groupId, " + groupId);
            return;
        }

        if (inputCodecConfig == null || outputCodecConfig == null) {
            Log.e(TAG, "setCodecConfigPreference: Codec config can't be null");
            return;
        }

        /* We support different configuration for input and output but codec type
         * shall be same */
        if (inputCodecConfig.getCodecType() != outputCodecConfig.getCodecType()) {
            Log.e(TAG, "setCodecConfigPreference: Input codec type: "
                    + inputCodecConfig.getCodecType()
                    + "does not match output codec type: " + outputCodecConfig.getCodecType());
            return;
        }

        if (descriptor.mCodecStatus == null) {
            Log.e(TAG, "setCodecConfigPreference: Codec status is null");
            return;
        }

        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "Le Audio not initialized properly.");
            return;
        }

        mLeAudioNativeInterface.setCodecConfigPreference(
                groupId, inputCodecConfig, outputCodecConfig);
    }

    /**
     * Checks if the remote device supports LE Audio duplex (output and input).
     * @param device the remote device to check
     * @return {@code true} if LE Audio duplex is supported, {@code false} otherwise
     */
    public boolean isLeAudioDuplexSupported(BluetoothDevice device) {
        int groupId = getGroupId(device);
        if (groupId == LE_AUDIO_GROUP_ID_INVALID) {
            return false;
        }

        LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
        if (descriptor == null) {
            return false;
        }
        return (descriptor.mDirection & AUDIO_DIRECTION_OUTPUT_BIT) != 0
                && (descriptor.mDirection & AUDIO_DIRECTION_INPUT_BIT) != 0;
    }

    /**
     * Checks if the remote device supports LE Audio output
     * @param device the remote device to check
     * @return {@code true} if LE Audio output is supported, {@code false} otherwise
     */
    public boolean isLeAudioOutputSupported(BluetoothDevice device) {
        int groupId = getGroupId(device);
        if (groupId == LE_AUDIO_GROUP_ID_INVALID) {
            return false;
        }

        LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
        if (descriptor == null) {
            return false;
        }
        return (descriptor.mDirection & AUDIO_DIRECTION_OUTPUT_BIT) != 0;
    }

    /**
     * Gets the lead device for the CSIP group containing the provided device
     * @param device the remote device whose CSIP group lead device we want to find
     * @return the lead device of the CSIP group or {@code null} if the group does not exist
     */
    public BluetoothDevice getLeadDevice(BluetoothDevice device) {
        int groupId = getGroupId(device);
        if (groupId == LE_AUDIO_GROUP_ID_INVALID) {
            return null;
        }
        return getConnectedGroupLeadDevice(groupId);
    }

    /**
     * Sends the preferred audio profile change requested from a call to
     * {@link BluetoothAdapter#setPreferredAudioProfiles(BluetoothDevice, Bundle)} to the audio
     * framework to apply the change. The audio framework will call
     * {@link BluetoothAdapter#notifyActiveDeviceChangeApplied(BluetoothDevice)} once the
     * change is successfully applied.
     *
     * @return the number of requests sent to the audio framework
     */
    public int sendPreferredAudioProfileChangeToAudioFramework() {
        if (mActiveAudioOutDevice == null && mActiveAudioInDevice == null) {
            Log.e(TAG, "sendPreferredAudioProfileChangeToAudioFramework: no active device");
            return 0;
        }

        int audioFrameworkCalls = 0;

        if (mActiveAudioOutDevice != null) {
            int volume = getAudioDeviceGroupVolume(getGroupId(mActiveAudioOutDevice));
            final boolean suppressNoisyIntent = mActiveAudioOutDevice != null;
            Log.i(TAG, "Sending LE Audio Output active device changed for preferred profile "
                    + "change with volume=" + volume + " and suppressNoisyIntent="
                    + suppressNoisyIntent);

            final BluetoothProfileConnectionInfo connectionInfo;
            if (isAtLeastU()) {
                connectionInfo =
                        BluetoothProfileConnectionInfo.createLeAudioOutputInfo(
                                suppressNoisyIntent, volume);
            } else {
                connectionInfo =
                        BluetoothProfileConnectionInfo.createLeAudioInfo(suppressNoisyIntent, true);
            }

            mAudioManager.handleBluetoothActiveDeviceChanged(
                    mActiveAudioOutDevice, mActiveAudioOutDevice, connectionInfo);
            audioFrameworkCalls++;
        }

        if (mActiveAudioInDevice != null) {
            Log.i(TAG, "Sending LE Audio Input active device changed for audio profile change");
            mAudioManager.handleBluetoothActiveDeviceChanged(mActiveAudioInDevice,
                    mActiveAudioInDevice, BluetoothProfileConnectionInfo.createLeAudioInfo(false,
                            false));
            audioFrameworkCalls++;
        }

        return audioFrameworkCalls;
    }

    class DialingOutTimeoutEvent implements Runnable {
        @Override
        public void run() {
            Log.w(TAG, "Failed to start Broadcast in time");

            mDialingOutTimeoutEvent = null;

            if (getLeAudioService() == null) {
                Log.e(TAG, "DialingOutTimeoutEvent: No LE Audio service");
                return;
            }

            if (mActiveBroadcastAudioDevice != null) {
                updateBroadcastActiveDevice(null, mActiveBroadcastAudioDevice, false);
            }
        }
    }

    /**
     * Binder object: must be a static class or memory leak may occur
     */
    @VisibleForTesting
    static class BluetoothLeAudioBinder extends IBluetoothLeAudio.Stub
            implements IProfileServiceBinder {
        private LeAudioService mService;

        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        private LeAudioService getService(AttributionSource source) {
            if (Utils.isInstrumentationTestMode()) {
                return mService;
            }
            if (!Utils.checkServiceAvailable(mService, TAG)
                    || !Utils.checkCallerIsSystemOrActiveOrManagedUser(mService, TAG)
                    || !Utils.checkConnectPermissionForDataDelivery(mService, source, TAG)) {
                return null;
            }
            return mService;
        }

        BluetoothLeAudioBinder(LeAudioService svc) {
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

                LeAudioService service = getService(source);
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
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
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
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                List<BluetoothDevice> result = new ArrayList<>(0);
                if (service != null) {
                    result = service.getConnectedDevices();
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectedGroupLeadDevice(int groupId, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                BluetoothDevice result = null;
                if (service != null) {
                    result = service.getConnectedGroupLeadDevice(groupId);
                }
                receiver.send(result);
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

                LeAudioService service = getService(source);
                List<BluetoothDevice> result = new ArrayList<>(0);
                if (service != null) {
                    result = service.getDevicesMatchingConnectionStates(states);
                }
                receiver.send(result);
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

                LeAudioService service = getService(source);
                int result = BluetoothProfile.STATE_DISCONNECTED;
                if (service != null) {
                    result = service.getConnectionState(device);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setActiveDevice(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");
                LeAudioService service = getService(source);
                if (service != null) {
                    if (Flags.audioRoutingCentralization()) {
                        ((AudioRoutingManager) service.mAdapterService.getActiveDeviceManager())
                                .activateDeviceProfile(device, BluetoothProfile.LE_AUDIO, receiver);
                    } else {
                        boolean result;
                        if (device == null) {
                            result = service.removeActiveDevice(true);
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
        public void getActiveDevices(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                List<BluetoothDevice> result = new ArrayList<>();
                if (service != null) {
                    result = service.getActiveDevices();
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getAudioLocation(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                int result = BluetoothLeAudio.AUDIO_LOCATION_INVALID;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    result = service.getAudioLocation(device);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void isInbandRingtoneEnabled(AttributionSource source,
                SynchronousResultReceiver receiver, int groupId) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                boolean result = false;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    result = service.isInbandRingtoneEnabled(groupId);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setConnectionPolicy(BluetoothDevice device, int connectionPolicy,
                AttributionSource source, SynchronousResultReceiver receiver) {
            Objects.requireNonNull(device, "device cannot be null");
            Objects.requireNonNull(source, "source cannot be null");
            Objects.requireNonNull(receiver, "receiver cannot be null");

            try {
                LeAudioService service = getService(source);
                boolean result = false;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
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
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                int result = BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                enforceBluetoothPrivilegedPermission(service);
                result = service.getConnectionPolicy(device);
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setCcidInformation(ParcelUuid userUuid, int ccid, int contextType,
                AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(userUuid, "userUuid cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                enforceBluetoothPrivilegedPermission(service);
                service.setCcidInformation(userUuid, ccid, contextType);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getGroupId(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                int result = LE_AUDIO_GROUP_ID_INVALID;
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                result = service.getGroupId(device);
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void groupAddNode(int group_id, BluetoothDevice device,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                boolean result = false;
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                enforceBluetoothPrivilegedPermission(service);
                result = service.groupAddNode(group_id, device);
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setInCall(boolean inCall, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                enforceBluetoothPrivilegedPermission(service);
                service.setInCall(inCall);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setInactiveForHfpHandover(BluetoothDevice hfpHandoverDevice,
                AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                enforceBluetoothPrivilegedPermission(service);
                service.setInactiveForHfpHandover(hfpHandoverDevice);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void groupRemoveNode(int groupId, BluetoothDevice device,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                boolean result = false;
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                enforceBluetoothPrivilegedPermission(service);
                result = service.groupRemoveNode(groupId, device);
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setVolume(int volume, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                enforceBluetoothPrivilegedPermission(service);
                receiver.send(null);
                service.setVolume(volume);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void registerCallback(IBluetoothLeAudioCallback callback,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(callback, "callback cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                if ((service == null) || (service.mLeAudioCallbacks == null)) {
                    throw new IllegalStateException("Service is unavailable: " + service);
                }

                enforceBluetoothPrivilegedPermission(service);
                service.mLeAudioCallbacks.register(callback);
                if (!service.mBluetoothEnabled) {
                    service.handleBluetoothEnabled();
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void unregisterCallback(IBluetoothLeAudioCallback callback,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(callback, "callback cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                if ((service == null) || (service.mLeAudioCallbacks == null)) {
                    throw new IllegalStateException("Service is unavailable");
                }

                enforceBluetoothPrivilegedPermission(service);

                service.mLeAudioCallbacks.unregister(callback);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void registerLeBroadcastCallback(IBluetoothLeBroadcastCallback callback,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(callback, "callback cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                if ((service == null) || (service.mBroadcastCallbacks == null)) {
                    throw new IllegalStateException("Service is unavailable");
                }

                enforceBluetoothPrivilegedPermission(service);

                service.mBroadcastCallbacks.register(callback);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void unregisterLeBroadcastCallback(IBluetoothLeBroadcastCallback callback,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                LeAudioService service = getService(source);
                if ((service == null) || (service.mBroadcastCallbacks == null)) {
                    throw new IllegalStateException("Service is unavailable");
                }

                enforceBluetoothPrivilegedPermission(service);
                Objects.requireNonNull(callback, "callback cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                service.mBroadcastCallbacks.unregister(callback);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void startBroadcast(
                BluetoothLeBroadcastSettings broadcastSettings, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                LeAudioService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    service.createBroadcast(broadcastSettings);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void stopBroadcast(int broadcastId, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                LeAudioService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    service.stopBroadcast(broadcastId);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void updateBroadcast(
                int broadcastId,
                BluetoothLeBroadcastSettings broadcastSettings,
                AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                LeAudioService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    service.updateBroadcast(broadcastId, broadcastSettings);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void isPlaying(int broadcastId, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                boolean result = false;
                LeAudioService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    result = service.isPlaying(broadcastId);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getAllBroadcastMetadata(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                List<BluetoothLeBroadcastMetadata> result = new ArrayList<>();
                LeAudioService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    result = service.getAllBroadcastMetadata();
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getMaximumNumberOfBroadcasts(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                int result = 0;
                LeAudioService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    result = service.getMaximumNumberOfBroadcasts();
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getMaximumStreamsPerBroadcast(
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                int result = 0;
                LeAudioService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    result = service.getMaximumStreamsPerBroadcast();
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getMaximumSubgroupsPerBroadcast(
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                int result = 0;
                LeAudioService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    result = service.getMaximumSubgroupsPerBroadcast();
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getCodecStatus(int groupId,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                LeAudioService service = getService(source);
                BluetoothLeAudioCodecStatus codecStatus = null;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    codecStatus = service.getCodecStatus(groupId);
                }
                receiver.send(codecStatus);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setCodecConfigPreference(int groupId,
                BluetoothLeAudioCodecConfig inputCodecConfig,
                BluetoothLeAudioCodecConfig outputCodecConfig,
                AttributionSource source) {
            LeAudioService service = getService(source);
            if (service == null) {
                return;
            }

            enforceBluetoothPrivilegedPermission(service);
            service.setCodecConfigPreference(groupId, inputCodecConfig, outputCodecConfig);
        }

        @Override
        public void isBroadcastActive(
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                boolean result = false;
                LeAudioService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    result = service.isBroadcastActive();
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        ProfileService.println(sb, "isDualModeAudioEnabled: " + Utils.isDualModeAudioEnabled());
        ProfileService.println(sb, "Active Groups information: ");
        ProfileService.println(sb, "  currentlyActiveGroupId: " + getActiveGroupId());
        ProfileService.println(sb, "  mActiveAudioOutDevice: " + mActiveAudioOutDevice);
        ProfileService.println(sb, "  mActiveAudioInDevice: " + mActiveAudioInDevice);
        ProfileService.println(sb, "  mUnicastGroupIdDeactivatedForBroadcastTransition: "
                + mUnicastGroupIdDeactivatedForBroadcastTransition);
        ProfileService.println(sb, "  mBroadcastIdDeactivatedForUnicastTransition: "
                + mBroadcastIdDeactivatedForUnicastTransition);
        ProfileService.println(sb, "  mExposedActiveDevice: " + mExposedActiveDevice);
        ProfileService.println(sb, "  mHfpHandoverDevice:" + mHfpHandoverDevice);
        ProfileService.println(sb, "  mLeAudioIsInbandRingtoneSupported:"
                                + mLeAudioInbandRingtoneSupportedByPlatform);

        int numberOfUngroupedDevs = 0;
        groupMutexLock(/* isReadOnly */ true);
        try {
            for (Map.Entry<Integer, LeAudioGroupDescriptor> groupEntry
                                                : mGroupDescriptors.entrySet()) {
                LeAudioGroupDescriptor groupDescriptor = groupEntry.getValue();
                Integer groupId = groupEntry.getKey();
                BluetoothDevice leadDevice = getConnectedGroupLeadDevice(groupId);
                ProfileService.println(sb, "Group: " + groupId);
                ProfileService.println(sb, "  isActive: " + groupDescriptor.mIsActive);
                ProfileService.println(sb, "  isConnected: " + groupDescriptor.mIsConnected);
                ProfileService.println(sb, "  mDirection: " + groupDescriptor.mDirection);
                ProfileService.println(sb, "  group lead: " + leadDevice);
                ProfileService.println(sb, "  lost lead device: "
                        + groupDescriptor.mLostLeadDeviceWhileStreaming);
                ProfileService.println(sb, "  mInbandRingtoneEnabled: "
                        + groupDescriptor.mInbandRingtoneEnabled);
                ProfileService.println(
                        sb,
                        "mInactivatedDueToContextType: "
                                + groupDescriptor.mInactivatedDueToContextType);

                for (Map.Entry<BluetoothDevice, LeAudioDeviceDescriptor> deviceEntry
                        : mDeviceDescriptors.entrySet()) {
                    LeAudioDeviceDescriptor deviceDescriptor = deviceEntry.getValue();
                    if (deviceDescriptor.mGroupId != groupId) {
                        if (deviceDescriptor.mGroupId == LE_AUDIO_GROUP_ID_INVALID) {
                            numberOfUngroupedDevs++;
                        }
                        continue;
                    }

                    if (deviceDescriptor.mStateMachine != null) {
                        deviceDescriptor.mStateMachine.dump(sb);
                    } else {
                        ProfileService.println(sb, "state machine is null");
                    }
                    ProfileService.println(
                            sb, "    mAclConnected: " + deviceDescriptor.mAclConnected);
                    ProfileService.println(sb, "    mDevInbandRingtoneEnabled: "
                            + deviceDescriptor.mDevInbandRingtoneEnabled);
                    ProfileService.println(sb, "    mSinkAudioLocation: "
                            + deviceDescriptor.mSinkAudioLocation);
                    ProfileService.println(sb, "    mDirection: " + deviceDescriptor.mDirection);
                }
            }
        } finally {
            groupMutexUnlock(/* isReadOnly */ true);
        }

        if (numberOfUngroupedDevs > 0) {
            ProfileService.println(sb, "UnGroup devices:");
            for (Map.Entry<BluetoothDevice, LeAudioDeviceDescriptor> entry
                    : mDeviceDescriptors.entrySet()) {
                LeAudioDeviceDescriptor deviceDescriptor = entry.getValue();
                if (deviceDescriptor.mGroupId != LE_AUDIO_GROUP_ID_INVALID) {
                    continue;
                }

                deviceDescriptor.mStateMachine.dump(sb);
                ProfileService.println(sb, "    mAclConnected: " + deviceDescriptor.mAclConnected);
                ProfileService.println(sb, "    mDevInbandRingtoneEnabled: "
                        + deviceDescriptor.mDevInbandRingtoneEnabled);
                ProfileService.println(sb, "    mSinkAudioLocation: "
                        + deviceDescriptor.mSinkAudioLocation);
                ProfileService.println(sb, "    mDirection: " + deviceDescriptor.mDirection);
            }
        }
    }

    private void groupMutexLock(boolean isReadOnly) {
        if (Flags.leaudioApiSynchronizedBlockFix()) {
            if (isReadOnly) {
                mGroupReadLock.lock();
            } else {
                mGroupWriteLock.lock();
            }
        } else {
            mGroupLock.lock();
        }
    }

    private void groupMutexUnlock(boolean isReadOnly) {
        if (Flags.leaudioApiSynchronizedBlockFix()) {
            if (isReadOnly) {
                mGroupReadLock.unlock();
            } else {
                mGroupWriteLock.unlock();
            }
        } else {
            mGroupLock.unlock();
        }
    }
}
