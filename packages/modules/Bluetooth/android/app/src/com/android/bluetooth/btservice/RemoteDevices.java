/*
 * Copyright (C) 2012-2014 The Android Open Source Project
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

package com.android.bluetooth.btservice;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.Manifest.permission.BLUETOOTH_SCAN;

import android.annotation.RequiresPermission;
import android.app.admin.SecurityLog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAssignedNumbers;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProtoEnums;
import android.bluetooth.BluetoothSinkAudioPolicy;
import android.bluetooth.IBluetoothConnectionCallback;
import android.content.Context;
import android.content.Intent;
import android.net.MacAddress;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Log;

import com.android.bluetooth.BluetoothStatsLog;
import com.android.bluetooth.R;
import com.android.bluetooth.Utils;
import com.android.bluetooth.bas.BatteryService;
import com.android.bluetooth.flags.Flags;
import com.android.bluetooth.hfp.HeadsetHalConstants;
import com.android.internal.annotations.VisibleForTesting;

import java.io.UnsupportedEncodingException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Remote device manager. This class is currently mostly used for HF and AG remote devices. */
public class RemoteDevices {
    private static final boolean DBG = false;
    private static final String TAG = "BluetoothRemoteDevices";

    // Maximum number of device properties to remember
    private static final int MAX_DEVICE_QUEUE_SIZE = 200;

    private BluetoothAdapter mAdapter;
    private AdapterService mAdapterService;
    private ArrayList<BluetoothDevice> mSdpTracker;
    private final Object mObject = new Object();

    private static final int UUID_INTENT_DELAY = 6000;
    private static final int MESSAGE_UUID_INTENT = 1;
    private static final String LOG_SOURCE_DIS = "DIS";

    private final HashMap<String, DeviceProperties> mDevices;
    private final HashMap<String, String> mDualDevicesMap;
    private ArrayDeque<String> mDeviceQueue;

    /**
     * Bluetooth HFP v1.8 specifies the Battery Charge indicator of AG can take values from
     * {@code 0} to {@code 5}, but it does not specify how to map the values back to percentages.
     * The following mapping is used:
     *   - Level 0:                 0%
     *   - Level 1: midpoint of  1-25%
     *   - Level 2: midpoint of 26-50%
     *   - Level 3: midpoint of 51-75%
     *   - Level 4: midpoint of 76-99%
     *   - Level 5:               100%
     */
    private static final int HFP_BATTERY_CHARGE_INDICATOR_0 = 0;
    private static final int HFP_BATTERY_CHARGE_INDICATOR_1 = 13;
    private static final int HFP_BATTERY_CHARGE_INDICATOR_2 = 38;
    private static final int HFP_BATTERY_CHARGE_INDICATOR_3 = 63;
    private static final int HFP_BATTERY_CHARGE_INDICATOR_4 = 88;
    private static final int HFP_BATTERY_CHARGE_INDICATOR_5 = 100;

    private final Handler mHandler;
    private final Handler mMainHandler;

    private class RemoteDevicesHandler extends Handler {

