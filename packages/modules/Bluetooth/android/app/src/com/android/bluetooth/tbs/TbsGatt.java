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

package com.android.bluetooth.tbs;

import static android.bluetooth.BluetoothDevice.METADATA_GTBS_CCCD;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import com.android.bluetooth.BluetoothEventLogger;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class TbsGatt {

    private static final String TAG = "TbsGatt";
    private static final boolean DBG = true;

    private static final String UUID_PREFIX = "0000";
    private static final String UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb";

    /* TBS assigned uuid's */
    @VisibleForTesting
    static final UUID UUID_TBS = makeUuid("184B");
    @VisibleForTesting
    public static final UUID UUID_GTBS = makeUuid("184C");
    @VisibleForTesting
    static final UUID UUID_BEARER_PROVIDER_NAME = makeUuid("2BB3");
    @VisibleForTesting
    static final UUID UUID_BEARER_UCI = makeUuid("2BB4");
    @VisibleForTesting
    static final UUID UUID_BEARER_TECHNOLOGY = makeUuid("2BB5");
    @VisibleForTesting
    static final UUID UUID_BEARER_URI_SCHEMES_SUPPORTED_LIST = makeUuid("2BB6");
    @VisibleForTesting
    static final UUID UUID_BEARER_LIST_CURRENT_CALLS = makeUuid("2BB9");
    @VisibleForTesting
    static final UUID UUID_CONTENT_CONTROL_ID = makeUuid("2BBA");
    @VisibleForTesting
    static final UUID UUID_STATUS_FLAGS = makeUuid("2BBB");
    @VisibleForTesting
    static final UUID UUID_CALL_STATE = makeUuid("2BBD");
    @VisibleForTesting
    static final UUID UUID_CALL_CONTROL_POINT = makeUuid("2BBE");
    @VisibleForTesting
    static final UUID UUID_CALL_CONTROL_POINT_OPTIONAL_OPCODES = makeUuid("2BBF");
    @VisibleForTesting
    static final UUID UUID_TERMINATION_REASON = makeUuid("2BC0");
    @VisibleForTesting
    static final UUID UUID_INCOMING_CALL = makeUuid("2BC1");
    @VisibleForTesting
    static final UUID UUID_CALL_FRIENDLY_NAME = makeUuid("2BC2");
    @VisibleForTesting
    static final UUID UUID_CLIENT_CHARACTERISTIC_CONFIGURATION = makeUuid("2902");

    @VisibleForTesting
    static final int STATUS_FLAG_INBAND_RINGTONE_ENABLED = 0x0001;
    @VisibleForTesting
    static final int STATUS_FLAG_SILENT_MODE_ENABLED = 0x0002;

    @VisibleForTesting
    static final int CALL_CONTROL_POINT_OPTIONAL_OPCODE_LOCAL_HOLD = 0x0001;
    @VisibleForTesting
    static final int CALL_CONTROL_POINT_OPTIONAL_OPCODE_JOIN = 0x0002;

    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_OPCODE_ACCEPT = 0x00;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_OPCODE_TERMINATE = 0x01;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_OPCODE_LOCAL_HOLD = 0x02;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_OPCODE_LOCAL_RETRIEVE = 0x03;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_OPCODE_ORIGINATE = 0x04;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_OPCODE_JOIN = 0x05;

    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_RESULT_SUCCESS = 0x00;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_RESULT_OPCODE_NOT_SUPPORTED = 0x01;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_RESULT_OPERATION_NOT_POSSIBLE = 0x02;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_RESULT_INVALID_CALL_INDEX = 0x03;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_RESULT_STATE_MISMATCH = 0x04;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_RESULT_LACK_OF_RESOURCES = 0x05;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_RESULT_INVALID_OUTGOING_URI = 0x06;

    private final Object mPendingGattOperationsLock = new Object();
    private final Context mContext;
    private final GattCharacteristic mBearerProviderNameCharacteristic;
    private final GattCharacteristic mBearerUciCharacteristic;
    private final GattCharacteristic mBearerTechnologyCharacteristic;
    private final GattCharacteristic mBearerUriSchemesSupportedListCharacteristic;
    private final GattCharacteristic mBearerListCurrentCallsCharacteristic;
    private final GattCharacteristic mContentControlIdCharacteristic;
    private final GattCharacteristic mStatusFlagsCharacteristic;
    private final GattCharacteristic mCallStateCharacteristic;
    private final CallControlPointCharacteristic mCallControlPointCharacteristic;
    private final GattCharacteristic mCallControlPointOptionalOpcodesCharacteristic;
    private final GattCharacteristic mTerminationReasonCharacteristic;
    private final GattCharacteristic mIncomingCallCharacteristic;
    private final GattCharacteristic mCallFriendlyNameCharacteristic;
    private boolean mSilentMode = false;
    private Map<BluetoothDevice, Integer> mStatusFlagValue = new HashMap<>();
    @GuardedBy("mPendingGattOperationsLock")
    private Map<BluetoothDevice, List<GattOpContext>> mPendingGattOperations = new HashMap<>();
    private BluetoothGattServerProxy mBluetoothGattServer;
    private Handler mHandler;
    private Callback mCallback;
    private AdapterService mAdapterService;
    private HashMap<BluetoothDevice, HashMap<UUID, Short>> mCccDescriptorValues;
    private TbsService mTbsService;

    private static final int LOG_NB_EVENTS = 200;
    private BluetoothEventLogger mEventLogger = null;

    private static String tbsUuidToString(UUID uuid) {
        if (uuid.equals(UUID_BEARER_PROVIDER_NAME)) {
            return "BEARER_PROVIDER_NAME";
        } else if (uuid.equals(UUID_BEARER_UCI)) {
            return "BEARER_UCI";
        } else if (uuid.equals(UUID_BEARER_TECHNOLOGY)) {
            return "BEARER_TECHNOLOGY";
        } else if (uuid.equals(UUID_BEARER_URI_SCHEMES_SUPPORTED_LIST)) {
            return "BEARER_URI_SCHEMES_SUPPORTED_LIST";
        } else if (uuid.equals(UUID_BEARER_LIST_CURRENT_CALLS)) {
            return "BEARER_LIST_CURRENT_CALLS";
        } else if (uuid.equals(UUID_CONTENT_CONTROL_ID)) {
            return "CONTENT_CONTROL_ID";
        } else if (uuid.equals(UUID_STATUS_FLAGS)) {
            return "STATUS_FLAGS";
        } else if (uuid.equals(UUID_CALL_STATE)) {
            return "CALL_STATE";
        } else if (uuid.equals(UUID_CALL_CONTROL_POINT)) {
            return "CALL_CONTROL_POINT";
        } else if (uuid.equals(UUID_CALL_CONTROL_POINT_OPTIONAL_OPCODES)) {
            return "CALL_CONTROL_POINT_OPTIONAL_OPCODES";
        } else if (uuid.equals(UUID_TERMINATION_REASON)) {
            return "TERMINATION_REASON";
        } else if (uuid.equals(UUID_INCOMING_CALL)) {
            return "INCOMING_CALL";
        } else if (uuid.equals(UUID_CALL_FRIENDLY_NAME)) {
            return "CALL_FRIENDLY_NAME";
        } else if (uuid.equals(UUID_CLIENT_CHARACTERISTIC_CONFIGURATION)) {
            return "CLIENT_CHARACTERISTIC_CONFIGURATION";
        } else {
            return "UNKNOWN(" + uuid + ")";
        }
    }

    public static abstract class Callback {

        public abstract void onServiceAdded(boolean success);

        public abstract void onCallControlPointRequest(BluetoothDevice device, int opcode,
                byte[] args);

        /**
         * Check if device has enabled inband ringtone
         *
         * @param device device which is checked for inband ringtone availability
         * @return  {@code true} if enabled, {@code false} otherwise
         */
        public abstract boolean isInbandRingtoneEnabled(BluetoothDevice device);
    }

    private static class GattOpContext {
        public enum Operation {
            READ_CHARACTERISTIC,
            WRITE_CHARACTERISTIC,
            READ_DESCRIPTOR,
            WRITE_DESCRIPTOR,
        }

        GattOpContext(Operation operation, int requestId,
                BluetoothGattCharacteristic characteristic, BluetoothGattDescriptor descriptor,
                boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            mOperation = operation;
            mRequestId = requestId;
            mCharacteristic = characteristic;
            mDescriptor = descriptor;
            mPreparedWrite = preparedWrite;
            mResponseNeeded = responseNeeded;
            mOffset = offset;
            mValue = value;
        }

        GattOpContext(Operation operation, int requestId,
                BluetoothGattCharacteristic characteristic, BluetoothGattDescriptor descriptor) {
            mOperation = operation;
            mRequestId = requestId;
            mCharacteristic = characteristic;
            mDescriptor = descriptor;
            mPreparedWrite = false;
            mResponseNeeded = false;
            mOffset = 0;
            mValue = null;
        }

        public Operation mOperation;
        public int mRequestId;
        public BluetoothGattCharacteristic mCharacteristic;
        public BluetoothGattDescriptor mDescriptor;
        public boolean mPreparedWrite;
        public boolean mResponseNeeded;
        public int mOffset;
        public byte[] mValue;
    }

    TbsGatt(TbsService tbsService) {
        mContext = tbsService;
        mAdapterService =
                Objects.requireNonNull(
                        AdapterService.getAdapterService(),
                        "AdapterService shouldn't be null when creating TbsGatt");

        mAdapterService.registerBluetoothStateCallback(
                mContext.getMainExecutor(), mBluetoothStateChangeCallback);

        mBearerProviderNameCharacteristic = new GattCharacteristic(UUID_BEARER_PROVIDER_NAME,
                BluetoothGattCharacteristic.PROPERTY_READ
                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mBearerUciCharacteristic =
                new GattCharacteristic(UUID_BEARER_UCI, BluetoothGattCharacteristic.PROPERTY_READ,
                        BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mBearerTechnologyCharacteristic = new GattCharacteristic(UUID_BEARER_TECHNOLOGY,
                BluetoothGattCharacteristic.PROPERTY_READ
                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mBearerUriSchemesSupportedListCharacteristic =
                new GattCharacteristic(UUID_BEARER_URI_SCHEMES_SUPPORTED_LIST,
                        BluetoothGattCharacteristic.PROPERTY_READ
                                | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mBearerListCurrentCallsCharacteristic =
                new GattCharacteristic(UUID_BEARER_LIST_CURRENT_CALLS,
                        BluetoothGattCharacteristic.PROPERTY_READ
                                | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mContentControlIdCharacteristic = new GattCharacteristic(UUID_CONTENT_CONTROL_ID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mStatusFlagsCharacteristic = new GattCharacteristic(UUID_STATUS_FLAGS,
                BluetoothGattCharacteristic.PROPERTY_READ
                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mCallStateCharacteristic = new GattCharacteristic(UUID_CALL_STATE,
                BluetoothGattCharacteristic.PROPERTY_READ
                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mCallControlPointCharacteristic = new CallControlPointCharacteristic();
        mCallControlPointOptionalOpcodesCharacteristic = new GattCharacteristic(
                UUID_CALL_CONTROL_POINT_OPTIONAL_OPCODES, BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mTerminationReasonCharacteristic = new GattCharacteristic(UUID_TERMINATION_REASON,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0);
        mIncomingCallCharacteristic = new GattCharacteristic(UUID_INCOMING_CALL,
                BluetoothGattCharacteristic.PROPERTY_READ
                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mCallFriendlyNameCharacteristic = new GattCharacteristic(UUID_CALL_FRIENDLY_NAME,
                BluetoothGattCharacteristic.PROPERTY_READ
                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);

        mTbsService = tbsService;
        mBluetoothGattServer = null;
    }

    @VisibleForTesting
    void setBluetoothGattServerForTesting(BluetoothGattServerProxy proxy) {
        mBluetoothGattServer = proxy;
    }

    public boolean init(int ccid, String uci, List<String> uriSchemes,
            boolean isLocalHoldOpcodeSupported, boolean isJoinOpcodeSupported, String providerName,
            int technology, Callback callback) {
        mCccDescriptorValues = new HashMap<>();
        mBearerProviderNameCharacteristic.setValue(providerName);
        mBearerTechnologyCharacteristic.setValue(new byte[] {(byte) (technology & 0xFF)});
        mBearerUciCharacteristic.setValue(uci);
        setBearerUriSchemesSupportedList(uriSchemes);
        mContentControlIdCharacteristic.setValue(ccid, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        setCallControlPointOptionalOpcodes(isLocalHoldOpcodeSupported, isJoinOpcodeSupported);
        mStatusFlagsCharacteristic.setValue(0, BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        mCallback = callback;
        mHandler = new Handler(Looper.getMainLooper());

        if (mBluetoothGattServer == null) {
            mBluetoothGattServer = new BluetoothGattServerProxy(mContext);
        }

        if (!mBluetoothGattServer.open(mGattServerCallback)) {
            Log.e(TAG, " Could not open Gatt server");
            return false;
        }

        BluetoothGattService gattService =
                new BluetoothGattService(UUID_GTBS, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        gattService.addCharacteristic(mBearerProviderNameCharacteristic);
        gattService.addCharacteristic(mBearerUciCharacteristic);
        gattService.addCharacteristic(mBearerTechnologyCharacteristic);
        gattService.addCharacteristic(mBearerUriSchemesSupportedListCharacteristic);
        gattService.addCharacteristic(mBearerListCurrentCallsCharacteristic);
        gattService.addCharacteristic(mContentControlIdCharacteristic);
        gattService.addCharacteristic(mStatusFlagsCharacteristic);
        gattService.addCharacteristic(mCallStateCharacteristic);
        gattService.addCharacteristic(mCallControlPointCharacteristic);
        gattService.addCharacteristic(mCallControlPointOptionalOpcodesCharacteristic);
        gattService.addCharacteristic(mTerminationReasonCharacteristic);
        gattService.addCharacteristic(mIncomingCallCharacteristic);
        gattService.addCharacteristic(mCallFriendlyNameCharacteristic);

        mEventLogger = new BluetoothEventLogger(LOG_NB_EVENTS,
                TAG + " instance (CCID= " + ccid + ") event log");
        mEventLogger.add("Initializing");

        return mBluetoothGattServer.addService(gattService);
    }

    public void cleanup() {
        mAdapterService.unregisterBluetoothStateCallback(mBluetoothStateChangeCallback);

        if (mBluetoothGattServer == null) {
            return;
        }
        mBluetoothGattServer.close();
        mBluetoothGattServer = null;
    }

    public Context getContext() {
        return mContext;
    }

    private void removeUuidFromMetadata(ParcelUuid charUuid, BluetoothDevice device) {
        List<ParcelUuid> uuidList;
        byte[] gtbs_cccd = device.getMetadata(METADATA_GTBS_CCCD);

        if ((gtbs_cccd == null) || (gtbs_cccd.length == 0)) {
            uuidList = new ArrayList<ParcelUuid>();
        } else {
            uuidList = new ArrayList<>(Arrays.asList(Utils.byteArrayToUuid(gtbs_cccd)));

            if (!uuidList.contains(charUuid)) {
                Log.d(TAG, "Characteristic CCCD can't be removed (not cached): "
                        + charUuid.toString());
                return;
            }
        }

        uuidList.remove(charUuid);

        if (!device.setMetadata(METADATA_GTBS_CCCD,
                Utils.uuidsToByteArray(uuidList.toArray(new ParcelUuid[0])))) {
            Log.e(TAG, "Can't set CCCD for GTBS characteristic UUID: " + charUuid + ", (remove)");
        }
    }

    private void addUuidToMetadata(ParcelUuid charUuid, BluetoothDevice device) {
        List<ParcelUuid> uuidList;
        byte[] gtbs_cccd = device.getMetadata(METADATA_GTBS_CCCD);

        if ((gtbs_cccd == null) || (gtbs_cccd.length == 0)) {
            uuidList = new ArrayList<ParcelUuid>();
        } else {
            uuidList = new ArrayList<>(Arrays.asList(Utils.byteArrayToUuid(gtbs_cccd)));

            if (uuidList.contains(charUuid)) {
                Log.d(TAG, "Characteristic CCCD already add: " + charUuid.toString());
                return;
            }
        }

        uuidList.add(charUuid);

        if (!device.setMetadata(METADATA_GTBS_CCCD,
                Utils.uuidsToByteArray(uuidList.toArray(new ParcelUuid[0])))) {
            Log.e(TAG, "Can't set CCCD for GTBS characteristic UUID: " + charUuid + ", (add)");
        }
    }

    @VisibleForTesting
    void setCcc(BluetoothDevice device, UUID charUuid, byte[] value) {
        HashMap<UUID, Short> characteristicCcc = mCccDescriptorValues.get(device);
        if (characteristicCcc == null) {
            characteristicCcc = new HashMap<>();
            mCccDescriptorValues.put(device, characteristicCcc);
        }

        characteristicCcc.put(charUuid,
                ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getShort());

        Log.d(TAG, "setCcc, device: " + device.getAddress() + ", UUID: " + charUuid + ", value: "
                + characteristicCcc.get(charUuid));
    }

    private byte[] getCccBytes(BluetoothDevice device, UUID charUuid) {
        Map<UUID, Short> characteristicCcc = mCccDescriptorValues.get(device);
        if (characteristicCcc != null) {
            ByteBuffer bb = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            Short ccc = characteristicCcc.get(charUuid);
            if (ccc != null) {
                bb.putShort(characteristicCcc.get(charUuid));
                return bb.array();
            }
        }

        return BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
    }

    /** Class that handles GATT characteristic notifications */
    private class BluetoothGattCharacteristicNotifier {
        public int setSubscriptionConfiguration(BluetoothDevice device, UUID uuid,
                byte[] configuration) {
            setCcc(device, uuid, configuration);

            return BluetoothGatt.GATT_SUCCESS;
        }

        public byte[] getSubscriptionConfiguration(BluetoothDevice device, UUID uuid) {
            return getCccBytes(device, uuid);
        }

        public boolean isSubscribed(BluetoothDevice device, UUID uuid) {
            return Arrays.equals(getCccBytes(device, uuid),
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        }

        private void notifyCharacteristicChanged(BluetoothDevice device,
                BluetoothGattCharacteristic characteristic, byte[] value) {
            if (getDeviceAuthorization(device) != BluetoothDevice.ACCESS_ALLOWED) return;
            if (value == null) return;
            if (mBluetoothGattServer != null) {
                mBluetoothGattServer.notifyCharacteristicChanged(device, characteristic, false,
                                                                 value);
            }
        }

        private void notifyCharacteristicChanged(BluetoothDevice device,
                BluetoothGattCharacteristic characteristic) {
            if (getDeviceAuthorization(device) != BluetoothDevice.ACCESS_ALLOWED) return;

            if (mBluetoothGattServer != null) {
                mBluetoothGattServer.notifyCharacteristicChanged(device, characteristic, false);
            }
        }

        public void notifyWithValue(BluetoothDevice device,
                BluetoothGattCharacteristic characteristic, byte[] value) {
            if (isSubscribed(device, characteristic.getUuid())) {
                notifyCharacteristicChanged(device, characteristic, value);
            }
        }

        public void notify(BluetoothDevice device, BluetoothGattCharacteristic characteristic) {
            if (isSubscribed(device, characteristic.getUuid())) {
                notifyCharacteristicChanged(device, characteristic);
            }
        }

        public void notifyAll(BluetoothGattCharacteristic characteristic) {
            for (BluetoothDevice device : mCccDescriptorValues.keySet()) {
                notify(device, characteristic);
            }
        }
    }

    /** Wrapper class for BluetoothGattCharacteristic */
    private class GattCharacteristic extends BluetoothGattCharacteristic {

        protected BluetoothGattCharacteristicNotifier mNotifier;

        public GattCharacteristic(UUID uuid, int properties, int permissions) {
            super(uuid, properties, permissions);
            if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                mNotifier = new BluetoothGattCharacteristicNotifier();
                addDescriptor(new ClientCharacteristicConfigurationDescriptor());
            } else {
                mNotifier = null;
            }
        }

        public byte[] getSubscriptionConfiguration(BluetoothDevice device, UUID uuid) {
            return mNotifier.getSubscriptionConfiguration(device, uuid);
        }

        public int setSubscriptionConfiguration(BluetoothDevice device, UUID uuid,
                byte[] configuration) {
            return mNotifier.setSubscriptionConfiguration(device, uuid, configuration);
        }

        private boolean isNotifiable() {
            return mNotifier != null;
        }

        @Override
        public boolean setValue(byte[] value) {
            boolean success = super.setValue(value);
            if (success && isNotifiable()) {
                mNotifier.notifyAll(this);
            }

            return success;
        }

        @Override
        public boolean setValue(int value, int formatType, int offset) {
            boolean success = super.setValue(value, formatType, offset);
            if (success && isNotifiable()) {
                mNotifier.notifyAll(this);
            }

            return success;
        }

        @Override
        public boolean setValue(String value) {
            boolean success = super.setValue(value);
            if (success && isNotifiable()) {
                mNotifier.notifyAll(this);
            }

            return success;
        }

        public boolean setValueNoNotify(byte[] value) {
            return super.setValue(value);
        }

        public boolean notifyWithValue(BluetoothDevice device, byte[] value) {
            if (isNotifiable()) {
                mNotifier.notifyWithValue(device, this, value);
                return true;
            }
            return false;
        }

        public void notify(BluetoothDevice device) {
            if (isNotifiable() && super.getValue() != null) {
                mNotifier.notify(device, this);
            }
        }

        public boolean clearValue(boolean notify) {
            boolean success = super.setValue(new byte[0]);
            if (success && notify && isNotifiable()) {
                mNotifier.notifyAll(this);
            }

            return success;
        }

        public void handleWriteRequest(BluetoothDevice device, int requestId,
                boolean responseNeeded, byte[] value) {
            if (responseNeeded) {
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0,
                        value);
            }
        }
    }

    private class CallControlPointCharacteristic extends GattCharacteristic {

        public CallControlPointCharacteristic() {
            super(UUID_CALL_CONTROL_POINT,
                    PROPERTY_WRITE | PROPERTY_WRITE_NO_RESPONSE | PROPERTY_NOTIFY,
                    PERMISSION_WRITE_ENCRYPTED);
        }

        @Override
        public void handleWriteRequest(BluetoothDevice device, int requestId,
                boolean responseNeeded, byte[] value) {
            int status;
            if (value.length < 2) {
                // at least opcode is required and value is at least 1 byte
                status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
            } else {
                status = BluetoothGatt.GATT_SUCCESS;
            }

            if (responseNeeded) {
                mBluetoothGattServer.sendResponse(device, requestId, status, 0, value);
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                return;
            }

            int opcode = (int) value[0];
            mCallback.onCallControlPointRequest(device, opcode,
                    Arrays.copyOfRange(value, 1, value.length));
        }

        public void setResult(BluetoothDevice device, int requestedOpcode, int callIndex,
                int requestResult) {
            byte[] value = new byte[3];
            value[0] = (byte) (requestedOpcode);
            value[1] = (byte) (callIndex);
            value[2] = (byte) (requestResult);

            super.setValueNoNotify(value);

            // to avoid sending control point notification before write response
            mHandler.post(() -> mNotifier.notify(device, this));
        }
    }

    private class ClientCharacteristicConfigurationDescriptor extends BluetoothGattDescriptor {

        ClientCharacteristicConfigurationDescriptor() {
            super(UUID_CLIENT_CHARACTERISTIC_CONFIGURATION,
                    PERMISSION_WRITE_ENCRYPTED | PERMISSION_READ_ENCRYPTED);
        }

        public byte[] getValue(BluetoothDevice device) {
            GattCharacteristic characteristic = (GattCharacteristic) getCharacteristic();
            byte[] value = characteristic.getSubscriptionConfiguration(device,
                    characteristic.getUuid());
            if (value == null) {
                return BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
            }

            return value;
        }

        public int setValue(BluetoothDevice device, byte[] value) {
            GattCharacteristic characteristic = (GattCharacteristic) getCharacteristic();
            int properties = characteristic.getProperties();

            if (value.length != 2) {
                return BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;

            } else if ((!Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    && !Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                    && !Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE))
                    || ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0 && Arrays
                            .equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE))
                    || ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0 && Arrays
                            .equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE))) {
                return BluetoothGatt.GATT_FAILURE;
            }

            if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                addUuidToMetadata(new ParcelUuid(characteristic.getUuid()), device);
            } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                removeUuidFromMetadata(new ParcelUuid(characteristic.getUuid()), device);
            } else {
                Log.e(TAG, "Not handled CCC value: " + Arrays.toString(value));
            }

            return characteristic.setSubscriptionConfiguration(device, characteristic.getUuid(),
                    value);
        }
    }

    public boolean setBearerProviderName(String providerName) {
        return mBearerProviderNameCharacteristic.setValue(providerName);
    }

    public boolean setBearerTechnology(int technology) {
        return mBearerTechnologyCharacteristic.setValue(technology,
                BluetoothGattCharacteristic.FORMAT_UINT8, 0);
    }

    public boolean setBearerUriSchemesSupportedList(List<String> bearerUriSchemesSupportedList) {
        return mBearerUriSchemesSupportedListCharacteristic
                .setValue(String.join(",", bearerUriSchemesSupportedList));
    }

    public boolean setCallState(Map<Integer, TbsCall> callsList) {
        if (DBG) {
            Log.d(TAG, "setCallState: callsList=" + callsList);
        }
        int i = 0;
        byte[] value = new byte[callsList.size() * 3];
        for (Map.Entry<Integer, TbsCall> entry : callsList.entrySet()) {
            TbsCall call = entry.getValue();
            value[i++] = (byte) (entry.getKey() & 0xff);
            value[i++] = (byte) (call.getState() & 0xff);
            value[i++] = (byte) (call.getFlags() & 0xff);
        }

        return mCallStateCharacteristic.setValue(value);
    }

    public boolean setBearerListCurrentCalls(Map<Integer, TbsCall> callsList) {
        if (DBG) {
            Log.d(TAG, "setBearerListCurrentCalls: callsList=" + callsList);
        }
        final int listItemLengthMax = Byte.MAX_VALUE;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (Map.Entry<Integer, TbsCall> entry : callsList.entrySet()) {
            TbsCall call = entry.getValue();
            if (call == null) {
                Log.w(TAG, "setBearerListCurrentCalls: call is null");
                continue;
            }

            int uri_len = 0;
            if (call.getUri() != null) {
                uri_len =  call.getUri().getBytes().length;
            }

            int listItemLength = Math.min(listItemLengthMax, 3 + uri_len);
            stream.write((byte) (listItemLength & 0xff));
            stream.write((byte) (entry.getKey() & 0xff));
            stream.write((byte) (call.getState() & 0xff));
            stream.write((byte) (call.getFlags() & 0xff));
            if (uri_len > 0) {
                stream.write(call.getUri().getBytes(), 0, listItemLength - 3);
            }
        }

        return mBearerListCurrentCallsCharacteristic.setValue(stream.toByteArray());
    }

    private boolean updateStatusFlags(BluetoothDevice device, int valueInt) {
        /* uint16_t */
        byte[] value = new byte[2];
        value[0] = (byte) (valueInt & 0xFF);
        value[1] = (byte) ((valueInt >> 8) & 0xFF);
        return mStatusFlagsCharacteristic.notifyWithValue(device, value);
    }

    private boolean updateStatusFlagsInbandRingtone(BluetoothDevice device, boolean set) {
        boolean entryExist = mStatusFlagValue.containsKey(device);
        if (entryExist
                && (((mStatusFlagValue.get(device)
                        & STATUS_FLAG_INBAND_RINGTONE_ENABLED) != 0) == set)) {
            Log.i(TAG, "Silent mode already set for " + device);
            return false;
        }

        Integer valueInt = entryExist ? mStatusFlagValue.get(device) : 0;
        valueInt ^= STATUS_FLAG_INBAND_RINGTONE_ENABLED;

        if (entryExist) {
            mStatusFlagValue.replace(device, valueInt);
        } else {
            mStatusFlagValue.put(device, valueInt);
        }
        return updateStatusFlags(device, valueInt);
    }

    private boolean updateStatusFlagsSilentMode(boolean set) {
        mSilentMode = set;
        for (BluetoothDevice device: mCccDescriptorValues.keySet()) {
            boolean entryExist = mStatusFlagValue.containsKey(device);
            if (entryExist
                    && (((mStatusFlagValue.get(device)
                            & STATUS_FLAG_SILENT_MODE_ENABLED) != 0) == set)) {
                Log.i(TAG, "Silent mode already set for " + device);
                continue;
            }

            Integer valueInt = entryExist ? mStatusFlagValue.get(device) : 0;
            valueInt ^= STATUS_FLAG_SILENT_MODE_ENABLED;

            if (entryExist) {
                mStatusFlagValue.replace(device, valueInt);
            } else {
                mStatusFlagValue.put(device, valueInt);
            }
            updateStatusFlags(device, valueInt);
        }
        return true;
    }

    /**
     * Set inband ringtone for the device.
     * When set, notification will be sent to given device.
     *
     * @param device    device for which inband ringtone has been set
     * @return          true, when notification has been sent, false otherwise
     */
    public boolean setInbandRingtoneFlag(BluetoothDevice device) {
        return updateStatusFlagsInbandRingtone(device, true);
    }

    /**
     * Clear inband ringtone for the device.
     * When set, notification will be sent to given device.
     *
     * @param device    device for which inband ringtone has been cleared
     * @return          true, when notification has been sent, false otherwise
     */
    public boolean clearInbandRingtoneFlag(BluetoothDevice device) {
        return updateStatusFlagsInbandRingtone(device, false);
    }
    public boolean setSilentModeFlag() {
        return updateStatusFlagsSilentMode(true);
    }

    public boolean clearSilentModeFlag() {
        return updateStatusFlagsSilentMode(false);
    }

    private void setCallControlPointOptionalOpcodes(boolean isLocalHoldOpcodeSupported,
            boolean isJoinOpcodeSupported) {
        int valueInt = 0;
        if (isLocalHoldOpcodeSupported) {
            valueInt |= CALL_CONTROL_POINT_OPTIONAL_OPCODE_LOCAL_HOLD;
        }
        if (isJoinOpcodeSupported) {
            valueInt |= CALL_CONTROL_POINT_OPTIONAL_OPCODE_JOIN;
        }

        byte[] value = new byte[2];
        value[0] = (byte) (valueInt & 0xff);
        value[1] = (byte) ((valueInt >> 8) & 0xff);

        mCallControlPointOptionalOpcodesCharacteristic.setValue(value);
    }

    public boolean setTerminationReason(int callIndex, int terminationReason) {
        if (DBG) {
            Log.d(TAG, "setTerminationReason: callIndex=" + callIndex + " terminationReason="
                    + terminationReason);
        }
        byte[] value = new byte[2];
        value[0] = (byte) (callIndex & 0xff);
        value[1] = (byte) (terminationReason & 0xff);

        return mTerminationReasonCharacteristic.setValue(value);
    }

    public Integer getIncomingCallIndex() {
        byte[] value = mIncomingCallCharacteristic.getValue();
        if (value == null || value.length == 0) {
            return null;
        }

        return (int) value[0];
    }

    public boolean setIncomingCall(int callIndex, String uri) {
        if (DBG) {
            Log.d(TAG, "setIncomingCall: callIndex=" + callIndex + " uri=" + uri);
        }
        int uri_len = 0;
        if (uri != null) {
            uri_len = uri.length();
        }

        byte[] value = new byte[uri_len + 1];
        value[0] = (byte) (callIndex & 0xff);

        if (uri_len > 0) {
            System.arraycopy(uri.getBytes(), 0, value, 1, uri_len);
        }

        return mIncomingCallCharacteristic.setValue(value);
    }

    public boolean clearIncomingCall() {
        if (DBG) {
            Log.d(TAG, "clearIncomingCall");
        }
        return mIncomingCallCharacteristic.clearValue(false);
    }

    public boolean setCallFriendlyName(int callIndex, String callFriendlyName) {
        if (DBG) {
            Log.d(TAG, "setCallFriendlyName: callIndex=" + callIndex + "callFriendlyName="
                    + callFriendlyName);
        }
        byte[] value = new byte[callFriendlyName.length() + 1];
        value[0] = (byte) (callIndex & 0xff);
        System.arraycopy(callFriendlyName.getBytes(), 0, value, 1, callFriendlyName.length());

        return mCallFriendlyNameCharacteristic.setValue(value);
    }

    public Integer getCallFriendlyNameIndex() {
        byte[] value = mCallFriendlyNameCharacteristic.getValue();
        if (value == null || value.length == 0) {
            return null;
        }

        return (int) value[0];
    }

    public boolean clearFriendlyName() {
        if (DBG) {
            Log.d(TAG, "clearFriendlyName");
        }
        return mCallFriendlyNameCharacteristic.clearValue(false);
    }

    public void setCallControlPointResult(BluetoothDevice device, int requestedOpcode,
            int callIndex, int requestResult) {
        if (DBG) {
            Log.d(TAG,
                    "setCallControlPointResult: device=" + device + " requestedOpcode="
                            + requestedOpcode + " callIndex=" + callIndex + " requesuResult="
                            + requestResult);
        }
        mCallControlPointCharacteristic.setResult(device, requestedOpcode, callIndex,
                requestResult);
    }

    private static UUID makeUuid(String uuid16) {
        return UUID.fromString(UUID_PREFIX + uuid16 + UUID_SUFFIX);
    }

    private void restoreCccValuesForStoredDevices() {
        BluetoothGattService gattService = mBluetoothGattServer.getService(UUID_GTBS);

        for (BluetoothDevice device : mAdapterService.getBondedDevices()) {
            byte[] gtbs_cccd = device.getMetadata(METADATA_GTBS_CCCD);

            if ((gtbs_cccd == null) || (gtbs_cccd.length == 0)) {
                return;
            }

            List<ParcelUuid> uuidList = Arrays.asList(Utils.byteArrayToUuid(gtbs_cccd));

            /* Restore CCCD values for device */
            for (ParcelUuid uuid : uuidList) {
                BluetoothGattCharacteristic characteristic =
                        gattService.getCharacteristic(uuid.getUuid());
                if (characteristic == null) {
                    Log.e(TAG, "Invalid UUID stored in metadata: " + uuid.toString());
                    continue;
                }

                BluetoothGattDescriptor descriptor =
                        characteristic.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIGURATION);
                if (descriptor == null) {
                    Log.e(TAG, "Invalid characteristic, does not include CCCD");
                    continue;
                }

                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
        }
    }

    private final AdapterService.BluetoothStateCallback mBluetoothStateChangeCallback =
            new AdapterService.BluetoothStateCallback() {
                public void onBluetoothStateChange(int prevState, int newState) {
                    if (DBG) {
                        Log.d(
                                TAG,
                                "onBluetoothStateChange: state="
                                        + BluetoothAdapter.nameForState(newState));
                    }
                    if (newState == BluetoothAdapter.STATE_ON) {
                        restoreCccValuesForStoredDevices();
                    }
                }
            };

    private int getDeviceAuthorization(BluetoothDevice device) {
        return mTbsService.getDeviceAuthorization(device);
    }

    private void onRejectedAuthorizationGattOperation(BluetoothDevice device, GattOpContext op) {
        UUID charUuid = (op.mCharacteristic != null ? op.mCharacteristic.getUuid()
                : (op.mDescriptor != null ? op.mDescriptor.getCharacteristic().getUuid() : null));
        mEventLogger.logw(TAG, "onRejectedAuthorizationGattOperation device: " + device
                        + ", opcode= " + op.mOperation
                        + ", characteristic= "
                        + (charUuid != null ? tbsUuidToString(charUuid) : "UNKNOWN"));

        switch (op.mOperation) {
            case READ_CHARACTERISTIC:
            case READ_DESCRIPTOR:
                mBluetoothGattServer.sendResponse(device, op.mRequestId,
                        BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, op.mOffset, null);
                break;
            case WRITE_CHARACTERISTIC:
                if (op.mResponseNeeded) {
                    mBluetoothGattServer.sendResponse(device, op.mRequestId,
                            BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, op.mOffset, null);
                } else {
                    // In case of control point operations we can send an application error code
                    if (op.mCharacteristic.getUuid().equals(UUID_CALL_CONTROL_POINT)) {
                        setCallControlPointResult(device, op.mOperation.ordinal(), 0,
                                TbsGatt.CALL_CONTROL_POINT_RESULT_OPERATION_NOT_POSSIBLE);
                    }
                }
                break;
            case WRITE_DESCRIPTOR:
                if (op.mResponseNeeded) {
                    mBluetoothGattServer.sendResponse(device, op.mRequestId,
                            BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, op.mOffset, null);
                }
                break;

            default:
                break;
        }
    }

    private void onUnauthorizedCharRead(BluetoothDevice device, GattOpContext op) {
        UUID charUuid = op.mCharacteristic.getUuid();
        boolean allowToReadRealValue = false;
        byte[] buffer = null;

        /* Allow only some informations to be disclosed at this stage. */
        if (charUuid.equals(UUID_BEARER_PROVIDER_NAME)) {
            ByteBuffer bb = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN);
            bb.put("".getBytes());
            buffer = bb.array();

        } else if (charUuid.equals(UUID_BEARER_UCI)) {
            buffer = "E.164".getBytes();

        } else if (charUuid.equals(UUID_BEARER_TECHNOLOGY)) {
            allowToReadRealValue = true;

        } else if (charUuid.equals(UUID_BEARER_URI_SCHEMES_SUPPORTED_LIST)) {
            ByteBuffer bb = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN);
            bb.put("".getBytes());
            buffer = bb.array();

        } else if (charUuid.equals(UUID_BEARER_LIST_CURRENT_CALLS)) {
            ByteBuffer bb = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN);
            bb.put("".getBytes());
            buffer = bb.array();

        } else if (charUuid.equals(UUID_CONTENT_CONTROL_ID)) {
            allowToReadRealValue = true;

        } else if (charUuid.equals(UUID_STATUS_FLAGS)) {
            allowToReadRealValue = true;

        } else if (charUuid.equals(UUID_CALL_STATE)) {
            ByteBuffer bb = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN);
            bb.put("".getBytes());
            buffer = bb.array();

        } else if (charUuid.equals(UUID_CALL_CONTROL_POINT)) {
            // No read is available on this characteristic

        } else if (charUuid.equals(UUID_CALL_CONTROL_POINT_OPTIONAL_OPCODES)) {
            ByteBuffer bb = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN);
            bb.put((byte) 0x00);
            buffer = bb.array();

        } else if (charUuid.equals(UUID_TERMINATION_REASON)) {
            // No read is available on this characteristic

        } else if (charUuid.equals(UUID_INCOMING_CALL)) {
            ByteBuffer bb = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN);
            bb.put("".getBytes());
            buffer = bb.array();

        } else if (charUuid.equals(UUID_CALL_FRIENDLY_NAME)) {
            ByteBuffer bb = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN);
            bb.put((byte) 0x00);
            buffer = bb.array();
        }

        if (allowToReadRealValue) {
            if (op.mCharacteristic.getValue() != null) {
                buffer =
                        Arrays.copyOfRange(
                                op.mCharacteristic.getValue(),
                                op.mOffset,
                                op.mCharacteristic.getValue().length);
            }
        }

        if (buffer != null) {
            mBluetoothGattServer.sendResponse(
                    device, op.mRequestId, BluetoothGatt.GATT_SUCCESS, op.mOffset, buffer);
        } else {
            mEventLogger.loge(TAG, "Missing characteristic value for char: "
                    + tbsUuidToString(charUuid));
            mBluetoothGattServer.sendResponse(
                    device,
                    op.mRequestId,
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH,
                    op.mOffset,
                    buffer);
        }
    }

    private void onUnauthorizedGattOperation(BluetoothDevice device, GattOpContext op) {
        UUID charUuid = (op.mCharacteristic != null ? op.mCharacteristic.getUuid()
                : (op.mDescriptor != null ? op.mDescriptor.getCharacteristic().getUuid() : null));
        mEventLogger.logw(TAG, "onUnauthorizedGattOperation device: " + device
                        + ", opcode= " + op.mOperation
                        + ", characteristic= "
                        + (charUuid != null ? tbsUuidToString(charUuid) : "UNKNOWN"));

        int status = BluetoothGatt.GATT_SUCCESS;

        switch (op.mOperation) {
            /* Allow not yet authorized devices to subscribe for notifications */
            case READ_DESCRIPTOR:
                byte[] value = getCccBytes(device, op.mDescriptor.getCharacteristic().getUuid());
                if (value.length < op.mOffset) {
                    status = BluetoothGatt.GATT_INVALID_OFFSET;
                } else {
                    value = Arrays.copyOfRange(value, op.mOffset, value.length);
                    status = BluetoothGatt.GATT_SUCCESS;
                }

                mBluetoothGattServer.sendResponse(device, op.mRequestId, status, op.mOffset, value);
                return;
            case WRITE_DESCRIPTOR:
                if (op.mPreparedWrite) {
                    status = BluetoothGatt.GATT_FAILURE;
                } else if (op.mOffset > 0) {
                    status = BluetoothGatt.GATT_INVALID_OFFSET;
                } else if (op.mValue.length != 2) {
                    status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
                } else {
                    status = BluetoothGatt.GATT_SUCCESS;
                    setCcc(device, op.mDescriptor.getCharacteristic().getUuid(), op.mValue);
                }

                if (op.mResponseNeeded) {
                    mBluetoothGattServer.sendResponse(
                            device, op.mRequestId, status, op.mOffset, op.mValue);
                }
                return;
            case READ_CHARACTERISTIC:
                onUnauthorizedCharRead(device, op);
                return;
            case WRITE_CHARACTERISTIC:
                // store as pending operation
                break;
            default:
                break;
        }

        synchronized (mPendingGattOperationsLock) {
            List<GattOpContext> operations = mPendingGattOperations.get(device);
            if (operations == null) {
                operations = new ArrayList<>();
                mPendingGattOperations.put(device, operations);
            }

            operations.add(op);
            // Send authorization request for each device only for it's first GATT request
            if (operations.size() == 1) {
                mTbsService.onDeviceUnauthorized(device);
            }
        }
    }

    private void onAuthorizedGattOperation(BluetoothDevice device, GattOpContext op) {
        int status = BluetoothGatt.GATT_SUCCESS;
        ClientCharacteristicConfigurationDescriptor cccd;
        byte[] value;

        UUID charUuid = (op.mCharacteristic != null ? op.mCharacteristic.getUuid()
                : (op.mDescriptor != null ? op.mDescriptor.getCharacteristic().getUuid() : null));
        mEventLogger.logd(TAG, "onAuthorizedGattOperation device: " + device
                        + ", opcode= " + op.mOperation
                        + ", characteristic= "
                        + (charUuid != null ? tbsUuidToString(charUuid) : "UNKNOWN"));

        switch (op.mOperation) {
            case READ_CHARACTERISTIC:
                if (DBG) {
                    Log.d(TAG, "onCharacteristicReadRequest: device=" + device);
                }

                if (getDeviceAuthorization(device) != BluetoothDevice.ACCESS_ALLOWED) {
                    onRejectedAuthorizationGattOperation(device, op);
                    return;
                }

                if (op.mCharacteristic.getUuid().equals(UUID_STATUS_FLAGS)) {
                    value = new byte[2];
                    int valueInt = mSilentMode ? STATUS_FLAG_SILENT_MODE_ENABLED : 0;
                    if (mStatusFlagValue.containsKey(device)) {
                        valueInt = mStatusFlagValue.get(device);
                    } else if (mCallback.isInbandRingtoneEnabled(device)) {
                        valueInt |= STATUS_FLAG_INBAND_RINGTONE_ENABLED;
                    }
                    value[0] = (byte) (valueInt & 0xFF);
                    value[1] = (byte) ((valueInt >> 8) & 0xFF);
                } else {
                    GattCharacteristic gattCharacteristic = (GattCharacteristic) op.mCharacteristic;
                    value = gattCharacteristic.getValue();
                    if (value == null) {
                        value = new byte[0];
                    }
                }

                if (value.length < op.mOffset) {
                    status = BluetoothGatt.GATT_INVALID_OFFSET;
                } else {
                    value = Arrays.copyOfRange(value, op.mOffset, value.length);
                    status = BluetoothGatt.GATT_SUCCESS;
                }

                mBluetoothGattServer.sendResponse(device, op.mRequestId, status, op.mOffset, value);
                break;

            case WRITE_CHARACTERISTIC:
                if (DBG) {
                    Log.d(TAG, "onCharacteristicWriteRequest: device=" + device);
                }

                if (getDeviceAuthorization(device) != BluetoothDevice.ACCESS_ALLOWED) {
                    onRejectedAuthorizationGattOperation(device, op);
                    return;
                }

                GattCharacteristic gattCharacteristic = (GattCharacteristic) op.mCharacteristic;
                if (op.mPreparedWrite) {
                    status = BluetoothGatt.GATT_FAILURE;
                } else if (op.mOffset > 0) {
                    status = BluetoothGatt.GATT_INVALID_OFFSET;
                } else {
                    gattCharacteristic.handleWriteRequest(device, op.mRequestId, op.mResponseNeeded,
                            op.mValue);
                    return;
                }

                if (op.mResponseNeeded) {
                    mBluetoothGattServer.sendResponse(device, op.mRequestId, status, op.mOffset,
                            op.mValue);
                }
                break;

            case READ_DESCRIPTOR:
                if (DBG) {
                    Log.d(TAG, "onDescriptorReadRequest: device=" + device);
                }

                if (getDeviceAuthorization(device) != BluetoothDevice.ACCESS_ALLOWED) {
                    onRejectedAuthorizationGattOperation(device, op);
                    return;
                }

                cccd = (ClientCharacteristicConfigurationDescriptor) op.mDescriptor;
                value = cccd.getValue(device);
                if (value.length < op.mOffset) {
                    status = BluetoothGatt.GATT_INVALID_OFFSET;
                } else {
                    value = Arrays.copyOfRange(value, op.mOffset, value.length);
                    status = BluetoothGatt.GATT_SUCCESS;
                }

                mBluetoothGattServer.sendResponse(device, op.mRequestId, status, op.mOffset, value);
                break;

            case WRITE_DESCRIPTOR:
                if (DBG) {
                    Log.d(TAG, "onDescriptorWriteRequest: device=" + device);
                }

                if (getDeviceAuthorization(device) != BluetoothDevice.ACCESS_ALLOWED) {
                    onRejectedAuthorizationGattOperation(device, op);
                    return;
                }

                cccd = (ClientCharacteristicConfigurationDescriptor) op.mDescriptor;
                if (op.mPreparedWrite) {
                    // TODO: handle prepareWrite
                    status = BluetoothGatt.GATT_FAILURE;
                } else if (op.mOffset > 0) {
                    status = BluetoothGatt.GATT_INVALID_OFFSET;
                } else if (op.mValue.length != 2) {
                    status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
                } else {
                    status = cccd.setValue(device, op.mValue);
                }

                if (op.mResponseNeeded) {
                    mBluetoothGattServer.sendResponse(device, op.mRequestId, status, op.mOffset,
                            op.mValue);
                }
                break;

            default:
                break;
        }
    }

    private GattCharacteristic getLocalCharacteristicWrapper(UUID uuid) {
        if (uuid.equals(UUID_BEARER_PROVIDER_NAME)) {
            return mBearerProviderNameCharacteristic;
        } else if (uuid.equals(UUID_BEARER_UCI)) {
            return mBearerUciCharacteristic;
        } else if (uuid.equals(UUID_BEARER_TECHNOLOGY)) {
            return mBearerTechnologyCharacteristic;
        } else if (uuid.equals(UUID_BEARER_URI_SCHEMES_SUPPORTED_LIST)) {
            return mBearerUriSchemesSupportedListCharacteristic;
        } else if (uuid.equals(UUID_BEARER_LIST_CURRENT_CALLS)) {
            return mBearerListCurrentCallsCharacteristic;
        } else if (uuid.equals(UUID_CONTENT_CONTROL_ID)) {
            return mContentControlIdCharacteristic;
        } else if (uuid.equals(UUID_STATUS_FLAGS)) {
            return mStatusFlagsCharacteristic;
        } else if (uuid.equals(UUID_CALL_STATE)) {
            return mCallStateCharacteristic;
        } else if (uuid.equals(UUID_CALL_CONTROL_POINT)) {
            return mCallControlPointCharacteristic;
        } else if (uuid.equals(UUID_CALL_CONTROL_POINT_OPTIONAL_OPCODES)) {
            return mCallControlPointOptionalOpcodesCharacteristic;
        } else if (uuid.equals(UUID_TERMINATION_REASON)) {
            return mTerminationReasonCharacteristic;
        } else if (uuid.equals(UUID_INCOMING_CALL)) {
            return mIncomingCallCharacteristic;
        } else if (uuid.equals(UUID_CALL_FRIENDLY_NAME)) {
            return mCallFriendlyNameCharacteristic;
        }

        return null;
    }

    /**
     * Callback for TBS GATT instance about authorization change for device.
     *
     * @param device device for which authorization is changed
     */
    public void onDeviceAuthorizationSet(BluetoothDevice device) {
        int auth = getDeviceAuthorization(device);
        mEventLogger.logd(TAG, "onDeviceAuthorizationSet: device= " + device
                + ", authorization= " + (auth == BluetoothDevice.ACCESS_ALLOWED ? "ALLOWED"
                        : (auth == BluetoothDevice.ACCESS_REJECTED ? "REJECTED" : "UNKNOWN")));
        processPendingGattOperations(device);

        if (auth != BluetoothDevice.ACCESS_ALLOWED) {
            return;
        }

        BluetoothGattService gattService = mBluetoothGattServer.getService(UUID_GTBS);
        if (gattService != null) {
            List<BluetoothGattCharacteristic> characteristics = gattService.getCharacteristics();
            for (BluetoothGattCharacteristic characteristic : characteristics) {
                GattCharacteristic wrapper =
                        getLocalCharacteristicWrapper(characteristic.getUuid());
                if (wrapper != null) {
                    /* Value of status flags is not keep in the characteristic but in the
                     * mStatusFlagValue
                     */
                    if (characteristic.getUuid().equals(UUID_STATUS_FLAGS)) {
                        if (mStatusFlagValue.containsKey(device)) {
                            updateStatusFlags(device, mStatusFlagValue.get(device));
                        }
                    } else {
                        wrapper.notify(device);
                    }
                }
            }
        }
    }

    private void clearUnauthorizedGattOperationss(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "clearUnauthorizedGattOperationss device: " + device);
        }

        synchronized (mPendingGattOperationsLock) {
            mPendingGattOperations.remove(device);
        }
    }

    private void processPendingGattOperations(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "processPendingGattOperations device: " + device);
        }

        synchronized (mPendingGattOperationsLock) {
            if (mPendingGattOperations.containsKey(device)) {
                if (getDeviceAuthorization(device) == BluetoothDevice.ACCESS_ALLOWED) {
                    for (GattOpContext op : mPendingGattOperations.get(device)) {
                        onAuthorizedGattOperation(device, op);
                    }
                } else {
                    for (GattOpContext op : mPendingGattOperations.get(device)) {
                        onRejectedAuthorizationGattOperation(device, op);
                    }
                }
                clearUnauthorizedGattOperationss(device);
            }
        }
    }

    /**
     * Callback to handle incoming requests to the GATT server. All read/write requests for
     * characteristics and descriptors are handled here.
     */
    @VisibleForTesting
    final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (DBG) {
                Log.d(TAG, "BluetoothGattServerCallback: onConnectionStateChange");
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                clearUnauthorizedGattOperationss(device);
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            if (DBG) {
                Log.d(TAG, "onServiceAdded: status=" + status);
            }
            if (mCallback != null) {
                mCallback.onServiceAdded(status == BluetoothGatt.GATT_SUCCESS);
            }

            restoreCccValuesForStoredDevices();
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            if (DBG) {
                Log.d(TAG, "BluetoothGattServerCallback: onCharacteristicReadRequest offset= "
                        + offset + " entire value= " + Arrays.toString(characteristic.getValue()));
            }

            if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
                mBluetoothGattServer.sendResponse(device, requestId,
                        BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
                return;
            }

            GattOpContext op = new GattOpContext(
                    GattOpContext.Operation.READ_CHARACTERISTIC, requestId, characteristic, null);
            switch (getDeviceAuthorization(device)) {
                case BluetoothDevice.ACCESS_REJECTED:
                    onRejectedAuthorizationGattOperation(device, op);
                    break;
                case BluetoothDevice.ACCESS_UNKNOWN:
                    onUnauthorizedGattOperation(device, op);
                    break;
                default:
                    onAuthorizedGattOperation(device, op);
                    break;
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
                    responseNeeded, offset, value);
            if (DBG) {
                Log.d(TAG,
                        "BluetoothGattServerCallback: "
                                + "onCharacteristicWriteRequest");
            }

            if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE)
                    == 0) {
                mBluetoothGattServer.sendResponse(
                        device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, value);
                return;
            }

            GattOpContext op = new GattOpContext(GattOpContext.Operation.WRITE_CHARACTERISTIC,
                    requestId, characteristic, null, preparedWrite, responseNeeded, offset, value);
            switch (getDeviceAuthorization(device)) {
                case BluetoothDevice.ACCESS_REJECTED:
                    onRejectedAuthorizationGattOperation(device, op);
                    break;
                case BluetoothDevice.ACCESS_UNKNOWN:
                    onUnauthorizedGattOperation(device, op);
                    break;
                default:
                    onAuthorizedGattOperation(device, op);
                    break;
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            if (DBG) {
                Log.d(TAG,
                        "BluetoothGattServerCallback: "
                                + "onDescriptorReadRequest");
            }

            if ((descriptor.getPermissions() & BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED)
                    == 0) {
                mBluetoothGattServer.sendResponse(
                        device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null);
                return;
            }

            GattOpContext op = new GattOpContext(
                    GattOpContext.Operation.READ_DESCRIPTOR, requestId, null, descriptor);
            switch (getDeviceAuthorization(device)) {
                case BluetoothDevice.ACCESS_REJECTED:
                    onRejectedAuthorizationGattOperation(device, op);
                    break;
                case BluetoothDevice.ACCESS_UNKNOWN:
                    onUnauthorizedGattOperation(device, op);
                    break;
                default:
                    onAuthorizedGattOperation(device, op);
                    break;
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded,
                int offset, byte[] value) {
            super.onDescriptorWriteRequest(
                    device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            if (DBG) {
                Log.d(TAG,
                        "BluetoothGattServerCallback: "
                                + "onDescriptorWriteRequest");
            }

            if ((descriptor.getPermissions() & BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED)
                    == 0) {
                mBluetoothGattServer.sendResponse(
                        device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, value);
                return;
            }

            GattOpContext op = new GattOpContext(GattOpContext.Operation.WRITE_DESCRIPTOR,
                    requestId, null, descriptor, preparedWrite, responseNeeded, offset, value);
            switch (getDeviceAuthorization(device)) {
                case BluetoothDevice.ACCESS_REJECTED:
                    onRejectedAuthorizationGattOperation(device, op);
                    break;
                case BluetoothDevice.ACCESS_UNKNOWN:
                    onUnauthorizedGattOperation(device, op);
                    break;
                default:
                    onAuthorizedGattOperation(device, op);
                    break;
            }
        }
    };

    public void dump(StringBuilder sb) {
        sb.append("\n\tSilent mode: " + mSilentMode);

        for (Map.Entry<BluetoothDevice, HashMap<UUID, Short>> deviceEntry
                : mCccDescriptorValues.entrySet()) {
            sb.append("\n\tCCC states for device: " + deviceEntry.getKey());
            for (Map.Entry<UUID, Short> entry : deviceEntry.getValue().entrySet()) {
                sb.append("\n\t\tCharacteristic: " + tbsUuidToString(entry.getKey()) + ", value: "
                        + Utils.cccIntToStr(entry.getValue()));
            }
        }

        if (mEventLogger != null) {
            sb.append("\n\n");
            mEventLogger.dump(sb);
        }
    }
}
