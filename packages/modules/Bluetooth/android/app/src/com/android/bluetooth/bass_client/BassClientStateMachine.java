/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.bass_client;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import android.annotation.Nullable;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothLeAudioCodecConfigMetadata;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastAssistant;
import android.bluetooth.BluetoothLeBroadcastChannel;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastReceiveState;
import android.bluetooth.BluetoothLeBroadcastSubgroup;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothUtils;
import android.bluetooth.BluetoothUtils.TypeValueEntry;
import android.bluetooth.le.PeriodicAdvertisingCallback;
import android.bluetooth.le.PeriodicAdvertisingReport;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.os.Binder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.provider.DeviceConfig;
import android.util.Log;
import android.util.Pair;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.flags.FeatureFlags;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.UUID;
import java.util.stream.IntStream;

@VisibleForTesting
public class BassClientStateMachine extends StateMachine {
    private static final String TAG = "BassClientStateMachine";
    @VisibleForTesting
    static final byte[] REMOTE_SCAN_STOP = {00};
    @VisibleForTesting
    static final byte[] REMOTE_SCAN_START = {01};
    private static final byte OPCODE_ADD_SOURCE = 0x02;
    private static final byte OPCODE_UPDATE_SOURCE = 0x03;
    private static final byte OPCODE_SET_BCAST_PIN = 0x04;
    private static final byte OPCODE_REMOVE_SOURCE = 0x05;
    private static final int ADD_SOURCE_FIXED_LENGTH = 16;
    private static final int UPDATE_SOURCE_FIXED_LENGTH = 6;

    static final int CONNECT = 1;
    static final int DISCONNECT = 2;
    static final int CONNECTION_STATE_CHANGED = 3;
    static final int GATT_TXN_PROCESSED = 4;
    static final int READ_BASS_CHARACTERISTICS = 5;
    static final int START_SCAN_OFFLOAD = 6;
    static final int STOP_SCAN_OFFLOAD = 7;
    static final int SELECT_BCAST_SOURCE = 8;
    static final int ADD_BCAST_SOURCE = 9;
    static final int UPDATE_BCAST_SOURCE = 10;
    static final int SET_BCAST_CODE = 11;
    static final int REMOVE_BCAST_SOURCE = 12;
    static final int GATT_TXN_TIMEOUT = 13;
    static final int PSYNC_ACTIVE_TIMEOUT = 14;
    static final int CONNECT_TIMEOUT = 15;
    static final int REACHED_MAX_SOURCE_LIMIT = 16;
    static final int SWITCH_BCAST_SOURCE = 17;

    // NOTE: the value is not "final" - it is modified in the unit tests
    @VisibleForTesting
    private int mConnectTimeoutMs;

    // Type of argument for set broadcast code operation
    static final int ARGTYPE_METADATA = 1;
    static final int ARGTYPE_RCVSTATE = 2;

    static final int ATT_WRITE_CMD_HDR_LEN = 3;

    private final Map<Integer, PeriodicAdvertisingCallback> mPeriodicAdvCallbacksMap =
            new HashMap<>();
    /*key is combination of sourceId, Address and advSid for this hashmap*/
    private final Map<Integer, BluetoothLeBroadcastReceiveState>
            mBluetoothLeBroadcastReceiveStates =
            new HashMap<Integer, BluetoothLeBroadcastReceiveState>();
    private final Map<Integer, BluetoothLeBroadcastMetadata> mCurrentMetadata = new HashMap();
    private final Disconnected mDisconnected = new Disconnected();
    private final Connected mConnected = new Connected();
    private final Connecting mConnecting = new Connecting();
    private final ConnectedProcessing mConnectedProcessing = new ConnectedProcessing();
    private final FeatureFlags mFeatureFlags;
    private final List<Pair<ScanResult, Integer>> mSourceSyncRequestsQueue =
            new ArrayList<Pair<ScanResult, Integer>>();

    @VisibleForTesting
    final List<BluetoothGattCharacteristic> mBroadcastCharacteristics =
            new ArrayList<BluetoothGattCharacteristic>();
    @VisibleForTesting
    BluetoothDevice mDevice;

    private boolean mIsAllowedList = false;
    private int mLastConnectionState = -1;
    @VisibleForTesting
    boolean mMTUChangeRequested = false;
    @VisibleForTesting
    boolean mDiscoveryInitiated = false;
    @VisibleForTesting
    BassClientService mService;
    AdapterService mAdapterService;
    @VisibleForTesting
    BluetoothGattCharacteristic mBroadcastScanControlPoint;
    private final Map<Integer, Boolean> mFirstTimeBisDiscoveryMap;
    private int mPASyncRetryCounter = 0;
    private ScanResult mScanRes = null;
    @VisibleForTesting
    int mNumOfBroadcastReceiverStates = 0;
    @VisibleForTesting
    int mPendingOperation = -1;
    @VisibleForTesting
    byte mPendingSourceId = -1;
    @VisibleForTesting
    BluetoothLeBroadcastMetadata mPendingMetadata = null;
    private BluetoothLeBroadcastMetadata mSetBroadcastPINMetadata = null;
    @VisibleForTesting
    boolean mSetBroadcastCodePending = false;
    private final Map<Integer, Boolean> mPendingRemove = new HashMap();
    @VisibleForTesting
    boolean mAutoTriggered = false;
    private boolean mDefNoPAS = false;
    private boolean mForceSB = false;
    @VisibleForTesting
    BluetoothLeBroadcastMetadata mPendingSourceToAdd = null;
    private int mBroadcastSourceIdLength = 3;
    @VisibleForTesting
    byte mNextSourceId = 0;
    private boolean mAllowReconnect = false;
    @VisibleForTesting
    BluetoothGattTestableWrapper mBluetoothGatt = null;
    BluetoothGattCallback mGattCallback = null;
    @VisibleForTesting PeriodicAdvertisingCallback mLocalPeriodicAdvCallback = new PACallback();
    int mMaxSingleAttributeWriteValueLen = 0;
    @VisibleForTesting BluetoothLeBroadcastMetadata mPendingSourceToSwitch = null;