        /**
         * Handler must be created from an explicit looper to avoid threading ambiguity
         * @param looper The looper that this handler should be executed on
         */
        RemoteDevicesHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_UUID_INTENT:
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    if (device != null) {
                        // SDP Sending delayed SDP UUID intent
                        MetricsLogger.getInstance().cacheCount(
                                BluetoothProtoEnums.SDP_SENDING_DELAYED_UUID, 1);
                        DeviceProperties prop = getDeviceProperties(device);
                        sendUuidIntent(device, prop);
                    } else {
                        // SDP Not sending delayed SDP UUID intent b/c device is not there
                        MetricsLogger.getInstance().cacheCount(
                                BluetoothProtoEnums.SDP_NOT_SENDING_DELAYED_UUID, 1);
                    }
                    break;
            }
        }
    }

    /**
     * Predicate that tests if the given {@link BluetoothDevice} is well-known
     * to be used for physical location.
     */
    private final Predicate<BluetoothDevice> mLocationDenylistPredicate = (device) -> {
        final MacAddress parsedAddress = MacAddress.fromString(device.getAddress());
        if (mAdapterService.getLocationDenylistMac().test(parsedAddress.toByteArray())) {
            Log.v(TAG, "Skipping device matching denylist: " + device);
            return true;
        }
        final String name = Utils.getName(device);
        if (mAdapterService.getLocationDenylistName().test(name)) {
            Log.v(TAG, "Skipping name matching denylist: " + name);
            return true;
        }
        return false;
    };

    RemoteDevices(AdapterService service, Looper looper) {
        mAdapter = ((Context) service).getSystemService(BluetoothManager.class).getAdapter();
        mAdapterService = service;
        mSdpTracker = new ArrayList<BluetoothDevice>();
        mDevices = new HashMap<String, DeviceProperties>();
        mDualDevicesMap = new HashMap<String, String>();
        mDeviceQueue = new ArrayDeque<>();
        mHandler = new RemoteDevicesHandler(looper);
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    /** Init should be called before using this RemoteDevices object */
    void init() {}

    /**
     * Clean up should be called when this object is no longer needed, must be called after init()
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    void cleanup() {
        reset();
    }

    /**
     * Reset should be called when the state of this object needs to be cleared
     * RemoteDevices is still usable after reset
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    void reset() {
        if (mSdpTracker != null) {
            mSdpTracker.clear();
        }

        // Unregister Handler and stop all queued messages.
        if (mMainHandler != null) {
            mMainHandler.removeCallbacksAndMessages(null);
        }

        synchronized (mDevices) {
            if (mDevices != null) {
                debugLog("reset(): Broadcasting ACL_DISCONNECTED");

                mDevices.forEach(
                        (address, deviceProperties) -> {
                            BluetoothDevice bluetoothDevice = deviceProperties.getDevice();

                            debugLog(
                                    "reset(): address="
                                            + address
                                            + ", connected="
                                            + bluetoothDevice.isConnected());

                    if (bluetoothDevice.isConnected()) {
                        int transport = deviceProperties.getConnectionHandle(
                                BluetoothDevice.TRANSPORT_BREDR) != BluetoothDevice.ERROR
                                        ? BluetoothDevice.TRANSPORT_BREDR
                                        : BluetoothDevice.TRANSPORT_LE;
                        mAdapterService.notifyAclDisconnected(bluetoothDevice, transport);
                        Intent intent = new Intent(BluetoothDevice.ACTION_ACL_DISCONNECTED);
                        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, bluetoothDevice);
                        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                                | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
                        mAdapterService.sendBroadcast(intent, BLUETOOTH_CONNECT);
                    }
                });
                mDevices.clear();
            }
        }

        if (mDualDevicesMap != null) {
            mDualDevicesMap.clear();
        }

        if (mDeviceQueue != null) {
            mDeviceQueue.clear();
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    DeviceProperties getDeviceProperties(BluetoothDevice device) {
        if (device == null) {
            return null;
        }

        synchronized (mDevices) {
            String address = mDualDevicesMap.get(device.getAddress());
            // If the device is not in the dual map, use its original address
            if (address == null || mDevices.get(address) == null) {
                address = device.getAddress();
            }
            return mDevices.get(address);
        }
    }

    BluetoothDevice getDevice(byte[] address) {
        String addressString = Utils.getAddressStringFromByte(address);
        String deviceAddress = mDualDevicesMap.get(addressString);
        // If the device is not in the dual map, use its original address
        if (deviceAddress == null || mDevices.get(deviceAddress) == null) {
            deviceAddress = addressString;
        }

        DeviceProperties prop = mDevices.get(deviceAddress);
        if (prop != null) {
            return prop.getDevice();
        }
        return null;
    }

    @VisibleForTesting
    DeviceProperties addDeviceProperties(byte[] address) {
        synchronized (mDevices) {
            DeviceProperties prop = new DeviceProperties();
            prop.setDevice(mAdapter.getRemoteDevice(Utils.getAddressStringFromByte(address)));
            prop.setAddress(address);
            String key = Utils.getAddressStringFromByte(address);
            DeviceProperties pv = mDevices.put(key, prop);

            if (pv == null) {
                mDeviceQueue.offer(key);
                if (mDeviceQueue.size() > MAX_DEVICE_QUEUE_SIZE) {
                    String deleteKey = mDeviceQueue.poll();
                    for (BluetoothDevice device : mAdapterService.getBondedDevices()) {
                        if (device.getAddress().equals(deleteKey)) {
                            return prop;
                        }
                    }
                    debugLog("Removing device " + deleteKey + " from property map");
                    mDevices.remove(deleteKey);
                }
            }
            return prop;
        }
    }

    class DeviceProperties {
        private String mName;
        private byte[] mAddress;
        private String mIdentityAddress;
        private boolean mIsConsolidated = false;
        private int mBluetoothClass = BluetoothClass.Device.Major.UNCATEGORIZED;
        private int mBredrConnectionHandle = BluetoothDevice.ERROR;
        private int mLeConnectionHandle = BluetoothDevice.ERROR;
        private short mRssi;
        private String mAlias;
        private BluetoothDevice mDevice;
        private boolean mIsBondingInitiatedLocally;
        private int mBatteryLevelFromHfp = BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        private int mBatteryLevelFromBatteryService = BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        private boolean mIsCoordinatedSetMember;
        private int mAshaCapability;
        private int mAshaTruncatedHiSyncId;
        private String mModelName;
        @VisibleForTesting int mBondState;
        @VisibleForTesting int mDeviceType;
        @VisibleForTesting ParcelUuid[] mUuids;
        private BluetoothSinkAudioPolicy mAudioPolicy;

        DeviceProperties() {
            mBondState = BluetoothDevice.BOND_NONE;
        }

        /**
         * @return the mName
         */
        String getName() {
            synchronized (mObject) {
                return mName;
            }
        }

        /**
         * @param name the mName to set
         */
        void setName(String name) {
            synchronized (mObject) {
                this.mName = name;
            }
        }

        /**
         * @return the mIdentityAddress
         */
        String getIdentityAddress() {
            synchronized (mObject) {
                return mIdentityAddress;
            }
        }

        /**
         * @param identityAddress the mIdentityAddress to set
         */
        void setIdentityAddress(String identityAddress) {
            synchronized (mObject) {
                this.mIdentityAddress = identityAddress;
            }
        }

        /**
         * @return mIsConsolidated
         */
        boolean isConsolidated() {
            synchronized (mObject) {
                return mIsConsolidated;
            }
        }

        /**
         * @param isConsolidated the mIsConsolidated to set
         */
        void setIsConsolidated(boolean isConsolidated) {
            synchronized (mObject) {
                this.mIsConsolidated = isConsolidated;
            }
        }

        /**
         * @return the mClass
         */
        int getBluetoothClass() {
            synchronized (mObject) {
                return mBluetoothClass;
            }
        }

        /**
         * @param bluetoothClass the mBluetoothClass to set
         */
        void setBluetoothClass(int bluetoothClass) {
            synchronized (mObject) {
                this.mBluetoothClass = bluetoothClass;
            }
        }

        /**
         * @param transport the transport on which the connection exists
         * @return the mConnectionHandle
         */
        int getConnectionHandle(int transport) {
            synchronized (mObject) {
                if (transport == BluetoothDevice.TRANSPORT_BREDR) {
                    return mBredrConnectionHandle;
                } else if (transport == BluetoothDevice.TRANSPORT_LE) {
                    return mLeConnectionHandle;
                } else {
                    return BluetoothDevice.ERROR;
                }
            }
        }

        /**
         * @param connectionHandle the connectionHandle to set
         * @param transport the transport on which to set the handle
         */
        void setConnectionHandle(int connectionHandle, int transport) {
            synchronized (mObject) {
                if (transport == BluetoothDevice.TRANSPORT_BREDR) {
                    mBredrConnectionHandle = connectionHandle;
                } else if (transport == BluetoothDevice.TRANSPORT_LE) {
                    mLeConnectionHandle = connectionHandle;
                } else {
                    errorLog("setConnectionHandle() unexpected transport value " + transport);
                }
            }
        }

        /**
         * @return the mUuids
         */
        ParcelUuid[] getUuids() {
            synchronized (mObject) {
                return mUuids;
            }
        }

        /**
         * @param uuids the mUuids to set
         */
        void setUuids(ParcelUuid[] uuids) {
            synchronized (mObject) {
                this.mUuids = uuids;
            }
        }

        /**
         * @return the mAddress
         */
        byte[] getAddress() {
            synchronized (mObject) {
                return mAddress;
            }
        }

        /**
         * @param address the mAddress to set
         */
        void setAddress(byte[] address) {
            synchronized (mObject) {
                this.mAddress = address;
            }
        }

        /**
         * @return the mDevice
         */
        BluetoothDevice getDevice() {
            synchronized (mObject) {
                return mDevice;
            }
        }

        /**
         * @param device the mDevice to set
         */
        void setDevice(BluetoothDevice device) {
            synchronized (mObject) {
                this.mDevice = device;
            }
        }

        /**
         * @return mRssi
         */
        short getRssi() {
            synchronized (mObject) {
                return mRssi;
            }
        }

        /**
         * @param rssi the mRssi to set
         */
        void setRssi(short rssi) {
            synchronized (mObject) {
                this.mRssi = rssi;
            }
        }

        /**
         * @return mDeviceType
         */
        int getDeviceType() {
            synchronized (mObject) {
                return mDeviceType;
            }
        }

        /**
         * @param deviceType the mDeviceType to set
         */
        void setDeviceType(int deviceType) {
            synchronized (mObject) {
                this.mDeviceType = deviceType;
            }
        }

        /**
         * @return the mAlias
         */
        String getAlias() {
            synchronized (mObject) {
                return mAlias;
            }
        }

        /**
         * @param mAlias the mAlias to set
         */
        void setAlias(BluetoothDevice device, String mAlias) {
            synchronized (mObject) {
                this.mAlias = mAlias;
                mAdapterService
                        .getNative()
                        .setDeviceProperty(
                                mAddress,
                                AbstractionLayer.BT_PROPERTY_REMOTE_FRIENDLY_NAME,
                                mAlias.getBytes());
                Intent intent = new Intent(BluetoothDevice.ACTION_ALIAS_CHANGED);
                intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
                intent.putExtra(BluetoothDevice.EXTRA_NAME, mAlias);
                Utils.sendBroadcast(mAdapterService, intent, BLUETOOTH_CONNECT,
                        Utils.getTempAllowlistBroadcastOptions());
            }
        }

        /**
         * @param newBondState the mBondState to set
         */
        void setBondState(int newBondState) {
            synchronized (mObject) {
                this.mBondState = newBondState;
                if (newBondState == BluetoothDevice.BOND_NONE) {
                    /* Clearing the Uuids local copy when the device is unpaired. If not cleared,
                    cachedBluetoothDevice issued a connect using the local cached copy of uuids,
                    without waiting for the ACTION_UUID intent.
                    This was resulting in multiple calls to connect().*/
                    mUuids = null;
                    mAlias = null;
                }
            }
        }

        /**
         * @return the mBondState
         */
        int getBondState() {
            synchronized (mObject) {
                return mBondState;
            }
        }

        boolean isBonding() {
            return getBondState() == BluetoothDevice.BOND_BONDING;
        }

        boolean isBondingOrBonded() {
            return isBonding() || getBondState() == BluetoothDevice.BOND_BONDED;
        }

        /**
         * @param isBondingInitiatedLocally wether bonding is initiated locally
         */
        void setBondingInitiatedLocally(boolean isBondingInitiatedLocally) {
            synchronized (mObject) {
                this.mIsBondingInitiatedLocally = isBondingInitiatedLocally;
            }
        }

        /**
         * @return the isBondingInitiatedLocally
         */
        boolean isBondingInitiatedLocally() {
            synchronized (mObject) {
                return mIsBondingInitiatedLocally;
            }
        }

        /**
         * @return mBatteryLevel
         */
        int getBatteryLevel() {
            synchronized (mObject) {
                if (mBatteryLevelFromBatteryService != BluetoothDevice.BATTERY_LEVEL_UNKNOWN) {
                    return mBatteryLevelFromBatteryService;
                }
                return mBatteryLevelFromHfp;
            }
        }

        void setBatteryLevelFromHfp(int batteryLevel) {
            synchronized (mObject) {
                if (mBatteryLevelFromHfp == batteryLevel) {
                    return;
                }
                mBatteryLevelFromHfp = batteryLevel;
            }
        }

        void setBatteryLevelFromBatteryService(int batteryLevel) {
            synchronized (mObject) {
                if (mBatteryLevelFromBatteryService == batteryLevel) {
                    return;
                }
                mBatteryLevelFromBatteryService = batteryLevel;
            }
        }

        /**
         * @return the mIsCoordinatedSetMember
        */
        boolean isCoordinatedSetMember() {
            synchronized (mObject) {
                return mIsCoordinatedSetMember;
            }
        }

        /**
         * @param isCoordinatedSetMember the mIsCoordinatedSetMember to set
        */
        void setIsCoordinatedSetMember(boolean isCoordinatedSetMember) {
            if ((mAdapterService.getSupportedProfilesBitMask()
                            & (1 << BluetoothProfile.CSIP_SET_COORDINATOR))
                    == 0) {
                debugLog("CSIP is not supported");
                return;
            }
            synchronized (mObject) {
                this.mIsCoordinatedSetMember = isCoordinatedSetMember;
            }
        }

        /**
         * @return the mAshaCapability
         */
        int getAshaCapability() {
            synchronized (mObject) {
                return mAshaCapability;
            }
        }

        void setAshaCapability(int ashaCapability) {
            synchronized (mObject) {
                this.mAshaCapability = ashaCapability;
            }
        }

        /**
         * @return the mAshaTruncatedHiSyncId
         */
        int getAshaTruncatedHiSyncId() {
            synchronized (mObject) {
                return mAshaTruncatedHiSyncId;
            }
        }

        void setAshaTruncatedHiSyncId(int ashaTruncatedHiSyncId) {
            synchronized (mObject) {
                this.mAshaTruncatedHiSyncId = ashaTruncatedHiSyncId;
            }
        }

        public void setHfAudioPolicyForRemoteAg(BluetoothSinkAudioPolicy policies) {
            mAudioPolicy = policies;
        }

        public BluetoothSinkAudioPolicy getHfAudioPolicyForRemoteAg() {
            return mAudioPolicy;
        }

        public void setModelName(String modelName) {
            mModelName = modelName;
            try {
                mAdapterService.setMetadata(
                    this.mDevice,
                    BluetoothDevice.METADATA_MODEL_NAME,
                    mModelName.getBytes("UTF-8"));
            } catch (UnsupportedEncodingException uee) {
                Log.e(TAG, "setModelName: UTF-8 not supported?!?"); // this should not happen
            }
        }

        /**
         * @return the mModelName
         */
        String getModelName() {
            synchronized (mObject) {
                return mModelName;
            }
        }
    }

    private void sendUuidIntent(BluetoothDevice device, DeviceProperties prop) {
        // Send uuids within the stack before the broadcast is sent out
        ParcelUuid[] uuids = prop == null ? null : prop.getUuids();
        mAdapterService.sendUuidsInternal(device, uuids);

        Intent intent = new Intent(BluetoothDevice.ACTION_UUID);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothDevice.EXTRA_UUID, uuids);
        Utils.sendBroadcast(mAdapterService, intent, BLUETOOTH_CONNECT,
                Utils.getTempAllowlistBroadcastOptions());

        // SDP Sent UUID Intent here
        MetricsLogger.getInstance().cacheCount(
                BluetoothProtoEnums.SDP_SENT_UUID, 1);
        //Remove the outstanding UUID request
        mSdpTracker.remove(device);
    }

    /**
     * When bonding is initiated to remote device that we have never seen, i.e Out Of Band pairing,
     * we must add device first before setting it's properties. This is a helper method for doing
     * that.
     */
    void setBondingInitiatedLocally(byte[] address) {
        DeviceProperties properties;

        BluetoothDevice device = getDevice(address);
        if (device == null) {
            properties = addDeviceProperties(address);
        } else {
            properties = getDeviceProperties(device);
        }

        properties.setBondingInitiatedLocally(true);
    }

    /**
     * Update battery level in device properties
     *
     * @param device The remote device to be updated
     * @param batteryLevel Battery level Indicator between 0-100, {@link
     *     BluetoothDevice#BATTERY_LEVEL_UNKNOWN} is error
     * @param isBas true if the battery level is from the battery service
     */
    @VisibleForTesting
    void updateBatteryLevel(BluetoothDevice device, int batteryLevel, boolean isBas) {
        if (device == null || batteryLevel < 0 || batteryLevel > 100) {
            warnLog(
                    "Invalid parameters device="
                            + String.valueOf(device == null)
                            + ", batteryLevel="
                            + String.valueOf(batteryLevel));
            return;
        }
        DeviceProperties deviceProperties = getDeviceProperties(device);
        if (deviceProperties == null) {
            deviceProperties = addDeviceProperties(Utils.getByteAddress(device));
        }
        int prevBatteryLevel = deviceProperties.getBatteryLevel();
        if (isBas) {
            deviceProperties.setBatteryLevelFromBatteryService(batteryLevel);
        } else {
            deviceProperties.setBatteryLevelFromHfp(batteryLevel);
        }
        int newBatteryLevel = deviceProperties.getBatteryLevel();
        if (prevBatteryLevel == newBatteryLevel) {
            debugLog(
                    "Same battery level for device "
                            + device
                            + " received "
                            + String.valueOf(batteryLevel)
                            + "%");
            return;
        }
        sendBatteryLevelChangedBroadcast(device, newBatteryLevel);
        Log.d(TAG, "Updated device " + device + " battery level to " + newBatteryLevel + "%");
    }

    /**
     * Reset battery level property to {@link BluetoothDevice#BATTERY_LEVEL_UNKNOWN} for a device
     *
     * @param device device whose battery level property needs to be reset
     */
    @VisibleForTesting
    void resetBatteryLevel(BluetoothDevice device, boolean isBas) {
        if (device == null) {
            warnLog("Device is null");
            return;
        }
        DeviceProperties deviceProperties = getDeviceProperties(device);
        if (deviceProperties == null) {
            return;
        }
        int prevBatteryLevel = deviceProperties.getBatteryLevel();
        if (isBas) {
            deviceProperties.setBatteryLevelFromBatteryService(
                    BluetoothDevice.BATTERY_LEVEL_UNKNOWN);
        } else {
            deviceProperties.setBatteryLevelFromHfp(BluetoothDevice.BATTERY_LEVEL_UNKNOWN);
        }

        int newBatteryLevel = deviceProperties.getBatteryLevel();
        if (prevBatteryLevel == newBatteryLevel) {
            debugLog("Battery level was not changed due to reset, device=" + device);
            return;
        }
        sendBatteryLevelChangedBroadcast(device, newBatteryLevel);
        Log.d(TAG, "Updated device " + device + " battery level to " + newBatteryLevel + "%");
    }

    private void sendBatteryLevelChangedBroadcast(BluetoothDevice device, int batteryLevel) {
        Intent intent = new Intent(BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothDevice.EXTRA_BATTERY_LEVEL, batteryLevel);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        Utils.sendBroadcast(mAdapterService, intent, BLUETOOTH_CONNECT,
                Utils.getTempAllowlistBroadcastOptions());
    }

    /**
     * Converts HFP's Battery Charge indicator values of {@code 0 -- 5} to an integer percentage.
     */
    @VisibleForTesting
    static int batteryChargeIndicatorToPercentge(int indicator) {
        int percent;
        switch (indicator) {
            case 5:
                percent = HFP_BATTERY_CHARGE_INDICATOR_5;
                break;
            case 4:
                percent = HFP_BATTERY_CHARGE_INDICATOR_4;
                break;
            case 3:
                percent = HFP_BATTERY_CHARGE_INDICATOR_3;
                break;
            case 2:
                percent = HFP_BATTERY_CHARGE_INDICATOR_2;
                break;
            case 1:
                percent = HFP_BATTERY_CHARGE_INDICATOR_1;
                break;
            case 0:
                percent = HFP_BATTERY_CHARGE_INDICATOR_0;
                break;
            default:
                percent = BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        Log.d(TAG, "Battery charge indicator: " + indicator + "; converted to: " + percent + "%");
        return percent;
    }

    private static boolean areUuidsEqual(ParcelUuid[] uuids1, ParcelUuid[] uuids2) {
        final int length1 = uuids1 == null ? 0 : uuids1.length;
        final int length2 = uuids2 == null ? 0 : uuids2.length;
        if (length1 != length2) {
            return false;
        }
        Set<ParcelUuid> set = new HashSet<>();
        for (int i = 0; i < length1; ++i) {
            set.add(uuids1[i]);
        }
        for (int i = 0; i < length2; ++i) {
            set.remove(uuids2[i]);
        }
        return set.isEmpty();
    }

    void devicePropertyChangedCallback(byte[] address, int[] types, byte[][] values) {
        Intent intent;
        byte[] val;
        int type;
        BluetoothDevice bdDevice = getDevice(address);
        DeviceProperties deviceProperties;
        if (bdDevice == null) {
            debugLog("Added new device property");
            deviceProperties = addDeviceProperties(address);
            bdDevice = getDevice(address);
        } else {
            deviceProperties = getDeviceProperties(bdDevice);
        }

        if (types.length <= 0) {
            errorLog("No properties to update");
            return;
        }

        for (int j = 0; j < types.length; j++) {
            type = types[j];
            val = values[j];
            if (val.length > 0) {
                synchronized (mObject) {
                    debugLog("Property type: " + type);
                    switch (type) {
                        case AbstractionLayer.BT_PROPERTY_BDNAME:
                            final String newName = new String(val);
                            if (newName.equals(deviceProperties.getName())) {
                                debugLog("Skip name update for " + bdDevice);
                                break;
                            }
                            deviceProperties.setName(newName);
                            intent = new Intent(BluetoothDevice.ACTION_NAME_CHANGED);
                            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, bdDevice);
                            intent.putExtra(BluetoothDevice.EXTRA_NAME, deviceProperties.getName());
                            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                            Utils.sendBroadcast(mAdapterService, intent, BLUETOOTH_CONNECT,
                                    Utils.getTempAllowlistBroadcastOptions());
                            debugLog("Remote device name is: " + deviceProperties.getName());
                            break;
                        case AbstractionLayer.BT_PROPERTY_REMOTE_FRIENDLY_NAME:
                            deviceProperties.setAlias(bdDevice, new String(val));
                            debugLog("Remote device alias is: " + deviceProperties.getAlias());
                            break;
                        case AbstractionLayer.BT_PROPERTY_BDADDR:
                            deviceProperties.setAddress(val);
                            debugLog(
                                    "Remote Address is:"
                                            + Utils.getRedactedAddressStringFromByte(val));
                            break;
                        case AbstractionLayer.BT_PROPERTY_CLASS_OF_DEVICE:
                            final int newBluetoothClass = Utils.byteArrayToInt(val);
                            if (newBluetoothClass == deviceProperties.getBluetoothClass()) {
                                debugLog("Skip class update for " + bdDevice);
                                break;
                            }
                            deviceProperties.setBluetoothClass(newBluetoothClass);
                            intent = new Intent(BluetoothDevice.ACTION_CLASS_CHANGED);
                            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, bdDevice);
                            intent.putExtra(BluetoothDevice.EXTRA_CLASS,
                                    new BluetoothClass(deviceProperties.getBluetoothClass()));
                            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                            Utils.sendBroadcast(mAdapterService, intent, BLUETOOTH_CONNECT,
                                    Utils.getTempAllowlistBroadcastOptions());
                            debugLog("Remote class is:" + newBluetoothClass);
                            break;
                        case AbstractionLayer.BT_PROPERTY_UUIDS:
                            final ParcelUuid[] newUuids = Utils.byteArrayToUuid(val);
                            if (areUuidsEqual(newUuids, deviceProperties.getUuids())) {
                                // SDP Skip adding UUIDs to property cache if equal
                                debugLog("Skip uuids update for " + bdDevice.getAddress());
                                MetricsLogger.getInstance().cacheCount(
                                        BluetoothProtoEnums.SDP_UUIDS_EQUAL_SKIP, 1);
                                break;
                            }
                            deviceProperties.setUuids(newUuids);
                            if (mAdapterService.getState() == BluetoothAdapter.STATE_ON) {
                                // SDP Adding UUIDs to property cache and sending intent
                                MetricsLogger.getInstance().cacheCount(
                                        BluetoothProtoEnums.SDP_ADD_UUID_WITH_INTENT, 1);
                                mAdapterService.deviceUuidUpdated(bdDevice);
                                sendUuidIntent(bdDevice, deviceProperties);
                            } else if (mAdapterService.getState()
                                    == BluetoothAdapter.STATE_BLE_ON) {
                                // SDP Adding UUIDs to property cache but with no intent
                                MetricsLogger.getInstance().cacheCount(
                                        BluetoothProtoEnums.SDP_ADD_UUID_WITH_NO_INTENT, 1);
                                mAdapterService.deviceUuidUpdated(bdDevice);
                            } else {
                                // SDP Silently dropping UUIDs and with no intent
                                MetricsLogger.getInstance().cacheCount(
                                        BluetoothProtoEnums.SDP_DROP_UUID, 1);
                            }
                            break;
                        case AbstractionLayer.BT_PROPERTY_TYPE_OF_DEVICE:
                            if (deviceProperties.isConsolidated()) {
                                break;
                            }
                            // The device type from hal layer, defined in bluetooth.h,
                            // matches the type defined in BluetoothDevice.java
                            deviceProperties.setDeviceType(Utils.byteArrayToInt(val));
                            break;
                        case AbstractionLayer.BT_PROPERTY_REMOTE_RSSI:
                            // RSSI from hal is in one byte
                            deviceProperties.setRssi(val[0]);
                            break;
                        case AbstractionLayer.BT_PROPERTY_REMOTE_IS_COORDINATED_SET_MEMBER:
                            deviceProperties.setIsCoordinatedSetMember(val[0] != 0);
                            break;
                        case AbstractionLayer.BT_PROPERTY_REMOTE_ASHA_CAPABILITY:
                            deviceProperties.setAshaCapability(val[0]);
                            break;
                        case AbstractionLayer.BT_PROPERTY_REMOTE_ASHA_TRUNCATED_HISYNCID:
                            deviceProperties.setAshaTruncatedHiSyncId(val[0]);
                            break;
                        case AbstractionLayer.BT_PROPERTY_REMOTE_MODEL_NUM:
                            final String modelName = new String(val);
                            debugLog("Remote device model name: " + modelName);
                            deviceProperties.setModelName(modelName);
                            BluetoothStatsLog.write(
                                    BluetoothStatsLog.BLUETOOTH_DEVICE_INFO_REPORTED,
                                    mAdapterService.obfuscateAddress(bdDevice),
                                    BluetoothProtoEnums.DEVICE_INFO_INTERNAL, LOG_SOURCE_DIS, null,
                                    modelName, null, null, mAdapterService.getMetricId(bdDevice),
                                    bdDevice.getAddressType(), 0, 0, 0);
                            break;
                    }
                }
            }
        }
    }

    void deviceFoundCallback(byte[] address) {
        // The device properties are already registered - we can send the intent
        // now
        BluetoothDevice device = getDevice(address);
        debugLog("deviceFoundCallback: Remote Address is:" + device);
        DeviceProperties deviceProp = getDeviceProperties(device);
        if (deviceProp == null) {
            errorLog("Device Properties is null for Device:" + device);
            return;
        }
        boolean restrict_device_found =
                SystemProperties.getBoolean("bluetooth.restrict_discovered_device.enabled", false);
        if (restrict_device_found && (deviceProp.mName == null || deviceProp.mName.isEmpty())) {
            debugLog("Device name is null or empty: " + device);
            return;
        }

        Intent intent = new Intent(BluetoothDevice.ACTION_FOUND);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothDevice.EXTRA_CLASS,
                new BluetoothClass(deviceProp.getBluetoothClass()));
        intent.putExtra(BluetoothDevice.EXTRA_RSSI, deviceProp.getRssi());
        intent.putExtra(BluetoothDevice.EXTRA_NAME, deviceProp.getName());
        intent.putExtra(BluetoothDevice.EXTRA_IS_COORDINATED_SET_MEMBER,
                deviceProp.isCoordinatedSetMember());

        final ArrayList<DiscoveringPackage> packages = mAdapterService.getDiscoveringPackages();
        synchronized (packages) {
            for (DiscoveringPackage pkg : packages) {
                if (pkg.hasDisavowedLocation()) {
                    if (mLocationDenylistPredicate.test(device)) {
                        continue;
                    }
                }

                intent.setPackage(pkg.getPackageName());

                if (pkg.getPermission() != null) {
                    mAdapterService.sendBroadcastMultiplePermissions(intent,
                            new String[] { BLUETOOTH_SCAN, pkg.getPermission() },
                            Utils.getTempBroadcastOptions());
                } else {
                    mAdapterService.sendBroadcastMultiplePermissions(intent,
                            new String[] { BLUETOOTH_SCAN },
                            Utils.getTempBroadcastOptions());
                }
            }
        }
    }

    void addressConsolidateCallback(byte[] mainAddress, byte[] secondaryAddress) {
        BluetoothDevice device = getDevice(mainAddress);
        if (device == null) {
            errorLog("addressConsolidateCallback: device is NULL, address="
                    + Utils.getRedactedAddressStringFromByte(mainAddress)
                    + ", secondaryAddress="
                    + Utils.getRedactedAddressStringFromByte(secondaryAddress));
            return;
        }
        Log.d(TAG, "addressConsolidateCallback device: " + device + ", secondaryAddress:"
                + Utils.getRedactedAddressStringFromByte(secondaryAddress));

        DeviceProperties deviceProperties = getDeviceProperties(device);
        deviceProperties.setIsConsolidated(true);
        deviceProperties.setDeviceType(BluetoothDevice.DEVICE_TYPE_DUAL);
        deviceProperties.setIdentityAddress(Utils.getAddressStringFromByte(secondaryAddress));
        mDualDevicesMap.put(deviceProperties.getIdentityAddress(), Utils.getAddressStringFromByte(mainAddress));
    }

    /**
     * Callback to associate an LE-only device's RPA with its identity address
     *
     * @param mainAddress the device's RPA
     * @param secondaryAddress the device's identity address
     */
    void leAddressAssociateCallback(byte[] mainAddress, byte[] secondaryAddress) {
        BluetoothDevice device = getDevice(mainAddress);
        if (device == null) {
            errorLog("leAddressAssociateCallback: device is NULL, address="
                    + Utils.getRedactedAddressStringFromByte(mainAddress)
                    + ", secondaryAddress="
                    + Utils.getRedactedAddressStringFromByte(secondaryAddress));
            return;
        }
        Log.d(TAG, "leAddressAssociateCallback device: " + device + ", secondaryAddress:"
                + Utils.getRedactedAddressStringFromByte(secondaryAddress));

        DeviceProperties deviceProperties = getDeviceProperties(device);
        deviceProperties.mIdentityAddress = Utils.getAddressStringFromByte(secondaryAddress);
    }

    @RequiresPermission(allOf = {
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_PRIVILEGED,
    })
    void aclStateChangeCallback(int status, byte[] address, int newState,
                                int transportLinkType, int hciReason, int handle) {
        if (status != AbstractionLayer.BT_STATUS_SUCCESS) {
            debugLog("aclStateChangeCallback status is " + status + ", skipping");
            return;
        }

        BluetoothDevice device = getDevice(address);

        if (device == null) {
            warnLog("aclStateChangeCallback: device is NULL, address="
                    + Utils.getRedactedAddressStringFromByte(address)
                    + ", newState=" + newState);
            addDeviceProperties(address);
            device = Objects.requireNonNull(getDevice(address));
        }

        DeviceProperties deviceProperties = getDeviceProperties(device);

        int state = mAdapterService.getState();

        Intent intent = null;
        if (newState == AbstractionLayer.BT_ACL_STATE_CONNECTED) {
            deviceProperties.setConnectionHandle(handle, transportLinkType);
            if (state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_TURNING_ON) {
                intent = new Intent(BluetoothDevice.ACTION_ACL_CONNECTED);
                intent.putExtra(BluetoothDevice.EXTRA_TRANSPORT, transportLinkType);
            } else if (state == BluetoothAdapter.STATE_BLE_ON
                    || state == BluetoothAdapter.STATE_BLE_TURNING_ON) {
                intent = new Intent(BluetoothAdapter.ACTION_BLE_ACL_CONNECTED);
            }
            BatteryService batteryService = BatteryService.getBatteryService();
            if (batteryService != null && transportLinkType == BluetoothDevice.TRANSPORT_LE) {
                batteryService.connectIfPossible(device);
            }
            mAdapterService.updatePhonePolicyOnAclConnect(device);
            SecurityLog.writeEvent(SecurityLog.TAG_BLUETOOTH_CONNECTION,
                    Utils.getLoggableAddress(device), /* success */ 1, /* reason */ "");
            debugLog(
                    "aclStateChangeCallback: Adapter State: " + BluetoothAdapter.nameForState(state)
                            + " Connected: " + device);
        } else {
            deviceProperties.setConnectionHandle(BluetoothDevice.ERROR, transportLinkType);
            if (device.getBondState() == BluetoothDevice.BOND_BONDING) {
                // Send PAIRING_CANCEL intent to dismiss any dialog requesting bonding.
                intent = new Intent(BluetoothDevice.ACTION_PAIRING_CANCEL);
                intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
                intent.setPackage(SystemProperties.get(
                        Utils.PAIRING_UI_PROPERTY,
                        mAdapterService.getString(R.string.pairing_ui_package)));

                Utils.sendBroadcast(mAdapterService, intent, BLUETOOTH_CONNECT,
                        Utils.getTempAllowlistBroadcastOptions());
            } else if (device.getBondState() == BluetoothDevice.BOND_NONE) {
                removeAddressMapping(address);
            }
            if (state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_TURNING_OFF) {
                mAdapterService.notifyAclDisconnected(device, transportLinkType);
                intent = new Intent(BluetoothDevice.ACTION_ACL_DISCONNECTED);
                intent.putExtra(BluetoothDevice.EXTRA_TRANSPORT, transportLinkType);
            } else if (state == BluetoothAdapter.STATE_BLE_ON
                    || state == BluetoothAdapter.STATE_BLE_TURNING_OFF) {
                intent = new Intent(BluetoothAdapter.ACTION_BLE_ACL_DISCONNECTED);
            }
            // Reset battery level on complete disconnection
            if (mAdapterService.getConnectionState(device) == 0) {
                BatteryService batteryService = BatteryService.getBatteryService();
                if (batteryService != null
                        && batteryService.getConnectionState(device)
                                != BluetoothProfile.STATE_DISCONNECTED
                        && transportLinkType == BluetoothDevice.TRANSPORT_LE) {
                    batteryService.disconnect(device);
                }
                resetBatteryLevel(device, /*isBas=*/ true);
            }

            if (mAdapterService.isAllProfilesUnknown(device)) {
                DeviceProperties deviceProp = getDeviceProperties(device);
                if (deviceProp != null) {
                    deviceProp.setBondingInitiatedLocally(false);
                }
            }
            SecurityLog.writeEvent(SecurityLog.TAG_BLUETOOTH_DISCONNECTION,
                    Utils.getLoggableAddress(device),
                    BluetoothAdapter.BluetoothConnectionCallback.disconnectReasonToString(
                            AdapterService.hciToAndroidDisconnectReason(hciReason)));
            debugLog(
                    "aclStateChangeCallback: Adapter State: " + BluetoothAdapter.nameForState(state)
                            + " Disconnected: " + device
                            + " transportLinkType: " + transportLinkType
                            + " hciReason: " + hciReason);
        }

        int connectionState = newState == AbstractionLayer.BT_ACL_STATE_CONNECTED
                ? BluetoothAdapter.STATE_CONNECTED : BluetoothAdapter.STATE_DISCONNECTED;
        int metricId = mAdapterService.getMetricId(device);
        BluetoothStatsLog.write(
                BluetoothStatsLog.BLUETOOTH_ACL_CONNECTION_STATE_CHANGED,
                mAdapterService.obfuscateAddress(device),
                connectionState,
                metricId,
                transportLinkType);

        BluetoothClass deviceClass = device.getBluetoothClass();
        int classOfDevice = deviceClass == null ? 0 : deviceClass.getClassOfDevice();
        BluetoothStatsLog.write(BluetoothStatsLog.BLUETOOTH_CLASS_OF_DEVICE_REPORTED,
                mAdapterService.obfuscateAddress(device), classOfDevice, metricId);

        if (intent != null) {
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device)
                .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT)
                .addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            Utils.sendBroadcast(mAdapterService, intent, BLUETOOTH_CONNECT,
                    Utils.getTempAllowlistBroadcastOptions());

            synchronized (mAdapterService.getBluetoothConnectionCallbacks()) {
                Set<IBluetoothConnectionCallback> bluetoothConnectionCallbacks =
                        mAdapterService.getBluetoothConnectionCallbacks();
                for (IBluetoothConnectionCallback callback : bluetoothConnectionCallbacks) {
                    try {
                        if (connectionState == BluetoothAdapter.STATE_CONNECTED) {
                            callback.onDeviceConnected(device);
                        } else {
                            callback.onDeviceDisconnected(device,
                                    AdapterService.hciToAndroidDisconnectReason(hciReason));
                        }
                    } catch (RemoteException ex) {
                        Log.e(TAG, "RemoteException in calling IBluetoothConnectionCallback");
                    }
                }
            }
        } else {
            Log.e(TAG, "aclStateChangeCallback intent is null. deviceBondState: "
                    + device.getBondState());
        }
    }

    private void removeAddressMapping(byte[] address) {
        String key = Utils.getAddressStringFromByte(address);
        synchronized (mDevices) {
            mDevices.remove(key);
            mDeviceQueue.remove(key); // Remove from LRU cache

            // Remove from dual mode device mappings
            mDualDevicesMap.values().remove(key);
            mDualDevicesMap.remove(key);
        }
    }

    void onBondStateChange(byte[] address, int newState) {
        if (newState == BluetoothDevice.BOND_NONE) {
            removeAddressMapping(address);
        }
    }

    void keyMissingCallback(byte[] address) {
        BluetoothDevice bluetoothDevice = getDevice(address);
        if (bluetoothDevice == null) {
            errorLog(
                    "keyMissingCallback: device is NULL, address="
                            + Utils.getRedactedAddressStringFromByte(address));
            return;
        }
        Log.d(TAG, "keyMissingCallback device: " + bluetoothDevice);

        if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
            if (!Flags.keyMissingBroadcast()) {
                Log.d(TAG, "flag not set - don't send key missing broadcast");
                return;
            }
            Intent intent =
                    new Intent(BluetoothDevice.ACTION_KEY_MISSING)
                            .putExtra(BluetoothDevice.EXTRA_DEVICE, bluetoothDevice)
                            .addFlags(
                                    Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                                            | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            mAdapterService.sendBroadcastMultiplePermissions(
                    intent,
                    new String[] {BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED},
                    Utils.getTempBroadcastOptions());
        }
    }

    void fetchUuids(BluetoothDevice device, int transport) {
        if (mSdpTracker.contains(device)) {
            // SDP Skip fetch UUIDs if cached
            MetricsLogger.getInstance().cacheCount(
                    BluetoothProtoEnums.SDP_FETCH_UUID_SKIP_ALREADY_CACHED, 1);
            return;
        }

        // If no UUIDs are cached and the device is bonding, wait for SDP after the device is bonded
        DeviceProperties deviceProperties = getDeviceProperties(device);
        if (deviceProperties != null && deviceProperties.isBonding()
                && getDeviceProperties(device).getUuids() == null) {
            // SDP Skip fetch UUIDs due to bonding
            MetricsLogger.getInstance().cacheCount(
                    BluetoothProtoEnums.SDP_FETCH_UUID_SKIP_ALREADY_BONDED, 1);
            return;
        }

        mSdpTracker.add(device);

        Message message = mHandler.obtainMessage(MESSAGE_UUID_INTENT);
        message.obj = device;
        mHandler.sendMessageDelayed(message, UUID_INTENT_DELAY);

        // Uses cached UUIDs if we are bonding. If not, we fetch the UUIDs with SDP.
        if (deviceProperties == null || !deviceProperties.isBonding()) {
            // SDP Invoked native code to spin up SDP cycle
            mAdapterService
                    .getNative()
                    .getRemoteServices(Utils.getBytesFromAddress(device.getAddress()), transport);
            MetricsLogger.getInstance().cacheCount(
                    BluetoothProtoEnums.SDP_INVOKE_SDP_CYCLE, 1);
        }
    }

    void updateUuids(BluetoothDevice device) {
        Message message = mHandler.obtainMessage(MESSAGE_UUID_INTENT);
        message.obj = device;
        mHandler.sendMessage(message);
    }

    /** Handles headset connection state change event */
    public void handleHeadsetConnectionStateChanged(
            BluetoothDevice device, int fromState, int toState) {
        mMainHandler.post(() -> onHeadsetConnectionStateChanged(device, fromState, toState));
    }

    @VisibleForTesting
    void onHeadsetConnectionStateChanged(BluetoothDevice device, int fromState, int toState) {
        if (device == null) {
            Log.e(TAG, "onHeadsetConnectionStateChanged() remote device is null");
            return;
        }
        if (toState == BluetoothProfile.STATE_DISCONNECTED && !hasBatteryService(device)) {
            resetBatteryLevel(device, /*isBas=*/ false);
        }
    }

    /** Handle indication events from Hands-free. */
    public void handleHfIndicatorValueChanged(
            BluetoothDevice device, int indicatorId, int indicatorValue) {
        mMainHandler.post(() -> onHfIndicatorValueChanged(device, indicatorId, indicatorValue));
    }

    @VisibleForTesting
    void onHfIndicatorValueChanged(BluetoothDevice device, int indicatorId, int indicatorValue) {
        if (device == null) {
            Log.e(TAG, "onHfIndicatorValueChanged() remote device is null");
            return;
        }
        if (indicatorId == HeadsetHalConstants.HF_INDICATOR_BATTERY_LEVEL_STATUS) {
            updateBatteryLevel(device, indicatorValue, /*isBas=*/ false);
        }
    }

    /** Handles Headset specific Bluetooth events */
    public void handleVendorSpecificHeadsetEvent(
            BluetoothDevice device, String cmd, int companyId, int cmdType, Object[] args) {
        mMainHandler.post(
                () -> onVendorSpecificHeadsetEvent(device, cmd, companyId, cmdType, args));
    }

    @VisibleForTesting
    void onVendorSpecificHeadsetEvent(
            BluetoothDevice device, String cmd, int companyId, int cmdType, Object[] args) {
        if (device == null) {
            Log.e(TAG, "onVendorSpecificHeadsetEvent() remote device is null");
            return;
        }
        if (companyId != BluetoothAssignedNumbers.PLANTRONICS
                && companyId != BluetoothAssignedNumbers.APPLE) {
            Log.i(
                    TAG,
                    "onVendorSpecificHeadsetEvent() filtered out non-PLANTRONICS and non-APPLE "
                            + "vendor commands");
            return;
        }
        if (cmd == null) {
            Log.e(TAG, "onVendorSpecificHeadsetEvent() command is null");
            return;
        }
        // Only process set command
        if (cmdType != BluetoothHeadset.AT_CMD_TYPE_SET) {
            debugLog("onVendorSpecificHeadsetEvent() only SET command is processed");
            return;
        }
        if (args == null) {
            Log.e(TAG, "onVendorSpecificHeadsetEvent() arguments are null");
            return;
        }
        int batteryPercent = BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        switch (cmd) {
            case BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_XEVENT:
                batteryPercent = getBatteryLevelFromXEventVsc(args);
                break;
            case BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_IPHONEACCEV:
                batteryPercent = getBatteryLevelFromAppleBatteryVsc(args);
                break;
        }
        if (batteryPercent != BluetoothDevice.BATTERY_LEVEL_UNKNOWN) {
            updateBatteryLevel(device, batteryPercent, /*isBas=*/ false);
            infoLog("Updated device " + device + " battery level to " + String.valueOf(
                    batteryPercent) + "%");
        }
    }

    /**
     * Parse
     *      AT+IPHONEACCEV=[NumberOfIndicators],[IndicatorType],[IndicatorValue]
     * vendor specific event
     * @param args Array of arguments on the right side of assignment
     * @return Battery level in percents, [0-100], {@link BluetoothDevice#BATTERY_LEVEL_UNKNOWN}
     *         when there is an error parsing the arguments
     */
    @VisibleForTesting
    static int getBatteryLevelFromAppleBatteryVsc(Object[] args) {
        if (args.length == 0) {
            Log.w(TAG, "getBatteryLevelFromAppleBatteryVsc() empty arguments");
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        int numKvPair;
        if (args[0] instanceof Integer) {
            numKvPair = (Integer) args[0];
        } else {
            Log.w(TAG, "getBatteryLevelFromAppleBatteryVsc() error parsing number of arguments");
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        if (args.length != (numKvPair * 2 + 1)) {
            Log.w(TAG, "getBatteryLevelFromAppleBatteryVsc() number of arguments does not match");
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        int indicatorType;
        int indicatorValue = -1;
        for (int i = 0; i < numKvPair; ++i) {
            Object indicatorTypeObj = args[2 * i + 1];
            if (indicatorTypeObj instanceof Integer) {
                indicatorType = (Integer) indicatorTypeObj;
            } else {
                Log.w(TAG, "getBatteryLevelFromAppleBatteryVsc() error parsing indicator type");
                return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
            }
            if (indicatorType
                    != BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_IPHONEACCEV_BATTERY_LEVEL) {
                continue;
            }
            Object indicatorValueObj = args[2 * i + 2];
            if (indicatorValueObj instanceof Integer) {
                indicatorValue = (Integer) indicatorValueObj;
            } else {
                Log.w(TAG, "getBatteryLevelFromAppleBatteryVsc() error parsing indicator value");
                return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
            }
            break;
        }
        return (indicatorValue < 0 || indicatorValue > 9) ? BluetoothDevice.BATTERY_LEVEL_UNKNOWN
                : (indicatorValue + 1) * 10;
    }

    /**
     * Parse
     *      AT+XEVENT=BATTERY,[Level],[NumberOfLevel],[MinutesOfTalk],[IsCharging]
     * vendor specific event
     * @param args Array of arguments on the right side of SET command
     * @return Battery level in percents, [0-100], {@link BluetoothDevice#BATTERY_LEVEL_UNKNOWN}
     *         when there is an error parsing the arguments
     */
    @VisibleForTesting
    static int getBatteryLevelFromXEventVsc(Object[] args) {
        if (args.length == 0) {
            Log.w(TAG, "getBatteryLevelFromXEventVsc() empty arguments");
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        Object eventNameObj = args[0];
        if (!(eventNameObj instanceof String)) {
            Log.w(TAG, "getBatteryLevelFromXEventVsc() error parsing event name");
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        String eventName = (String) eventNameObj;
        if (!eventName.equals(
                BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_XEVENT_BATTERY_LEVEL)) {
            infoLog("getBatteryLevelFromXEventVsc() skip none BATTERY event: " + eventName);
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        if (args.length != 5) {
            Log.w(TAG, "getBatteryLevelFromXEventVsc() wrong battery level event length: "
                    + String.valueOf(args.length));
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        if (!(args[1] instanceof Integer) || !(args[2] instanceof Integer)) {
            Log.w(TAG, "getBatteryLevelFromXEventVsc() error parsing event values");
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        int batteryLevel = (Integer) args[1];
        int numberOfLevels = (Integer) args[2];
        if (batteryLevel < 0 || numberOfLevels <= 1 || batteryLevel > numberOfLevels) {
            Log.w(TAG, "getBatteryLevelFromXEventVsc() wrong event value, batteryLevel="
                    + String.valueOf(batteryLevel) + ", numberOfLevels=" + String.valueOf(
                    numberOfLevels));
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        return batteryLevel * 100 / (numberOfLevels - 1);
    }

    @VisibleForTesting
    boolean hasBatteryService(BluetoothDevice device) {
        BatteryService batteryService = BatteryService.getBatteryService();
        return batteryService != null
                && batteryService.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED;
    }

    /** Handles headset client connection state change event. */
    public void handleHeadsetClientConnectionStateChanged(
            BluetoothDevice device, int fromState, int toState) {
        mMainHandler.post(() -> onHeadsetClientConnectionStateChanged(device, fromState, toState));
    }

    @VisibleForTesting
    void onHeadsetClientConnectionStateChanged(BluetoothDevice device, int fromState, int toState) {
        if (device == null) {
            Log.e(TAG, "onHeadsetClientConnectionStateChanged() remote device is null");
            return;
        }
        if (toState == BluetoothProfile.STATE_DISCONNECTED && !hasBatteryService(device)) {
            resetBatteryLevel(device, /*isBas=*/ false);
        }
    }

    /** Handle battery level changes indication events from Audio Gateway. */
    public void handleAgBatteryLevelChanged(BluetoothDevice device, int batteryLevel) {
        mMainHandler.post(() -> onAgBatteryLevelChanged(device, batteryLevel));
    }

    @VisibleForTesting
    void onAgBatteryLevelChanged(BluetoothDevice device, int batteryLevel) {
        if (device == null) {
            Log.e(TAG, "onAgBatteryLevelChanged() remote device is null");
            return;
        }
        updateBatteryLevel(
                device, batteryChargeIndicatorToPercentge(batteryLevel), /*isBas=*/ false);
    }

    private static void errorLog(String msg) {
        Log.e(TAG, msg);
    }

    private static void debugLog(String msg) {
        if (DBG) {
            Log.d(TAG, msg);
        }
    }

    private static void infoLog(String msg) {
        if (DBG) {
            Log.i(TAG, msg);
        }
    }

    private static void warnLog(String msg) {
        Log.w(TAG, msg);
    }

}