    BassClientStateMachine(
            BluetoothDevice device,
            BassClientService svc,
            AdapterService adapterService,
            Looper looper,
            int connectTimeoutMs,
            FeatureFlags featureFlags) {
        super(TAG + "(" + device.toString() + ")", looper);
        mDevice = device;
        mService = svc;
        mAdapterService = adapterService;
        mConnectTimeoutMs = connectTimeoutMs;
        mFeatureFlags = Objects.requireNonNull(featureFlags, "Feature Flags cannot be null");
        addState(mDisconnected);
        addState(mConnected);
        addState(mConnecting);
        addState(mConnectedProcessing);
        setInitialState(mDisconnected);
        mFirstTimeBisDiscoveryMap = new HashMap<Integer, Boolean>();
        long token = Binder.clearCallingIdentity();
        mIsAllowedList = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_BLUETOOTH,
                "persist.vendor.service.bt.wl", true);
        mDefNoPAS = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_BLUETOOTH,
                "persist.vendor.service.bt.defNoPAS", false);
        mForceSB = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_BLUETOOTH,
                "persist.vendor.service.bt.forceSB", false);
        Binder.restoreCallingIdentity(token);
    }

    static BassClientStateMachine make(
            BluetoothDevice device,
            BassClientService svc,
            AdapterService adapterService,
            Looper looper,
            FeatureFlags featureFlags) {
        Log.d(TAG, "make for device " + device);

        if (!BassClientPeriodicAdvertisingManager
                .initializePeriodicAdvertisingManagerOnDefaultAdapter()) {
            Log.e(TAG, "Failed to initialize Periodic Advertising Manager on Default Adapter");
            return null;
        }

        BassClientStateMachine BassclientSm =
                new BassClientStateMachine(
                        device,
                        svc,
                        adapterService,
                        looper,
                        BassConstants.CONNECT_TIMEOUT_MS,
                        featureFlags);
        BassclientSm.start();
        return BassclientSm;
    }

    static void destroy(BassClientStateMachine stateMachine) {
        Log.i(TAG, "destroy");
        if (stateMachine == null) {
            Log.w(TAG, "destroy(), stateMachine is null");
            return;
        }
        stateMachine.doQuit();
        stateMachine.cleanup();
    }

    public void doQuit() {
        log("doQuit for device " + mDevice);
        quitNow();
    }

    public void cleanup() {
        log("cleanup for device " + mDevice);
        clearCharsCache();

        if (mBluetoothGatt != null) {
            log("disconnect gatt");
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
            mGattCallback = null;
        }
        mPendingOperation = -1;
        mPendingSourceId = -1;
        mPendingMetadata = null;
        mPendingSourceToAdd = null;
        mPendingSourceToSwitch = null;
        mCurrentMetadata.clear();
        mPendingRemove.clear();
        mPeriodicAdvCallbacksMap.clear();
        mSourceSyncRequestsQueue.clear();
    }

    Boolean hasPendingSourceOperation() {
        return mPendingMetadata != null;
    }

    BluetoothLeBroadcastMetadata getCurrentBroadcastMetadata(Integer sourceId) {
        return mCurrentMetadata.getOrDefault(sourceId, null);
    }

    private void setCurrentBroadcastMetadata(Integer sourceId,
            BluetoothLeBroadcastMetadata metadata) {
        if (metadata != null) {
            mCurrentMetadata.put(sourceId, metadata);
        } else {
            mCurrentMetadata.remove(sourceId);
        }
    }

    boolean isPendingRemove(Integer sourceId) {
        return mPendingRemove.getOrDefault(sourceId, false);
    }

    private void setPendingRemove(Integer sourceId, boolean remove) {
        if (remove) {
            mPendingRemove.put(sourceId, remove);
        } else {
            mPendingRemove.remove(sourceId);
        }
    }

    BluetoothLeBroadcastReceiveState getBroadcastReceiveStateForSourceDevice(
            BluetoothDevice srcDevice) {
        List<BluetoothLeBroadcastReceiveState> currentSources = getAllSources();
        BluetoothLeBroadcastReceiveState state = null;
        for (int i = 0; i < currentSources.size(); i++) {
            BluetoothDevice device = currentSources.get(i).getSourceDevice();
            if (device != null && device.equals(srcDevice)) {
                state = currentSources.get(i);
                Log.e(TAG,
                        "getBroadcastReceiveStateForSourceDevice: returns for: "
                                + srcDevice + "&srcInfo" + state);
                return state;
            }
        }
        return null;
    }

    BluetoothLeBroadcastReceiveState getBroadcastReceiveStateForSourceId(int sourceId) {
        List<BluetoothLeBroadcastReceiveState> currentSources = getAllSources();
        for (int i = 0; i < currentSources.size(); i++) {
            if (sourceId == currentSources.get(i).getSourceId()) {
                return currentSources.get(i);
            }
        }
        return null;
    }

    boolean isSyncedToTheSource(int sourceId) {
        BluetoothLeBroadcastReceiveState recvState = getBroadcastReceiveStateForSourceId(sourceId);

        return recvState != null
                && (recvState.getPaSyncState()
                                == BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_SYNCHRONIZED
                        || recvState.getBisSyncState().stream()
                                .anyMatch(
                                        bitmap -> {
                                            return bitmap != 0;
                                        }));
    }

    void parseBaseData(BluetoothDevice device, int syncHandle, byte[] serviceData) {
        log("parseBaseData" + Arrays.toString(serviceData));
        BaseData base = BaseData.parseBaseData(serviceData);
        if (base != null) {
            mService.updateBase(syncHandle, base);
            base.print();
            if (mAutoTriggered) {
                // successful auto periodic synchrnization with source
                log("auto triggered assist");
                mAutoTriggered = false;
                // perform PAST with this device
                BluetoothDevice srcDevice = mService.getDeviceForSyncHandle(syncHandle);
                if (srcDevice != null) {
                    BluetoothLeBroadcastReceiveState recvState =
                            getBroadcastReceiveStateForSourceDevice(srcDevice);
                    processPASyncState(recvState);
                } else {
                    Log.w(TAG, "Autoassist: no matching device");
                }
            }
        } else {
            Log.e(TAG, "Seems BASE is not in parsable format");
            if (!mAutoTriggered) {
                cancelActiveSync(syncHandle);
            } else {
                mAutoTriggered = false;
            }
        }
    }

    void parseScanRecord(int syncHandle, ScanRecord record) {
        log("parseScanRecord: " + record);
        Map<ParcelUuid, byte[]> bmsAdvDataMap = record.getServiceData();
        if (bmsAdvDataMap != null) {
            for (Map.Entry<ParcelUuid, byte[]> entry : bmsAdvDataMap.entrySet()) {
                log("ParcelUUid = " + entry.getKey() + ", Value = "
                        + Arrays.toString(entry.getValue()));
            }
        }
        byte[] advData = record.getServiceData(BassConstants.BASIC_AUDIO_UUID);
        if (advData != null) {
            parseBaseData(mDevice, syncHandle, advData);
        } else {
            Log.e(TAG, "No service data in Scan record");
            if (!mAutoTriggered) {
                cancelActiveSync(syncHandle);
            } else {
                mAutoTriggered = false;
            }
        }
    }

    private String checkAndParseBroadcastName(ScanRecord record) {
        log("checkAndParseBroadcastName");
        byte[] rawBytes = record.getBytes();
        List<TypeValueEntry> entries = BluetoothUtils.parseLengthTypeValueBytes(rawBytes);
        if (rawBytes.length > 0 && rawBytes[0] > 0 && entries.isEmpty()) {
            Log.e(TAG, "Invalid LTV entries in Scan record");
            return null;
        }

        String broadcastName = null;
        for (TypeValueEntry entry : entries) {
            // Only use the first value of each type
            if (broadcastName == null && entry.getType() == BassConstants.BCAST_NAME_AD_TYPE) {
                byte[] bytes = entry.getValue();
                int len = bytes.length;
                if (len < BassConstants.BCAST_NAME_LEN_MIN
                        || len > BassConstants.BCAST_NAME_LEN_MAX) {
                    Log.e(TAG, "Invalid broadcast name length in Scan record" + len);
                    return null;
                }
                broadcastName = new String(bytes, StandardCharsets.UTF_8);
            }
        }
        return broadcastName;
    }

    private boolean selectSource(
            ScanResult scanRes, boolean autoTriggered) {
        log("selectSource: ScanResult " + scanRes);
        mAutoTriggered = autoTriggered;
        mPASyncRetryCounter = 1;

        // updating mainly for Address type and PA Interval here
        // extract BroadcastId from ScanResult
        ScanRecord scanRecord = scanRes.getScanRecord();
        if (scanRecord != null) {
            Map<ParcelUuid, byte[]> listOfUuids = scanRecord.getServiceData();
            int broadcastId = BassConstants.INVALID_BROADCAST_ID;
            PublicBroadcastData pbData = null;
            if (listOfUuids != null) {
                if (listOfUuids.containsKey(BassConstants.BAAS_UUID)) {
                    byte[] bId = listOfUuids.get(BassConstants.BAAS_UUID);
                    broadcastId = BassUtils.parseBroadcastId(bId);
                }
                if (listOfUuids.containsKey(BassConstants.PUBLIC_BROADCAST_UUID)) {
                    byte[] pbAnnouncement =
                            listOfUuids.get(BassConstants.PUBLIC_BROADCAST_UUID);
                    pbData = PublicBroadcastData.parsePublicBroadcastData(pbAnnouncement);
                }
            }
            // Check if broadcast name present in scan record and parse
            // null if no name present
            String broadcastName = checkAndParseBroadcastName(scanRecord);

            // Avoid duplicated sync request if the same broadcast BIG is synced
            if (isSourceSynced(broadcastId)) {
                log("Skip duplicated sync request to broadcast id: " + broadcastId);
                return false;
            }

            PeriodicAdvertisingCallback paCb = new PACallback();
            // put temp sync handle and update in onSyncEstablished
            int tempHandle = BassConstants.INVALID_SYNC_HANDLE;
            mPeriodicAdvCallbacksMap.put(tempHandle, paCb);
            try {
                BluetoothMethodProxy.getInstance()
                        .periodicAdvertisingManagerRegisterSync(
                                BassClientPeriodicAdvertisingManager
                                        .getPeriodicAdvertisingManager(),
                                scanRes,
                                0,
                                BassConstants.PSYNC_TIMEOUT,
                                paCb,
                                null);
            } catch (IllegalArgumentException ex) {
                Log.w(TAG, "registerSync:IllegalArgumentException");
                mPeriodicAdvCallbacksMap.remove(tempHandle);
                return false;
            }

            mService.updatePeriodicAdvertisementResultMap(
                    scanRes.getDevice(),
                    scanRes.getDevice().getAddressType(),
                    BassConstants.INVALID_SYNC_HANDLE,
                    BassConstants.INVALID_ADV_SID,
                    scanRes.getPeriodicAdvertisingInterval(),
                    broadcastId,
                    pbData,
                    broadcastName);
        }
        return true;
    }

    private boolean isSourceSynced(int broadcastId) {
        List<Integer> activeSyncedSrc = mService.getActiveSyncedSources(mDevice);
        return (activeSyncedSrc != null
                && activeSyncedSrc.contains(mService.getSyncHandleForBroadcastId(broadcastId)));
    }

    private void cancelActiveSync(Integer syncHandle) {
        log("cancelActiveSync: syncHandle = " + syncHandle);
        if (syncHandle == null) {
            // clean up the pending sync request if syncHandle is null
            mPeriodicAdvCallbacksMap.remove(BassConstants.INVALID_SYNC_HANDLE);
        }
        List<Integer> activeSyncedSrc = mService.getActiveSyncedSources(mDevice);

        /* Stop sync if there is some running */
        if (activeSyncedSrc != null
                && (syncHandle == null || activeSyncedSrc.contains(syncHandle))) {
            if (syncHandle != null) {
                // only one source needs to be unsynced
                unsyncSource(syncHandle);
                mService.removeActiveSyncedSource(mDevice, syncHandle);
            } else {
                // remove all the sources
                for (int handle : activeSyncedSrc) {
                    unsyncSource(handle);
                }
                mService.removeActiveSyncedSource(mDevice, null);
            }
            if (mService.getActiveSyncedSources(mDevice) == null) {
                // all sources are removed, clean up
                removeMessages(PSYNC_ACTIVE_TIMEOUT);
                mService.clearNotifiedFlags();
            }
        }
    }

    private boolean unsyncSource(int syncHandle) {
        if (syncHandle != BassConstants.INVALID_SYNC_HANDLE
                && mPeriodicAdvCallbacksMap.containsKey(syncHandle)) {
            try {
                BluetoothMethodProxy.getInstance()
                        .periodicAdvertisingManagerUnregisterSync(
                                BassClientPeriodicAdvertisingManager
                                        .getPeriodicAdvertisingManager(),
                                mPeriodicAdvCallbacksMap.get(syncHandle));
            } catch (IllegalArgumentException ex) {
                Log.w(TAG, "unregisterSync:IllegalArgumentException");
                return false;
            }
            mPeriodicAdvCallbacksMap.remove(syncHandle);
        } else {
            log("calling unregisterSync, not found syncHandle: " + syncHandle);
        }
        return true;
    }

    private void resetBluetoothGatt() {
        // cleanup mBluetoothGatt
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }

    private BluetoothLeBroadcastMetadata getBroadcastMetadataFromBaseData(
            BaseData baseData, BluetoothDevice device, int syncHandle) {
        return getBroadcastMetadataFromBaseData(baseData, device, syncHandle, false);
    }

    private BluetoothLeBroadcastMetadata getBroadcastMetadataFromBaseData(
            BaseData baseData, BluetoothDevice device, int syncHandle, boolean encrypted) {
        BluetoothLeBroadcastMetadata.Builder metaData =
                new BluetoothLeBroadcastMetadata.Builder();
        int index = 0;
        for (BaseData.BaseInformation baseLevel2 : baseData.getLevelTwo()) {
            BluetoothLeBroadcastSubgroup.Builder subGroup =
                    new BluetoothLeBroadcastSubgroup.Builder();
            for (int j = 0; j < baseLevel2.numSubGroups; j ++) {
                BaseData.BaseInformation baseLevel3 =
                        baseData.getLevelThree().get(index++);
                BluetoothLeBroadcastChannel.Builder channel =
                        new BluetoothLeBroadcastChannel.Builder();
                channel.setChannelIndex(baseLevel3.index);
                channel.setSelected(false);
                try {
                    channel.setCodecMetadata(
                            BluetoothLeAudioCodecConfigMetadata.fromRawBytes(
                                    baseLevel3.codecConfigInfo));
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Invalid metadata, adding empty data. Error: " + e);
                    channel.setCodecMetadata(
                            BluetoothLeAudioCodecConfigMetadata.fromRawBytes(new byte[0]));
                }
                subGroup.addChannel(channel.build());
            }
            byte[] arrayCodecId = baseLevel2.codecId;
            long codeId = ((long) (arrayCodecId[4] & 0xff)) << 32
                    | (arrayCodecId[3] & 0xff) << 24
                    | (arrayCodecId[2] & 0xff) << 16
                    | (arrayCodecId[1] & 0xff) << 8
                    | (arrayCodecId[0] & 0xff);
            subGroup.setCodecId(codeId);
            try {
                subGroup.setCodecSpecificConfig(
                        BluetoothLeAudioCodecConfigMetadata.fromRawBytes(
                                baseLevel2.codecConfigInfo));
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid config, adding empty one. Error: " + e);
                subGroup.setCodecSpecificConfig(
                        BluetoothLeAudioCodecConfigMetadata.fromRawBytes(new byte[0]));
            }

            try {
                subGroup.setContentMetadata(
                        BluetoothLeAudioContentMetadata.fromRawBytes(baseLevel2.metaData));
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid metadata, adding empty one. Error: " + e);
                subGroup.setContentMetadata(
                        BluetoothLeAudioContentMetadata.fromRawBytes(new byte[0]));
            }

            metaData.addSubgroup(subGroup.build());
        }
        metaData.setSourceDevice(device, device.getAddressType());
        byte[] arrayPresentationDelay = baseData.getLevelOne().presentationDelay;
        int presentationDelay =
                (int)
                        ((arrayPresentationDelay[2] & 0xff) << 16
                                | (arrayPresentationDelay[1] & 0xff) << 8
                                | (arrayPresentationDelay[0] & 0xff));
        metaData.setPresentationDelayMicros(presentationDelay);
        PeriodicAdvertisementResult result =
                mService.getPeriodicAdvertisementResult(
                        device, mService.getBroadcastIdForSyncHandle(syncHandle));
        if (result != null) {
            int broadcastId = result.getBroadcastId();
            log("broadcast ID: " + broadcastId);
            metaData.setBroadcastId(broadcastId);
            metaData.setSourceAdvertisingSid(result.getAdvSid());

            PublicBroadcastData pbData = result.getPublicBroadcastData();
            if (pbData != null) {
                metaData.setPublicBroadcast(true);
                metaData.setAudioConfigQuality(pbData.getAudioConfigQuality());
                try {
                    metaData.setPublicBroadcastMetadata(
                            BluetoothLeAudioContentMetadata.fromRawBytes(pbData.getMetadata()));
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Invalid public metadata, adding empty one. Error " + e);
                    metaData.setPublicBroadcastMetadata(null);
                }
            }

            String broadcastName = result.getBroadcastName();
            if (broadcastName != null) {
                metaData.setBroadcastName(broadcastName);
            }
        }
        metaData.setEncrypted(encrypted);
        if (mFeatureFlags.leaudioBroadcastMonitorSourceSyncStatus()) {
            // update the rssi value
            ScanResult scanRes = mService.getCachedBroadcast(result.getBroadcastId());
            if (scanRes != null) {
                metaData.setRssi(scanRes.getRssi());
            }
        }
        return metaData.build();
    }

    private void broadcastReceiverState(
            BluetoothLeBroadcastReceiveState state, int sourceId) {
        log("broadcastReceiverState: " + mDevice);
        mService.getCallbacks().notifyReceiveStateChanged(mDevice, sourceId, state);
    }

    @VisibleForTesting
    static boolean isEmpty(final byte[] data) {
        return IntStream.range(0, data.length).parallel().allMatch(i -> data[i] == 0);
    }

    private void processPASyncState(BluetoothLeBroadcastReceiveState recvState) {
        int serviceData = 0;
        if (recvState == null) {
            Log.e(TAG, "processPASyncState: recvState is null");
            return;
        }
        int state = recvState.getPaSyncState();
        if (state == BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_SYNCINFO_REQUEST) {
            log("Initiate PAST procedure");
            PeriodicAdvertisementResult result =
                    mService.getPeriodicAdvertisementResult(
                            recvState.getSourceDevice(), recvState.getBroadcastId());
            if (result != null) {
                int syncHandle = result.getSyncHandle();
                log("processPASyncState: syncHandle " + result.getSyncHandle());
                if (syncHandle != BassConstants.INVALID_SYNC_HANDLE) {
                    serviceData = 0x000000FF & recvState.getSourceId();
                    serviceData = serviceData << 8;
                    //advA matches EXT_ADV_ADDRESS
                    //also matches source address (as we would have written)
                    serviceData = serviceData
                            & (~BassConstants.ADV_ADDRESS_DONT_MATCHES_EXT_ADV_ADDRESS);
                    serviceData = serviceData
                            & (~BassConstants.ADV_ADDRESS_DONT_MATCHES_SOURCE_ADV_ADDRESS);
                    log("Initiate PAST for: " + mDevice + ", syncHandle: " +  syncHandle
                            + "serviceData" + serviceData);
                    BluetoothMethodProxy.getInstance()
                            .periodicAdvertisingManagerTransferSync(
                                    BassClientPeriodicAdvertisingManager
                                            .getPeriodicAdvertisingManager(),
                                    mDevice,
                                    serviceData,
                                    syncHandle);
                }
            } else {
                BluetoothLeBroadcastMetadata currentMetadata =
                        getCurrentBroadcastMetadata(recvState.getSourceId());
                if (mService.isLocalBroadcast(currentMetadata)) {
                    int advHandle = currentMetadata.getSourceAdvertisingSid();
                    serviceData = 0x000000FF & recvState.getSourceId();
                    serviceData = serviceData << 8;
                    // Address we set in the Source Address can differ from the address in the air
                    serviceData = serviceData
                            | BassConstants.ADV_ADDRESS_DONT_MATCHES_SOURCE_ADV_ADDRESS;
                    log("Initiate local broadcast PAST for: " + mDevice
                            + ", advSID/Handle: " +  advHandle
                            + ", serviceData: " + serviceData);
                    BluetoothMethodProxy.getInstance()
                            .periodicAdvertisingManagerTransferSetInfo(
                                    BassClientPeriodicAdvertisingManager
                                            .getPeriodicAdvertisingManager(),
                                    mDevice,
                                    serviceData,
                                    advHandle,
                                    mLocalPeriodicAdvCallback);
                } else {
                    Log.e(TAG, "There is no valid sync handle for this Source");
                }
            }
        }
    }

    private void checkAndUpdateBroadcastCode(BluetoothLeBroadcastReceiveState recvState) {
        log("checkAndUpdateBroadcastCode");
        // non colocated case, Broadcast PIN should have been updated from lyaer
        // If there is pending one process it Now
        if (recvState.getBigEncryptionState()
                == BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_CODE_REQUIRED
                && mSetBroadcastCodePending) {
            log("Update the Broadcast now");
            if (mSetBroadcastPINMetadata != null) {
                setCurrentBroadcastMetadata(recvState.getSourceId(),
                        mSetBroadcastPINMetadata);
            }
            Message m = obtainMessage(BassClientStateMachine.SET_BCAST_CODE);
            m.obj = recvState;
            m.arg1 = ARGTYPE_RCVSTATE;
            sendMessage(m);
            mSetBroadcastCodePending = false;
            mSetBroadcastPINMetadata = null;
        }
    }

    private BluetoothLeBroadcastReceiveState parseBroadcastReceiverState(
            byte[] receiverState) {
        byte sourceId = 0;
        if (receiverState.length > 0) {
            sourceId = receiverState[BassConstants.BCAST_RCVR_STATE_SRC_ID_IDX];
        }
        log("processBroadcastReceiverState: receiverState length: " + receiverState.length);

        BluetoothLeBroadcastReceiveState recvState = null;
        if (receiverState.length == 0
                || isEmpty(Arrays.copyOfRange(receiverState, 1, receiverState.length - 1))) {
            byte[] emptyBluetoothDeviceAddress = Utils.getBytesFromAddress("00:00:00:00:00:00");
            if (mPendingOperation == REMOVE_BCAST_SOURCE) {
                recvState =
                        new BluetoothLeBroadcastReceiveState(
                                mPendingSourceId,
                                BluetoothDevice.ADDRESS_TYPE_PUBLIC, // sourceAddressType
                                mAdapterService.getDeviceFromByte(
                                        emptyBluetoothDeviceAddress), // sourceDev
                                0, // sourceAdvertisingSid
                                0, // broadcastId
                                BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE, // paSyncState
                                // bigEncryptionState
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                                null, // badCode
                                0, // numSubgroups
                                Arrays.asList(new Long[0]), // bisSyncState
                                Arrays.asList(
                                        new BluetoothLeAudioContentMetadata[0]) // subgroupMetadata
                                );
            } else if (receiverState.length == 0) {
                if (mBluetoothLeBroadcastReceiveStates != null) {
                    mNextSourceId = (byte) mBluetoothLeBroadcastReceiveStates.size();
                }
                if (mNextSourceId >= mNumOfBroadcastReceiverStates) {
                    Log.e(TAG, "reached the remote supported max SourceInfos");
                    return null;
                }
                mNextSourceId++;
                recvState =
                        new BluetoothLeBroadcastReceiveState(
                                mNextSourceId,
                                BluetoothDevice.ADDRESS_TYPE_PUBLIC, // sourceAddressType
                                mAdapterService.getDeviceFromByte(
                                        emptyBluetoothDeviceAddress), // sourceDev
                                0, // sourceAdvertisingSid
                                0, // broadcastId
                                BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE, // paSyncState
                                // bigEncryptionState
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                                null, // badCode
                                0, // numSubgroups
                                Arrays.asList(new Long[0]), // bisSyncState
                                Arrays.asList(
                                        new BluetoothLeAudioContentMetadata[0]) // subgroupMetadata
                                );
            }
        } else {
            byte paSyncState = receiverState[BassConstants.BCAST_RCVR_STATE_PA_SYNC_IDX];
            byte bigEncryptionStatus = receiverState[BassConstants.BCAST_RCVR_STATE_ENC_STATUS_IDX];
            byte[] badBroadcastCode = null;
            int badBroadcastCodeLen = 0;
            if (bigEncryptionStatus
                    == BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_BAD_CODE) {
                badBroadcastCode = new byte[BassConstants.BCAST_RCVR_STATE_BADCODE_SIZE];
                System.arraycopy(
                        receiverState,
                        BassConstants.BCAST_RCVR_STATE_BADCODE_START_IDX,
                        badBroadcastCode,
                        0,
                        BassConstants.BCAST_RCVR_STATE_BADCODE_SIZE);
                badBroadcastCodeLen = BassConstants.BCAST_RCVR_STATE_BADCODE_SIZE;
            }
            byte numSubGroups = receiverState[BassConstants.BCAST_RCVR_STATE_BADCODE_START_IDX
                    + badBroadcastCodeLen];
            int offset = BassConstants.BCAST_RCVR_STATE_BADCODE_START_IDX
                    + badBroadcastCodeLen + 1;
            ArrayList<BluetoothLeAudioContentMetadata> metadataList =
                    new ArrayList<BluetoothLeAudioContentMetadata>();
            ArrayList<Long> bisSyncState = new ArrayList<Long>();
            for (int i = 0; i < numSubGroups; i++) {
                byte[] bisSyncIndex = new byte[BassConstants.BCAST_RCVR_STATE_BIS_SYNC_SIZE];
                System.arraycopy(receiverState, offset, bisSyncIndex, 0,
                        BassConstants.BCAST_RCVR_STATE_BIS_SYNC_SIZE);
                offset += BassConstants.BCAST_RCVR_STATE_BIS_SYNC_SIZE;
                bisSyncState.add((long) Utils.byteArrayToInt(bisSyncIndex));

                byte metaDataLength = receiverState[offset++];
                if (metaDataLength > 0) {
                    log("metadata of length: " + metaDataLength + "is available");
                    byte[] metaData = new byte[metaDataLength];
                    System.arraycopy(receiverState, offset, metaData, 0, metaDataLength);
                    offset += metaDataLength;
                    metadataList.add(BluetoothLeAudioContentMetadata.fromRawBytes(metaData));
                } else {
                    metadataList.add(BluetoothLeAudioContentMetadata.fromRawBytes(new byte[0]));
                }
            }
            byte[] broadcastIdBytes = new byte[mBroadcastSourceIdLength];
            System.arraycopy(
                    receiverState,
                    BassConstants.BCAST_RCVR_STATE_SRC_BCAST_ID_START_IDX,
                    broadcastIdBytes,
                    0,
                    mBroadcastSourceIdLength);
            int broadcastId = BassUtils.parseBroadcastId(broadcastIdBytes);
            byte[] sourceAddress = new byte[BassConstants.BCAST_RCVR_STATE_SRC_ADDR_SIZE];
            System.arraycopy(
                    receiverState,
                    BassConstants.BCAST_RCVR_STATE_SRC_ADDR_START_IDX,
                    sourceAddress,
                    0,
                    BassConstants.BCAST_RCVR_STATE_SRC_ADDR_SIZE);
            byte sourceAddressType = receiverState[BassConstants
                    .BCAST_RCVR_STATE_SRC_ADDR_TYPE_IDX];
            BassUtils.reverse(sourceAddress);
            BluetoothDevice device = mAdapterService.getDeviceFromByte(sourceAddress);
            byte sourceAdvSid = receiverState[BassConstants.BCAST_RCVR_STATE_SRC_ADV_SID_IDX];
            recvState = new BluetoothLeBroadcastReceiveState(
                    sourceId,
                    (int) sourceAddressType,
                    device,
                    sourceAdvSid,
                    broadcastId,
                    (int) paSyncState,
                    (int) bigEncryptionStatus,
                    badBroadcastCode,
                    numSubGroups,
                    bisSyncState,
                    metadataList);
        }
        return recvState;
    }

    private void processBroadcastReceiverState(
            byte[] receiverState, BluetoothGattCharacteristic characteristic) {
        log("processBroadcastReceiverState: characteristic:" + characteristic);
        BluetoothLeBroadcastReceiveState recvState = parseBroadcastReceiverState(
                receiverState);
        if (recvState == null) {
            log("processBroadcastReceiverState: Null recvState");
            return;
        } else if (recvState.getSourceId() == -1) {
            log("processBroadcastReceiverState: invalid index: " + recvState.getSourceId());
            return;
        }
        BluetoothLeBroadcastReceiveState oldRecvState =
                mBluetoothLeBroadcastReceiveStates.get(characteristic.getInstanceId());
        if (oldRecvState == null) {
            log("Initial Read and Populating values");
            if (mBluetoothLeBroadcastReceiveStates.size() == mNumOfBroadcastReceiverStates) {
                Log.e(TAG, "reached the Max SourceInfos");
                return;
            }
            mBluetoothLeBroadcastReceiveStates.put(characteristic.getInstanceId(), recvState);
            checkAndUpdateBroadcastCode(recvState);
            processPASyncState(recvState);
        } else {
            log("Updated receiver state: " + recvState);
            mBluetoothLeBroadcastReceiveStates.replace(characteristic.getInstanceId(), recvState);
            String emptyBluetoothDevice = "00:00:00:00:00:00";
            if (oldRecvState.getSourceDevice() == null
                    || oldRecvState.getSourceDevice().getAddress().equals(emptyBluetoothDevice)) {
                log("New Source Addition");
                mService.getCallbacks()
                        .notifySourceAdded(
                                mDevice, recvState, BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);
                if (mPendingMetadata != null) {
                    setCurrentBroadcastMetadata(recvState.getSourceId(), mPendingMetadata);
                    mPendingMetadata = null;
                }
                checkAndUpdateBroadcastCode(recvState);
                processPASyncState(recvState);
            } else {
                if (recvState.getSourceDevice() == null
                        || recvState.getSourceDevice().getAddress().equals(emptyBluetoothDevice)) {
                    BluetoothDevice removedDevice = oldRecvState.getSourceDevice();
                    log("sourceInfo removal" + removedDevice);
                    cancelActiveSync(
                            mService.getSyncHandleForBroadcastId(recvState.getBroadcastId()));
                    setCurrentBroadcastMetadata(oldRecvState.getSourceId(), null);
                    if (mPendingSourceToSwitch != null) {
                        // Source remove is triggered by switch source request
                        mService.getCallbacks()
                                .notifySourceRemoved(
                                        mDevice,
                                        oldRecvState.getSourceId(),
                                        BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST);
                        log("Switching to new source");
                        Message message = obtainMessage(ADD_BCAST_SOURCE);
                        message.obj = mPendingSourceToSwitch;
                        sendMessage(message);
                        mPendingSourceToSwitch = null;
                    } else {
                        mService.getCallbacks()
                                .notifySourceRemoved(
                                        mDevice,
                                        oldRecvState.getSourceId(),
                                        BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);
                    }
                } else {
                    log("update to an existing recvState");
                    if (mPendingMetadata != null) {
                        setCurrentBroadcastMetadata(recvState.getSourceId(), mPendingMetadata);
                        mPendingMetadata = null;
                    }
                    mService.getCallbacks().notifySourceModified(mDevice,
                            recvState.getSourceId(), BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);
                    checkAndUpdateBroadcastCode(recvState);
                    processPASyncState(recvState);

                    if (isPendingRemove(recvState.getSourceId())) {
                        Message message = obtainMessage(REMOVE_BCAST_SOURCE);
                        message.arg1 = recvState.getSourceId();
                        sendMessage(message);
                    }
                }
            }
        }
        broadcastReceiverState(recvState, recvState.getSourceId());
    }

    // Implements callback methods for GATT events that the app cares about.
    // For example, connection change and services discovered.
    final class GattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            boolean isStateChanged = false;
            log("onConnectionStateChange : Status=" + status + "newState" + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED
                    && getConnectionState() != BluetoothProfile.STATE_CONNECTED) {
                isStateChanged = true;
                Log.w(TAG, "Bassclient Connected from Disconnected state: " + mDevice);
                if (mService.okToConnect(mDevice)) {
                    log("Bassclient Connected to: " + mDevice);
                    if (mBluetoothGatt != null) {
                        log("Attempting to start service discovery:"
                                + mBluetoothGatt.discoverServices());
                        mDiscoveryInitiated = true;
                    }
                } else if (mBluetoothGatt != null) {
                    // Reject the connection
                    Log.w(TAG, "Bassclient Connect request rejected: " + mDevice);
                    mBluetoothGatt.disconnect();
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;
                    // force move to disconnected
                    newState = BluetoothProfile.STATE_DISCONNECTED;
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED
                    && getConnectionState() != BluetoothProfile.STATE_DISCONNECTED) {
                isStateChanged = true;
                log("Disconnected from Bass GATT server.");
            }
            if (isStateChanged) {
                Message m = obtainMessage(CONNECTION_STATE_CHANGED);
                m.obj = newState;
                sendMessage(m);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            log("onServicesDiscovered:" + status);
            if (mDiscoveryInitiated) {
                mDiscoveryInitiated = false;
                if (status == BluetoothGatt.GATT_SUCCESS && mBluetoothGatt != null) {
                    mBluetoothGatt.requestMtu(BassConstants.BASS_MAX_BYTES);
                    mMTUChangeRequested = true;
                } else {
                    Log.w(TAG, "onServicesDiscovered received: "
                            + status + "mBluetoothGatt" + mBluetoothGatt);
                }
            } else {
                log("remote initiated callback");
            }
        }

        @Override
        public void onCharacteristicRead(
                BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic,
                int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.getUuid()
                    .equals(BassConstants.BASS_BCAST_RECEIVER_STATE)) {
                log("onCharacteristicRead: BASS_BCAST_RECEIVER_STATE: status" + status);
                if (characteristic.getValue() == null) {
                    Log.e(TAG, "Remote receiver state is NULL");
                    return;
                }
                logByteArray("Received ", characteristic.getValue(), 0,
                        characteristic.getValue().length);
                processBroadcastReceiverState(characteristic.getValue(), characteristic);
            }
            // switch to receiving notifications after initial characteristic read
            BluetoothGattDescriptor desc = characteristic
                    .getDescriptor(BassConstants.CLIENT_CHARACTERISTIC_CONFIG);
            if (mBluetoothGatt != null && desc != null) {
                log("Setting the value for Desc");
                mBluetoothGatt.setCharacteristicNotification(characteristic, true);
                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mBluetoothGatt.writeDescriptor(desc);
            } else {
                Log.w(TAG, "CCC for " + characteristic + "seem to be not present");
                // at least move the SM to stable state
                Message m = obtainMessage(GATT_TXN_PROCESSED);
                m.arg1 = status;
                sendMessage(m);
            }
        }

        @Override
        public void onDescriptorWrite(
                BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            // Move the SM to connected so further reads happens
            Message m = obtainMessage(GATT_TXN_PROCESSED);
            m.arg1 = status;
            sendMessage(m);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (mMTUChangeRequested && mBluetoothGatt != null) {
                acquireAllBassChars();
                mMTUChangeRequested = false;
            } else {
                log("onMtuChanged is remote initiated trigger, mBluetoothGatt:"
                        + mBluetoothGatt);
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "mtu: " + mtu);
                mMaxSingleAttributeWriteValueLen = mtu - ATT_WRITE_CMD_HDR_LEN;
            }
        }

        @Override
        public void onCharacteristicChanged(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(BassConstants.BASS_BCAST_RECEIVER_STATE)) {
                if (characteristic.getValue() == null) {
                    Log.e(TAG, "Remote receiver state is NULL");
                    return;
                }
                processBroadcastReceiverState(characteristic.getValue(), characteristic);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            Message m = obtainMessage(GATT_TXN_PROCESSED);
            m.arg1 = status;
            sendMessage(m);
        }
    }

    /** Internal periodc Advertising manager callback */
    private final class PACallback extends PeriodicAdvertisingCallback {
        @Override
        public void onSyncEstablished(
                int syncHandle,
                BluetoothDevice device,
                int advertisingSid,
                int skip,
                int timeout,
                int status) {
            log("onSyncEstablished syncHandle: " + syncHandle
                    + ", device: " + device
                    + ", advertisingSid: " + advertisingSid
                    + ", skip: " + skip
                    + ", timeout: " + timeout
                    + ", status: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // updates syncHandle, advSid
                // set other fields as invalid or null
                mService.updatePeriodicAdvertisementResultMap(
                        device,
                        BassConstants.INVALID_ADV_ADDRESS_TYPE,
                        syncHandle,
                        advertisingSid,
                        BassConstants.INVALID_ADV_INTERVAL,
                        BassConstants.INVALID_BROADCAST_ID,
                        null,
                        null);
                removeMessages(PSYNC_ACTIVE_TIMEOUT);
                // Refresh sync timeout if another source synced
                sendMessageDelayed(
                        PSYNC_ACTIVE_TIMEOUT, BassConstants.PSYNC_ACTIVE_TIMEOUT_MS);
                mService.addActiveSyncedSource(mDevice, syncHandle);

                // update valid sync handle in mPeriodicAdvCallbacksMap
                if (mPeriodicAdvCallbacksMap.containsKey(BassConstants.INVALID_SYNC_HANDLE)) {
                    PeriodicAdvertisingCallback paCb =
                            mPeriodicAdvCallbacksMap.get(BassConstants.INVALID_SYNC_HANDLE);
                    mPeriodicAdvCallbacksMap.put(syncHandle, paCb);
                    mPeriodicAdvCallbacksMap.remove(BassConstants.INVALID_SYNC_HANDLE);
                }
                mFirstTimeBisDiscoveryMap.put(syncHandle, true);
                if (mPendingSourceToAdd != null) {
                    Message message = obtainMessage(ADD_BCAST_SOURCE);
                    message.obj = mPendingSourceToAdd;
                    sendMessage(message);
                }
            } else {
                log("failed to sync to PA: " + mPASyncRetryCounter);
                mAutoTriggered = false;
                // remove failed sync handle
                mPeriodicAdvCallbacksMap.remove(BassConstants.INVALID_SYNC_HANDLE);
            }
            mPendingSourceToAdd = null;
            if (!mSourceSyncRequestsQueue.isEmpty()) {
                log("Processing the next source to sync");
                Pair<ScanResult, Integer> queuedSourceToSync = mSourceSyncRequestsQueue.remove(0);
                Message msg = obtainMessage(SELECT_BCAST_SOURCE);
                msg.obj = queuedSourceToSync.first;
                msg.arg1 = queuedSourceToSync.second;
                sendMessage(msg);
            }
        }

        @Override
        public void onPeriodicAdvertisingReport(PeriodicAdvertisingReport report) {
            log("onPeriodicAdvertisingReport");
            Boolean first = mFirstTimeBisDiscoveryMap.get(report.getSyncHandle());
            // Parse the BIS indices from report's service data
            if (first != null && first.booleanValue() == true) {
                parseScanRecord(report.getSyncHandle(), report.getData());
                mFirstTimeBisDiscoveryMap.put(report.getSyncHandle(), false);
            }
        }

        @Override
        public void onSyncLost(int syncHandle) {
            log("OnSyncLost" + syncHandle);
            if (mFeatureFlags.leaudioBroadcastMonitorSourceSyncStatus()) {
                int broadcastId = mService.getBroadcastIdForSyncHandle(syncHandle);
                if (broadcastId != BassConstants.INVALID_BROADCAST_ID) {
                    log("Notify broadcast source lost, broadcast id: " + broadcastId);
                    mService.getCallbacks().notifySourceLost(broadcastId);
                }
            }
            cancelActiveSync(syncHandle);
        }

        @Override
        public void onBigInfoAdvertisingReport(int syncHandle, boolean encrypted) {
            log(
                    "onBIGInfoAdvertisingReport: syncHandle="
                            + syncHandle
                            + " ,encrypted ="
                            + encrypted);
            BluetoothDevice srcDevice = mService.getDeviceForSyncHandle(syncHandle);
            if (srcDevice == null) {
                log("No device found.");
                return;
            }
            PeriodicAdvertisementResult result =
                    mService.getPeriodicAdvertisementResult(
                            srcDevice, mService.getBroadcastIdForSyncHandle(syncHandle));
            if (result == null) {
                log("No PA record found");
                return;
            }
            if (!result.isNotified()) {
                result.setNotified(true);
                BaseData baseData = mService.getBase(syncHandle);
                if (baseData == null) {
                    log("No BaseData found");
                    return;
                }
                BluetoothLeBroadcastMetadata metaData =
                        getBroadcastMetadataFromBaseData(
                                baseData, srcDevice, syncHandle, encrypted);
                log("Notify broadcast source found");
                mService.getCallbacks().notifySourceFound(metaData);
            }
        }

        @Override
        public void onSyncTransferred(BluetoothDevice device, int status) {
            log("onSyncTransferred: device=" + device +
                    " ,status =" + status);
        }
    }

    /**
     * Connects to the GATT server of the device.
     *
     * @return {@code true} if it successfully connects to the GATT server.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public boolean connectGatt(Boolean autoConnect) {
        if (mGattCallback == null) {
            mGattCallback = new GattCallback();
        }

        BluetoothGatt gatt = mDevice.connectGatt(mService, autoConnect,
                mGattCallback, BluetoothDevice.TRANSPORT_LE,
                (BluetoothDevice.PHY_LE_1M_MASK
                        | BluetoothDevice.PHY_LE_2M_MASK
                        | BluetoothDevice.PHY_LE_CODED_MASK), null);

        if (gatt != null) {
            mBluetoothGatt = new BluetoothGattTestableWrapper(gatt);
        }

        return mBluetoothGatt != null;
    }

    /**
     * getAllSources
     */
    public List<BluetoothLeBroadcastReceiveState> getAllSources() {
        List list = new ArrayList(mBluetoothLeBroadcastReceiveStates.values());
        return list;
    }

    void acquireAllBassChars() {
        clearCharsCache();
        BluetoothGattService service = null;
        if (mBluetoothGatt != null) {
            log("getting Bass Service handle");
            service = mBluetoothGatt.getService(BassConstants.BASS_UUID);
        }
        if (service == null) {
            log("acquireAllBassChars: BASS service not found");
            return;
        }
        log("found BASS_SERVICE");
        List<BluetoothGattCharacteristic> allChars = service.getCharacteristics();
        int numOfChars = allChars.size();
        mNumOfBroadcastReceiverStates = numOfChars - 1;
        log("Total number of chars" + numOfChars);
        for (int i = 0; i < allChars.size(); i++) {
            if (allChars.get(i).getUuid().equals(BassConstants.BASS_BCAST_AUDIO_SCAN_CTRL_POINT)) {
                int properties = allChars.get(i).getProperties();

                if (((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0)
                        || ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0)) {
                    Log.w(TAG, "Broadcast Audio Scan Control Point characteristic has invalid "
                            + "properties!");
                } else {
                    mBroadcastScanControlPoint = allChars.get(i);
                    log("Index of ScanCtrlPoint:" + i);
                }
            } else {
                log("Reading " + i + "th ReceiverState");
                mBroadcastCharacteristics.add(allChars.get(i));
                Message m = obtainMessage(READ_BASS_CHARACTERISTICS);
                m.obj = allChars.get(i);
                sendMessage(m);
            }
        }
    }

    void clearCharsCache() {
        if (mBroadcastCharacteristics != null) {
            mBroadcastCharacteristics.clear();
        }
        if (mBroadcastScanControlPoint != null) {
            mBroadcastScanControlPoint = null;
        }
        mNumOfBroadcastReceiverStates = 0;
        if (mBluetoothLeBroadcastReceiveStates != null) {
            mBluetoothLeBroadcastReceiveStates.clear();
        }
        mPendingOperation = -1;
        mPendingMetadata = null;
        mCurrentMetadata.clear();
        mPendingRemove.clear();
    }

    @VisibleForTesting
    class Disconnected extends State {
        @Override
        public void enter() {
            log("Enter Disconnected(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            clearCharsCache();
            mNextSourceId = 0;
            removeDeferredMessages(DISCONNECT);
            if (mLastConnectionState == -1) {
                log("no Broadcast of initial profile state ");
            } else {
                broadcastConnectionState(
                        mDevice, mLastConnectionState, BluetoothProfile.STATE_DISCONNECTED);
                if (mLastConnectionState != BluetoothProfile.STATE_DISCONNECTED) {
                    // Reconnect in background if not disallowed by the service
                    if (mService.okToConnect(mDevice) && mAllowReconnect) {
                        connectGatt(true);
                    }
                }
            }
        }

        @Override
        public void exit() {
            log("Exit Disconnected(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            mLastConnectionState = BluetoothProfile.STATE_DISCONNECTED;
        }

        @Override
        public boolean processMessage(Message message) {
            log("Disconnected process message(" + mDevice
                    + "): " + messageWhatToString(message.what));
            switch (message.what) {
                case CONNECT:
                    log("Connecting to " + mDevice);
                    if (mBluetoothGatt != null) {
                        Log.d(TAG, "clear off, pending wl connection");
                        mBluetoothGatt.disconnect();
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                    }
                    mAllowReconnect = true;
                    if (connectGatt(mIsAllowedList)) {
                        transitionTo(mConnecting);
                    } else {
                        Log.e(TAG, "Disconnected: error connecting to " + mDevice);
                    }
                    break;
                case DISCONNECT:
                    // Disconnect if there's an ongoing background connection
                    mAllowReconnect = false;
                    if (mBluetoothGatt != null) {
                        log("Cancelling the background connection to " + mDevice);
                        mBluetoothGatt.disconnect();
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                    } else {
                        Log.d(TAG, "Disconnected: DISCONNECT ignored: " + mDevice);
                    }
                    break;
                case CONNECTION_STATE_CHANGED:
                    int state = (int) message.obj;
                    Log.w(TAG, "connection state changed:" + state);
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        log("remote/wl connection");
                        transitionTo(mConnected);
                    } else {
                        Log.w(TAG, "Disconnected: Connection failed to " + mDevice);
                    }
                    break;
                case PSYNC_ACTIVE_TIMEOUT:
                    cancelActiveSync(null);
                    break;
                default:
                    log("DISCONNECTED: not handled message:" + message.what);
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    @VisibleForTesting
    class Connecting extends State {
        @Override
        public void enter() {
            log("Enter Connecting(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            sendMessageDelayed(CONNECT_TIMEOUT, mDevice, mConnectTimeoutMs);
            broadcastConnectionState(
                    mDevice, mLastConnectionState, BluetoothProfile.STATE_CONNECTING);
        }

        @Override
        public void exit() {
            log("Exit Connecting(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            mLastConnectionState = BluetoothProfile.STATE_CONNECTING;
            removeMessages(CONNECT_TIMEOUT);
        }

        @Override
        public boolean processMessage(Message message) {
            log("Connecting process message(" + mDevice + "): "
                    + messageWhatToString(message.what));
            switch (message.what) {
                case CONNECT:
                    log("Already Connecting to " + mDevice);
                    log("Ignore this connection request " + mDevice);
                    break;
                case DISCONNECT:
                    Log.w(TAG, "Connecting: DISCONNECT deferred: " + mDevice);
                    deferMessage(message);
                    break;
                case READ_BASS_CHARACTERISTICS:
                    Log.w(TAG, "defer READ_BASS_CHARACTERISTICS requested!: " + mDevice);
                    deferMessage(message);
                    break;
                case CONNECTION_STATE_CHANGED:
                    int state = (int) message.obj;
                    Log.w(TAG, "Connecting: connection state changed:" + state);
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        transitionTo(mConnected);
                    } else {
                        Log.w(TAG, "Connection failed to " + mDevice);
                        resetBluetoothGatt();
                        transitionTo(mDisconnected);
                    }
                    break;
                case CONNECT_TIMEOUT:
                    Log.w(TAG, "CONNECT_TIMEOUT");
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (!mDevice.equals(device)) {
                        Log.e(TAG, "Unknown device timeout " + device);
                        break;
                    }
                    resetBluetoothGatt();
                    transitionTo(mDisconnected);
                    break;
                case PSYNC_ACTIVE_TIMEOUT:
                    deferMessage(message);
                    break;
                default:
                    log("CONNECTING: not handled message:" + message.what);
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private static int getBisSyncFromChannelPreference(
                List<BluetoothLeBroadcastChannel> channels) {
        int bisSync = 0;
        for (BluetoothLeBroadcastChannel channel : channels) {
            if (channel.isSelected()) {
                if (channel.getChannelIndex() == 0) {
                    Log.e(TAG, "getBisSyncFromChannelPreference: invalid channel index=0");
                    continue;
                }
                bisSync |= 1 << (channel.getChannelIndex() - 1);
            }
        }

        return bisSync;
    }

    private byte[] convertMetadataToAddSourceByteArray(BluetoothLeBroadcastMetadata metaData) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        BluetoothDevice advSource = metaData.getSourceDevice();

        // Opcode
        stream.write(OPCODE_ADD_SOURCE);

        // Advertiser_Address_Type
        stream.write(metaData.getSourceAddressType());

        // Advertiser_Address
        byte[] bcastSourceAddr = Utils.getBytesFromAddress(advSource.getAddress());
        BassUtils.reverse(bcastSourceAddr);
        stream.write(bcastSourceAddr, 0, 6);

        // Advertising_SID
        stream.write(metaData.getSourceAdvertisingSid());

        // Broadcast_ID
        stream.write(metaData.getBroadcastId() & 0x00000000000000FF);
        stream.write((metaData.getBroadcastId() & 0x000000000000FF00) >>> 8);
        stream.write((metaData.getBroadcastId() & 0x0000000000FF0000) >>> 16);

        // PA_Sync
        if (mDefNoPAS) {
            // Synchronize to PA – PAST not available
            stream.write(0x02);
        } else {
            // Synchronize to PA – PAST available
            stream.write(0x01);
        }

        // PA_Interval
        stream.write((metaData.getPaSyncInterval() & 0x00000000000000FF));
        stream.write((metaData.getPaSyncInterval() & 0x000000000000FF00) >>> 8);

        // Num_Subgroups
        List<BluetoothLeBroadcastSubgroup> subGroups = metaData.getSubgroups();
        stream.write(metaData.getSubgroups().size());

        for (BluetoothLeBroadcastSubgroup subGroup : subGroups) {
            // BIS_Sync
            int bisSync = getBisSyncFromChannelPreference(subGroup.getChannels());
            if (bisSync == 0) {
                bisSync = 0xFFFFFFFF;
            }
            stream.write(bisSync & 0x00000000000000FF);
            stream.write((bisSync & 0x000000000000FF00) >>> 8);
            stream.write((bisSync & 0x0000000000FF0000) >>> 16);
            stream.write((bisSync & 0x00000000FF000000) >>> 24);

            // Metadata_Length
            BluetoothLeAudioContentMetadata metadata = subGroup.getContentMetadata();
            stream.write(metadata.getRawMetadata().length);

            // Metadata
            stream.write(metadata.getRawMetadata(), 0, metadata.getRawMetadata().length);
        }

        byte[] res = stream.toByteArray();
        BassUtils.printByteArray(res);
        return res;
    }

    private byte[] convertBroadcastMetadataToUpdateSourceByteArray(int sourceId,
            BluetoothLeBroadcastMetadata metaData, int paSync) {
        BluetoothLeBroadcastReceiveState existingState =
                getBroadcastReceiveStateForSourceId(sourceId);
        if (existingState == null) {
            log("no existing SI for update source op");
            return null;
        }
        byte numSubGroups = (byte) metaData.getSubgroups().size();
        byte[] res = new byte[UPDATE_SOURCE_FIXED_LENGTH + numSubGroups * 5];
        int offset = 0;
        // Opcode
        res[offset++] = OPCODE_UPDATE_SOURCE;
        // Source_ID
        res[offset++] = (byte) sourceId;
        // PA_Sync
        if (paSync != BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_INVALID) {
            res[offset++] = (byte) paSync;
        } else if (existingState.getPaSyncState()
                == BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_SYNCHRONIZED) {
            res[offset++] = (byte) (0x01);
        } else {
            res[offset++] = (byte) 0x00;
        }
        // PA_Interval
        res[offset++] = (byte) 0xFF;
        res[offset++] = (byte) 0xFF;
        // Num_Subgroups
        res[offset++] = numSubGroups;
        for (int i = 0; i < numSubGroups; i++) {
            int bisIndexValue;
            if (paSync != BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_INVALID) {
                bisIndexValue = 0;
            } else {
                bisIndexValue = existingState.getBisSyncState().get(i).intValue();
            }
            log("UPDATE_BCAST_SOURCE: bisIndexValue : " + bisIndexValue);
            // BIS_Sync
            res[offset++] = (byte) (bisIndexValue & 0x00000000000000FF);
            res[offset++] = (byte) ((bisIndexValue & 0x000000000000FF00) >>> 8);
            res[offset++] = (byte) ((bisIndexValue & 0x0000000000FF0000) >>> 16);
            res[offset++] = (byte) ((bisIndexValue & 0x00000000FF000000) >>> 24);
            // Metadata_Length; On Modify source, don't update any Metadata
            res[offset++] = 0;
        }
        log("UPDATE_BCAST_SOURCE in Bytes");
        BassUtils.printByteArray(res);
        return res;
    }

    private byte[] convertRecvStateToSetBroadcastCodeByteArray(
            BluetoothLeBroadcastReceiveState recvState) {
        byte[] res = new byte[BassConstants.PIN_CODE_CMD_LEN];
        // Opcode
        res[0] = OPCODE_SET_BCAST_PIN;
        // Source_ID
        res[1] = (byte) recvState.getSourceId();
        log("convertRecvStateToSetBroadcastCodeByteArray: Source device : "
                + recvState.getSourceDevice());
        BluetoothLeBroadcastMetadata metaData =
                getCurrentBroadcastMetadata(recvState.getSourceId());
        if (metaData == null) {
            Log.e(TAG, "Fail to find broadcast source, sourceId = "
                    + recvState.getSourceId());
            return null;
        }
        // Broadcast Code
        byte[] actualPIN = metaData.getBroadcastCode();
        if (actualPIN == null) {
            Log.e(TAG, "actual PIN is null");
            return null;
        } else {
            log("byte array broadcast Code:" + Arrays.toString(actualPIN));
            log("pinLength:" + actualPIN.length);
            // Broadcast_Code, Fill the PIN code in the Last Position
            // This effectively adds padding zeros to MSB positions when the broadcast code
            // is shorter than 16 octets, skip the first 2 bytes for opcode and source_id.
            System.arraycopy(actualPIN, 0, res, 2, actualPIN.length);
            log("SET_BCAST_PIN in Bytes");
            BassUtils.printByteArray(res);
        }
        return res;
    }

    private boolean isItRightTimeToUpdateBroadcastPin(byte sourceId) {
        Collection<BluetoothLeBroadcastReceiveState> recvStates =
                mBluetoothLeBroadcastReceiveStates.values();
        Iterator<BluetoothLeBroadcastReceiveState> iterator = recvStates.iterator();
        boolean retval = false;
        if (mForceSB) {
            log("force SB is set");
            return true;
        }
        while (iterator.hasNext()) {
            BluetoothLeBroadcastReceiveState state = iterator.next();
            if (state == null) {
                log("Source state is null");
                continue;
            }
            if (sourceId == state.getSourceId() && state.getBigEncryptionState()
                    == BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_CODE_REQUIRED) {
                retval = true;
                break;
            }
        }
        log("IsItRightTimeToUpdateBroadcastPIN returning:" + retval);
        return retval;
    }

    @VisibleForTesting
    class Connected extends State {
        @Override
        public void enter() {
            log("Enter Connected(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            removeDeferredMessages(CONNECT);
            if (mLastConnectionState == BluetoothProfile.STATE_CONNECTED) {
                log("CONNECTED->CONNECTED: Ignore");
                // Broadcast for testing purpose only
                if (Utils.isInstrumentationTestMode()) {
                    Intent intent = new Intent("android.bluetooth.bass_client.NOTIFY_TEST");
                    Utils.sendBroadcast(mService, intent, BLUETOOTH_CONNECT,
                            Utils.getTempAllowlistBroadcastOptions());
                }
            } else {
                broadcastConnectionState(mDevice, mLastConnectionState,
                        BluetoothProfile.STATE_CONNECTED);
            }
        }

        @Override
        public void exit() {
            log("Exit Connected(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            mLastConnectionState = BluetoothProfile.STATE_CONNECTED;
        }

        private void writeBassControlPoint(byte[] value) {
            if (value.length > mMaxSingleAttributeWriteValueLen) {
                mBroadcastScanControlPoint.setWriteType(
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                mBroadcastScanControlPoint.setWriteType(
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            }

            mBroadcastScanControlPoint.setValue(value);
            mBluetoothGatt.writeCharacteristic(mBroadcastScanControlPoint);
        }

        @Override
        public boolean processMessage(Message message) {
            log("Connected process message(" + mDevice + "): " + messageWhatToString(message.what));
            BluetoothLeBroadcastMetadata metaData;
            switch (message.what) {
                case CONNECT:
                    Log.w(TAG, "Connected: CONNECT ignored: " + mDevice);
                    break;
                case DISCONNECT:
                    log("Disconnecting from " + mDevice);
                    mAllowReconnect = false;
                    if (mBluetoothGatt != null) {
                        mService.handleDeviceDisconnection(mDevice, true);
                        mBluetoothGatt.disconnect();
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                        cancelActiveSync(null);
                        transitionTo(mDisconnected);
                    } else {
                        log("mBluetoothGatt is null");
                    }
                    break;
                case CONNECTION_STATE_CHANGED:
                    int state = (int) message.obj;
                    Log.w(TAG, "Connected:connection state changed:" + state);
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        Log.w(TAG, "device is already connected to Bass" + mDevice);
                    } else {
                        Log.w(TAG, "unexpected disconnected from " + mDevice);
                        mService.handleDeviceDisconnection(mDevice, false);
                        resetBluetoothGatt();
                        cancelActiveSync(null);
                        transitionTo(mDisconnected);
                    }
                    break;
                case READ_BASS_CHARACTERISTICS:
                    BluetoothGattCharacteristic characteristic =
                            (BluetoothGattCharacteristic) message.obj;
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.readCharacteristic(characteristic);
                        transitionTo(mConnectedProcessing);
                    } else {
                        Log.e(TAG, "READ_BASS_CHARACTERISTICS is ignored, Gatt handle is null");
                    }
                    break;
                case START_SCAN_OFFLOAD:
                    if (mBluetoothGatt != null && mBroadcastScanControlPoint != null) {
                        writeBassControlPoint(REMOTE_SCAN_START);
                        mPendingOperation = message.what;
                        transitionTo(mConnectedProcessing);
                    } else {
                        log("no Bluetooth Gatt handle, may need to fetch write");
                    }
                    break;
                case STOP_SCAN_OFFLOAD:
                    if (mBluetoothGatt != null && mBroadcastScanControlPoint != null) {
                        writeBassControlPoint(REMOTE_SCAN_STOP);
                        mPendingOperation = message.what;
                        transitionTo(mConnectedProcessing);
                    } else {
                        log("no Bluetooth Gatt handle, may need to fetch write");
                    }
                    break;
                case SELECT_BCAST_SOURCE:
                    ScanResult scanRes = (ScanResult) message.obj;
                    boolean auto = ((int) message.arg1) == BassConstants.AUTO;
                    // check if invalid sync handle exists indicating a pending sync request
                    if (mPeriodicAdvCallbacksMap.containsKey(BassConstants.INVALID_SYNC_HANDLE)) {
                        log(
                                "SELECT_BCAST_SOURCE queued due to waiting for a previous sync"
                                        + " response");
                        mSourceSyncRequestsQueue.add(
                                new Pair<ScanResult, Integer>(scanRes, message.arg1));
                    } else {
                        selectSource(scanRes, auto);
                    }
                    break;
                case REACHED_MAX_SOURCE_LIMIT:
                    int handle = message.arg1;
                    cancelActiveSync(handle);
                    break;
                case SWITCH_BCAST_SOURCE:
                    metaData = (BluetoothLeBroadcastMetadata) message.obj;
                    int sourceIdToRemove = message.arg1;
                    // Save pending source to be added once existing source got removed
                    mPendingSourceToSwitch = metaData;
                    // Remove the source first
                    BluetoothLeBroadcastMetadata metaDataToUpdate =
                            getCurrentBroadcastMetadata(sourceIdToRemove);
                    if (metaDataToUpdate != null && isSyncedToTheSource(sourceIdToRemove)) {
                        log("SWITCH_BCAST_SOURCE force source to lost PA sync");
                        Message msg = obtainMessage(UPDATE_BCAST_SOURCE);
                        msg.arg1 = sourceIdToRemove;
                        msg.arg2 = BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE;
                        msg.obj = metaDataToUpdate;
                        /* Pending remove set. Remove source once not synchronized to PA */
                        sendMessage(msg);
                    } else {
                        Message msg = obtainMessage(REMOVE_BCAST_SOURCE);
                        msg.arg1 = sourceIdToRemove;
                        sendMessage(msg);
                    }
                    break;
                case ADD_BCAST_SOURCE:
                    metaData = (BluetoothLeBroadcastMetadata) message.obj;

                    List<Integer> activeSyncedSrc = mService.getActiveSyncedSources(mDevice);
                    BluetoothDevice sourceDevice = metaData.getSourceDevice();
                    if (!mService.isLocalBroadcast(metaData)
                            && (activeSyncedSrc == null
                                    || !activeSyncedSrc.contains(
                                            mService.getSyncHandleForBroadcastId(
                                                    metaData.getBroadcastId())))) {
                        log("Adding inactive source: " + sourceDevice);
                        int broadcastId = metaData.getBroadcastId();
                        if (broadcastId != BassConstants.INVALID_BROADCAST_ID
                                && mService.getCachedBroadcast(broadcastId) != null) {
                            // If the source has been synced before, try to re-sync(auto/true)
                            // with the source by previously cached scan result
                            Message msg = obtainMessage(SELECT_BCAST_SOURCE);
                            msg.obj = mService.getCachedBroadcast(broadcastId);
                            msg.arg1 = BassConstants.AUTO;
                            sendMessage(msg);
                            mPendingSourceToAdd = metaData;
                        } else {
                            mService.getCallbacks().notifySourceAddFailed(mDevice, metaData,
                                    BluetoothStatusCodes.ERROR_UNKNOWN);
                        }
                        break;
                    }

                    byte[] addSourceInfo = convertMetadataToAddSourceByteArray(metaData);
                    if (addSourceInfo == null) {
                        Log.e(TAG, "add source: source Info is NULL");
                        break;
                    }
                    if (mBluetoothGatt != null && mBroadcastScanControlPoint != null) {
                        writeBassControlPoint(addSourceInfo);
                        mPendingOperation = message.what;
                        mPendingMetadata = metaData;
                        if (metaData.isEncrypted() && (metaData.getBroadcastCode() != null)) {
                            mSetBroadcastCodePending = true;
                        }
                        transitionTo(mConnectedProcessing);
                        sendMessageDelayed(GATT_TXN_TIMEOUT, BassConstants.GATT_TXN_TIMEOUT_MS);
                    } else {
                        Log.e(TAG, "ADD_BCAST_SOURCE: no Bluetooth Gatt handle, Fatal");
                        mService.getCallbacks().notifySourceAddFailed(mDevice,
                                metaData, BluetoothStatusCodes.ERROR_UNKNOWN);
                    }
                    break;
                case UPDATE_BCAST_SOURCE:
                    metaData = (BluetoothLeBroadcastMetadata) message.obj;
                    int sourceId = message.arg1;
                    int paSync = message.arg2;
                    log("Updating Broadcast source: " + metaData);
                    byte[] updateSourceInfo = convertBroadcastMetadataToUpdateSourceByteArray(
                            sourceId, metaData, paSync);
                    if (updateSourceInfo == null) {
                        Log.e(TAG, "update source: source Info is NULL");
                        break;
                    }
                    if (mBluetoothGatt != null && mBroadcastScanControlPoint != null) {
                        writeBassControlPoint(updateSourceInfo);
                        mPendingOperation = message.what;
                        mPendingSourceId = (byte) sourceId;
                        if (paSync == BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE) {
                            setPendingRemove(sourceId, true);
                        }
                        if (metaData.isEncrypted() && (metaData.getBroadcastCode() != null)) {
                            mSetBroadcastCodePending = true;
                        }
                        mPendingMetadata = metaData;
                        transitionTo(mConnectedProcessing);
                        sendMessageDelayed(GATT_TXN_TIMEOUT, BassConstants.GATT_TXN_TIMEOUT_MS);
                    } else {
                        Log.e(TAG, "UPDATE_BCAST_SOURCE: no Bluetooth Gatt handle, Fatal");
                        mService.getCallbacks().notifySourceModifyFailed(
                                mDevice, sourceId, BluetoothStatusCodes.ERROR_UNKNOWN);
                    }
                    break;
                case SET_BCAST_CODE:
                    int argType = message.arg1;
                    mSetBroadcastCodePending = false;
                    BluetoothLeBroadcastReceiveState recvState = null;
                    if (argType == ARGTYPE_METADATA) {
                        mSetBroadcastPINMetadata =
                                (BluetoothLeBroadcastMetadata) message.obj;
                        mSetBroadcastCodePending = true;
                    } else {
                        recvState = (BluetoothLeBroadcastReceiveState) message.obj;
                        if (!isItRightTimeToUpdateBroadcastPin(
                                (byte) recvState.getSourceId())) {
                            mSetBroadcastCodePending = true;
                        }
                    }
                    if (mSetBroadcastCodePending == true) {
                        log("Ignore SET_BCAST now, but restore it for later");
                        break;
                    }
                    byte[] setBroadcastPINcmd =
                            convertRecvStateToSetBroadcastCodeByteArray(recvState);
                    if (setBroadcastPINcmd == null) {
                        Log.e(TAG, "SET_BCAST_CODE: Broadcast code is NULL");
                        break;
                    }
                    if (mBluetoothGatt != null && mBroadcastScanControlPoint != null) {
                        writeBassControlPoint(setBroadcastPINcmd);
                        mPendingOperation = message.what;
                        mPendingSourceId = (byte) recvState.getSourceId();
                        transitionTo(mConnectedProcessing);
                        sendMessageDelayed(GATT_TXN_TIMEOUT, BassConstants.GATT_TXN_TIMEOUT_MS);
                    }
                    break;
                case REMOVE_BCAST_SOURCE:
                    byte sid = (byte) message.arg1;
                    log("Removing Broadcast source, sourceId: " + sid);
                    byte[] removeSourceInfo = new byte[2];
                    removeSourceInfo[0] = OPCODE_REMOVE_SOURCE;
                    removeSourceInfo[1] = sid;
                    if (mBluetoothGatt != null && mBroadcastScanControlPoint != null) {
                        if (isPendingRemove((int) sid)) {
                            setPendingRemove((int) sid, false);
                        }

                        writeBassControlPoint(removeSourceInfo);
                        mPendingOperation = message.what;
                        mPendingSourceId = sid;
                        transitionTo(mConnectedProcessing);
                        sendMessageDelayed(GATT_TXN_TIMEOUT, BassConstants.GATT_TXN_TIMEOUT_MS);
                    } else {
                        Log.e(TAG, "REMOVE_BCAST_SOURCE: no Bluetooth Gatt handle, Fatal");
                        mService.getCallbacks().notifySourceRemoveFailed(mDevice,
                                sid, BluetoothStatusCodes.ERROR_UNKNOWN);
                        if (mPendingSourceToSwitch != null) {
                            // Switching source failed
                            // Need to notify add source failure for service to cleanup
                            mService.getCallbacks()
                                    .notifySourceAddFailed(
                                            mDevice,
                                            mPendingSourceToSwitch,
                                            BluetoothStatusCodes.ERROR_UNKNOWN);
                            mPendingSourceToSwitch = null;
                        }
                    }
                    break;
                case PSYNC_ACTIVE_TIMEOUT:
                    cancelActiveSync(null);
                    break;
                default:
                    log("CONNECTED: not handled message:" + message.what);
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private boolean isSuccess(int status) {
        boolean ret = false;
        switch (status) {
            case BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST:
            case BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST:
            case BluetoothStatusCodes.REASON_REMOTE_REQUEST:
            case BluetoothStatusCodes.REASON_SYSTEM_POLICY:
                ret = true;
                break;
            default:
                break;
        }
        return ret;
    }

    void sendPendingCallbacks(int pendingOp, int status) {
        switch (pendingOp) {
            case START_SCAN_OFFLOAD:
                if (!isSuccess(status)) {
                    if (!mAutoTriggered) {
                        cancelActiveSync(null);
                    } else {
                        mAutoTriggered = false;
                    }
                }
                break;
            case ADD_BCAST_SOURCE:
                if (!isSuccess(status)) {
                    cancelActiveSync(null);
                    if (mPendingMetadata != null) {
                        mService.getCallbacks()
                                .notifySourceAddFailed(mDevice, mPendingMetadata, status);
                        mPendingMetadata = null;
                    }
                }
                break;
            case UPDATE_BCAST_SOURCE:
                if (!mAutoTriggered) {
                    if (!isSuccess(status)) {
                        mService.getCallbacks().notifySourceModifyFailed(mDevice,
                                mPendingSourceId, status);
                        mPendingMetadata = null;
                    }
                } else {
                    mAutoTriggered = false;
                }
                break;
            case REMOVE_BCAST_SOURCE:
                if (!isSuccess(status)) {
                    mService.getCallbacks().notifySourceRemoveFailed(mDevice,
                            mPendingSourceId, status);
                    if (mPendingSourceToSwitch != null) {
                        // Switching source failed
                        // Need to notify add source failure for service to cleanup
                        mService.getCallbacks()
                                .notifySourceAddFailed(mDevice, mPendingSourceToSwitch, status);
                        mPendingSourceToSwitch = null;
                    }
                }
                break;
            case SET_BCAST_CODE:
                log("sendPendingCallbacks: SET_BCAST_CODE");
                break;
            default:
                log("sendPendingCallbacks: unhandled case");
                break;
        }
    }

    // public for testing, but private for non-testing
    @VisibleForTesting
    class ConnectedProcessing extends State {
        @Override
        public void enter() {
            log("Enter ConnectedProcessing(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));

            // Broadcast for testing purpose only
            if (Utils.isInstrumentationTestMode()) {
                Intent intent = new Intent("android.bluetooth.bass_client.NOTIFY_TEST");
                Utils.sendBroadcast(mService, intent, BLUETOOTH_CONNECT,
                        Utils.getTempAllowlistBroadcastOptions());
            }
        }
        @Override
        public void exit() {
            /* Pending Metadata will be used to bond with source ID in receiver state notify */
            if (mPendingOperation == REMOVE_BCAST_SOURCE) {
                    mPendingMetadata = null;
            }

            log("Exit ConnectedProcessing(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
        }
        @Override
        public boolean processMessage(Message message) {
            log("ConnectedProcessing process message(" + mDevice + "): "
                    + messageWhatToString(message.what));
            switch (message.what) {
                case CONNECT:
                    Log.w(TAG, "CONNECT request is ignored" + mDevice);
                    break;
                case DISCONNECT:
                    Log.w(TAG, "DISCONNECT requested!: " + mDevice);
                    mAllowReconnect = false;
                    if (mBluetoothGatt != null) {
                        mService.handleDeviceDisconnection(mDevice, true);
                        mBluetoothGatt.disconnect();
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                        cancelActiveSync(null);
                        transitionTo(mDisconnected);
                    } else {
                        log("mBluetoothGatt is null");
                    }
                    break;
                case READ_BASS_CHARACTERISTICS:
                    Log.w(TAG, "defer READ_BASS_CHARACTERISTICS requested!: " + mDevice);
                    deferMessage(message);
                    break;
                case CONNECTION_STATE_CHANGED:
                    int state = (int) message.obj;
                    Log.w(TAG, "ConnectedProcessing: connection state changed:" + state);
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        Log.w(TAG, "should never happen from this state");
                    } else {
                        Log.w(TAG, "Unexpected disconnection " + mDevice);
                        mService.handleDeviceDisconnection(mDevice, false);
                        resetBluetoothGatt();
                        cancelActiveSync(null);
                        transitionTo(mDisconnected);
                    }
                    break;
                case GATT_TXN_PROCESSED:
                    removeMessages(GATT_TXN_TIMEOUT);
                    int status = (int) message.arg1;
                    log("GATT transaction processed for" + mDevice);
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        sendPendingCallbacks(
                                mPendingOperation,
                                BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);
                    } else {
                        sendPendingCallbacks(
                                mPendingOperation,
                                BluetoothStatusCodes.ERROR_UNKNOWN);
                    }
                    transitionTo(mConnected);
                    break;
                case GATT_TXN_TIMEOUT:
                    log("GATT transaction timeout for" + mDevice);
                    sendPendingCallbacks(
                            mPendingOperation,
                            BluetoothStatusCodes.ERROR_UNKNOWN);
                    mPendingOperation = -1;
                    mPendingSourceId = -1;
                    transitionTo(mConnected);
                    break;
                case START_SCAN_OFFLOAD:
                case STOP_SCAN_OFFLOAD:
                case SELECT_BCAST_SOURCE:
                case ADD_BCAST_SOURCE:
                case SET_BCAST_CODE:
                case REMOVE_BCAST_SOURCE:
                case REACHED_MAX_SOURCE_LIMIT:
                case SWITCH_BCAST_SOURCE:
                case PSYNC_ACTIVE_TIMEOUT:
                    log("defer the message: "
                            + messageWhatToString(message.what)
                            + ", so that it will be processed later");
                    deferMessage(message);
                    break;
                default:
                    log("CONNECTEDPROCESSING: not handled message:" + message.what);
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    void broadcastConnectionState(BluetoothDevice device, int fromState, int toState) {
        log("broadcastConnectionState " + device + ": " + fromState + "->" + toState);
        if (fromState == BluetoothProfile.STATE_CONNECTED
                && toState == BluetoothProfile.STATE_CONNECTED) {
            log("CONNECTED->CONNECTED: Ignore");
            return;
        }

        mService.handleConnectionStateChanged(device, fromState, toState);
        Intent intent = new Intent(BluetoothLeBroadcastAssistant.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, fromState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, toState);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                    | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        Utils.sendBroadcast(mService, intent, BLUETOOTH_CONNECT,
                Utils.getTempAllowlistBroadcastOptions());
    }

    int getConnectionState() {
        String currentState = "Unknown";
        if (getCurrentState() != null) {
            currentState = getCurrentState().getName();
        }
        switch (currentState) {
            case "Disconnected":
                return BluetoothProfile.STATE_DISCONNECTED;
            case "Connecting":
                return BluetoothProfile.STATE_CONNECTING;
            case "Connected":
            case "ConnectedProcessing":
                return BluetoothProfile.STATE_CONNECTED;
            default:
                Log.e(TAG, "Bad currentState: " + currentState);
                return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    int getMaximumSourceCapacity() {
        return mNumOfBroadcastReceiverStates;
    }

    BluetoothDevice getDevice() {
        return mDevice;
    }

    synchronized boolean isConnected() {
        return (getCurrentState() == mConnected) || (getCurrentState() == mConnectedProcessing);
    }

    public static String messageWhatToString(int what) {
        switch (what) {
            case CONNECT:
                return "CONNECT";
            case DISCONNECT:
                return "DISCONNECT";
            case CONNECTION_STATE_CHANGED:
                return "CONNECTION_STATE_CHANGED";
            case GATT_TXN_PROCESSED:
                return "GATT_TXN_PROCESSED";
            case READ_BASS_CHARACTERISTICS:
                return "READ_BASS_CHARACTERISTICS";
            case START_SCAN_OFFLOAD:
                return "START_SCAN_OFFLOAD";
            case STOP_SCAN_OFFLOAD:
                return "STOP_SCAN_OFFLOAD";
            case ADD_BCAST_SOURCE:
                return "ADD_BCAST_SOURCE";
            case SELECT_BCAST_SOURCE:
                return "SELECT_BCAST_SOURCE";
            case UPDATE_BCAST_SOURCE:
                return "UPDATE_BCAST_SOURCE";
            case SET_BCAST_CODE:
                return "SET_BCAST_CODE";
            case REMOVE_BCAST_SOURCE:
                return "REMOVE_BCAST_SOURCE";
            case REACHED_MAX_SOURCE_LIMIT:
                return "REACHED_MAX_SOURCE_LIMIT";
            case SWITCH_BCAST_SOURCE:
                return "SWITCH_BCAST_SOURCE";
            case PSYNC_ACTIVE_TIMEOUT:
                return "PSYNC_ACTIVE_TIMEOUT";
            case CONNECT_TIMEOUT:
                return "CONNECT_TIMEOUT";
            default:
                break;
        }
        return Integer.toString(what);
    }

    /**
     * Dump info
     */
    public void dump(StringBuilder sb) {
        ProfileService.println(sb, "mDevice: " + mDevice);
        ProfileService.println(sb, "  StateMachine: " + this);
        // Dump the state machine logs
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        super.dump(new FileDescriptor(), printWriter, new String[] {});
        printWriter.flush();
        stringWriter.flush();
        ProfileService.println(sb, "  StateMachineLog:");
        Scanner scanner = new Scanner(stringWriter.toString());
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            ProfileService.println(sb, "    " + line);
        }
        scanner.close();
        for (Map.Entry<Integer, BluetoothLeBroadcastReceiveState> entry :
                mBluetoothLeBroadcastReceiveStates.entrySet()) {
            BluetoothLeBroadcastReceiveState state = entry.getValue();
            sb.append(state);
        }
    }

    @Override
    protected void log(String msg) {
        if (BassConstants.BASS_DBG) {
            super.log(msg);
        }
    }

    private static void logByteArray(String prefix, byte[] value, int offset, int count) {
        StringBuilder builder = new StringBuilder(prefix);
        for (int i = offset; i < count; i++) {
            builder.append(String.format("0x%02X", value[i]));
            if (i != value.length - 1) {
                builder.append(", ");
            }
        }
        Log.d(TAG, builder.toString());
    }

    /** Mockable wrapper of {@link BluetoothGatt}. */
    @VisibleForTesting
    public static class BluetoothGattTestableWrapper {
        public final BluetoothGatt mWrappedBluetoothGatt;

        BluetoothGattTestableWrapper(BluetoothGatt bluetoothGatt) {
            mWrappedBluetoothGatt = bluetoothGatt;
        }

        /** See {@link BluetoothGatt#getServices()}. */
        public List<BluetoothGattService> getServices() {
            return mWrappedBluetoothGatt.getServices();
        }

        /** See {@link BluetoothGatt#getService(UUID)}. */
        @Nullable
        public BluetoothGattService getService(UUID uuid) {
            return mWrappedBluetoothGatt.getService(uuid);
        }

        /** See {@link BluetoothGatt#discoverServices()}. */
        public boolean discoverServices() {
            return mWrappedBluetoothGatt.discoverServices();
        }

        /**
         * See {@link BluetoothGatt#readCharacteristic(
         * BluetoothGattCharacteristic)}.
         */
        public boolean readCharacteristic(BluetoothGattCharacteristic characteristic) {
            return mWrappedBluetoothGatt.readCharacteristic(characteristic);
        }

        /**
         * See {@link BluetoothGatt#writeCharacteristic(
         * BluetoothGattCharacteristic, byte[], int)} .
         */
        public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) {
            return mWrappedBluetoothGatt.writeCharacteristic(characteristic);
        }

        /** See {@link BluetoothGatt#readDescriptor(BluetoothGattDescriptor)}. */
        public boolean readDescriptor(BluetoothGattDescriptor descriptor) {
            return mWrappedBluetoothGatt.readDescriptor(descriptor);
        }

        /**
         * See {@link BluetoothGatt#writeDescriptor(BluetoothGattDescriptor,
         * byte[])}.
         */
        public boolean writeDescriptor(BluetoothGattDescriptor descriptor) {
            return mWrappedBluetoothGatt.writeDescriptor(descriptor);
        }

        /** See {@link BluetoothGatt#requestMtu(int)}. */
        public boolean requestMtu(int mtu) {
            return mWrappedBluetoothGatt.requestMtu(mtu);
        }

        /** See {@link BluetoothGatt#setCharacteristicNotification}. */
        public boolean setCharacteristicNotification(
                BluetoothGattCharacteristic characteristic, boolean enable) {
            return mWrappedBluetoothGatt.setCharacteristicNotification(characteristic, enable);
        }

        /** See {@link BluetoothGatt#disconnect()}. */
        public void disconnect() {
            mWrappedBluetoothGatt.disconnect();
        }

        /** See {@link BluetoothGatt#close()}. */
        public void close() {
            mWrappedBluetoothGatt.close();
        }
    }

}
