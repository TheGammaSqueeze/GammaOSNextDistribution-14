/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.bluetooth;

import static com.android.car.bluetooth.FastPairAccountKeyStorage.AccountKey;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.car.Car;
import android.car.PlatformVersion;
import android.car.builtin.bluetooth.le.AdvertisingSetCallbackHelper;
import android.car.builtin.bluetooth.le.AdvertisingSetHelper;
import android.car.builtin.util.Slogf;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;

import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The FastPairAdvertiser is responsible for the BLE advertisement of either the model ID while
 * in pairing mode or the stored account keys while not in pairing mode.
 *
 * This advertiser should always be advertising either the model ID or the account key filter if the
 * Bluetooth adapter is on.
 *
 * Additionally, the Fast Pair Advertiser is the only entity allowed to receive notifications about
 * our private address, which is used by the protocol to verify the remote device we're talking to.
 *
 * Advertisement packet formats and timing/intervals are described by the Fast Pair specification
 */
public class FastPairAdvertiser {
    private static final String TAG = CarLog.tagFor(FastPairAdvertiser.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    public static final int STATE_STOPPED = 0;
    public static final int STATE_STARTING = 1;
    public static final int STATE_STARTED = 2;
    public static final int STATE_STOPPING = 3;

    // Service ID assigned for FastPair.
    public static final ParcelUuid SERVICE_UUID = ParcelUuid
            .fromString("0000FE2C-0000-1000-8000-00805f9b34fb");

    private static final byte ACCOUNT_KEY_FILTER_FLAGS = 0x00;
    private static final byte SALT_FIELD_DESCRIPTOR = 0x11;

    private final Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private AdvertisingSetParameters mAdvertisingSetParameters;
    private AdvertisingSetCallback mAdvertisingSetCallback;
    private AdvertiseData mData;
    private int mTxPower = 0;
    private Callbacks mCallbacks;

    private final AdvertisingHandler mAdvertisingHandler;

    /**
     * Receive events from this FastPairAdvertiser
     */
    public interface Callbacks {
        /**
         * Notify the Resolvable Private Address of the BLE advertiser.
         *
         * @param device The current LE address
         */
        void onRpaUpdated(BluetoothDevice device);
    }

    FastPairAdvertiser(Context context) {
        mContext = context;
        mBluetoothAdapter = mContext.getSystemService(BluetoothManager.class).getAdapter();
        Objects.requireNonNull(mBluetoothAdapter, "Bluetooth adapter cannot be null");
        mAdvertisingHandler = new AdvertisingHandler();
        initializeAdvertisingSetCallback();
    }

    /**
     * Advertise the Fast Pair model ID.
     *
     * Model ID advertisements have the following format:
     *
     * Octet | Type   | Description                             | Value
     * --------------------------------------------------------------------------------------------
     * 0-2   | uint24 | 24-bit Model ID                         | varies, example: 0x123456
     * --------------------------------------------------------------------------------------------
     *
     * Ensure advertising is stopped before switching the underlying advertising data. This can be
     * done by calling stopAdvertising().
     */
    public void advertiseModelId(int modelId, Callbacks callback) {
        if (DBG) {
            Slogf.d(TAG, "advertiseModelId(id=0x%s)", Integer.toHexString(modelId));
        }

        ByteBuffer modelIdBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(
                modelId);
        mAdvertisingHandler.startAdvertising(Arrays.copyOfRange(modelIdBytes.array(), 1, 4),
                AdvertisingSetParameters.INTERVAL_LOW, callback);
    }

    /**
     * Advertise the stored account keys.
     *
     * Account Keys advertisements have the following format:
     *
     * Octet | Type   | Description                             | Value
     * --------------------------------------------------------------------------------------------
     * 0     | uint8  | Flags, all bits reserved for future use | 0x00
     * --------------------------------------------------------------------------------------------
     * 1-N   |        | Account Key Data                        | 0x00, if empty
     *       |        |                                         | bloom(account keys), otherwise
     * --------------------------------------------------------------------------------------------
     *
     * The Account Key Data has the following format:
     *
     * Octet | Type   | Description                             | Value
     * --------------------------------------------------------------------------------------------
     * 0     | uint8  | 0bLLLLTTTT (T=type, L=Length)           | length=0bLLLL, 4 bit field length
     *       |        |                                         | type=0bTTTT, 0b0000 (show UI)
     *       |        |                                         | type=0bTTTT, 0b0010 (hide UI)
     * --------------------------------------------------------------------------------------------
     * 1-N   |        | Account Key Filter                      | 0x00, if empty
     * --------------------------------------------------------------------------------------------
     * N+1   | uint8  | Salt Field Length and Type              | 0b00010001
     * --------------------------------------------------------------------------------------------
     * N+2   | uint8  | Salt                                    | varies
     * --------------------------------------------------------------------------------------------
     *
     * The Account Key Filter is a bloom filter representation of the stored keys. The filter alone
     * requires 1.2 * <number of keys> + 3 bytes. This means an Account Key Filter packet is a total
     * size of 4 (flags, filter field id + length, salt field id + length, salt) + 1.2 * <keys> + 3
     * bytes.
     *
     * Keep this in mind when defining your max keys size, as it will directly impact the size of
     * advertisement data and packet. Make sure your controller supports your maximum advertisement
     * size.
     *
     * Ensure advertising is stopped before switching the underlying advertising data. This can be
     * done by calling stopAdvertising().
     */
    public void advertiseAccountKeys(List<AccountKey> accountKeys, Callbacks callback) {
        if (DBG) {
            Slogf.d(TAG, "advertiseAccountKeys(keys=%s)", accountKeys);
        }

        // If we have account keys, then create a salt value and generate the account key filter
        byte[] accountKeyFilter = null;
        byte[] salt = null;
        if (accountKeys != null && accountKeys.size() > 0) {
            salt = new byte[1];
            new Random().nextBytes(salt);
            accountKeyFilter = getAccountKeyFilter(accountKeys, salt[0]);
        }

        // If we have an account key filter, then create an advertisement payload using it and the
        // salt. Otherwise, create an empty advertisement.
        ByteBuffer accountKeyAdvertisement = null;
        if (accountKeyFilter != null) {
            int size = accountKeyFilter.length;
            accountKeyAdvertisement = ByteBuffer.allocate(size + 4); // filter + 3b flags + 1b salt
            accountKeyAdvertisement.put(ACCOUNT_KEY_FILTER_FLAGS); // Reserved Flags byte
            accountKeyAdvertisement.put((byte) (size << 4)); // Length Type and Size, 0bLLLLTTTT
            accountKeyAdvertisement.put(accountKeyFilter); // Account Key Bloom Results
            accountKeyAdvertisement.put(SALT_FIELD_DESCRIPTOR); // Salt Field/Size, 0bLLLLTTTT
            accountKeyAdvertisement.put(salt); // The actual 1 byte of salt
        } else {
            accountKeyAdvertisement = ByteBuffer.allocate(2);
            accountKeyAdvertisement.put((byte) 0x00); // Reserved Flags Byte
            accountKeyAdvertisement.put((byte) 0x00); // Empty Keys Byte
        }

        mAdvertisingHandler.startAdvertising(accountKeyAdvertisement.array(),
                AdvertisingSetParameters.INTERVAL_MEDIUM, callback);
    }

    /**
     * Calculate the account key filter, defined as the bloom of the set of account keys.
     *
     * @param keys The list of Fast Pair Account keys
     * @param salt The salt to be used here, as well as appended to the Account Data Advertisment
     * @return A byte array representing the account key filter
     */
    byte[] getAccountKeyFilter(List<AccountKey> keys, byte salt) {
        if (keys == null || keys.size() <= 0) {
            Slogf.e(TAG, "Cannot generate account key filter, keys=%s, salt=%s", keys, salt);
            return null;
        }

        int size = (int) (1.2 * keys.size()) + 3;
        byte[] filter = new byte[size];

        for (AccountKey key : keys) {
            byte[] v = Arrays.copyOf(key.toBytes(), 17);
            v[16] = salt;
            try {
                byte[] hashed = MessageDigest.getInstance("SHA-256").digest(v);
                ByteBuffer byteBuffer = ByteBuffer.wrap(hashed);
                for (int j = 0; j < 8; j++) {
                    long k = Integer.toUnsignedLong(byteBuffer.getInt()) % (size * 8);
                    filter[(int) (k / 8)] |= (byte) (1 << (k % 8));
                }
            } catch (Exception e) {
                Slogf.e(TAG, "Error calculating account key filter: %s", e);
                return null;
            }
        }
        return filter;
    }

    /**
     * Stop advertising any data.
     */
    public void stopAdvertising() {
        if (DBG) {
            Slogf.d(TAG, "stoppingAdvertising");
        }
        mAdvertisingHandler.stopAdvertising();
    }

    /**
     * Start a BLE advertisement using the given data, interval, and callbacks.
     *
     * Must be called on the Advertising Handler.
     *
     * @param data The data to advertise
     * @param interval The interval at which to advertise
     * @param callbacks The callback object to notify of FastPairAdvertiser events
     */
    private boolean startAdvertisingInternal(byte[] data, int interval, Callbacks callbacks) {
        if (DBG) {
            Slogf.d(TAG, "startAdvertisingInternal(data=%s, internval=%d, cb=%s)",
                    Arrays.toString(data), interval, callbacks);
        }

        mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        if (mBluetoothLeAdvertiser == null) {
            Slogf.e(TAG, "startAdvertisingInternal: Failed to get an advertiser.");
            mBluetoothLeAdvertiser = null;
            return false;
        }

        mAdvertisingSetParameters = new AdvertisingSetParameters.Builder()
                .setLegacyMode(true)
                .setInterval(interval)
                .setScannable(true)
                .setConnectable(true)
                .build();
        mData = new AdvertiseData.Builder()
                .addServiceUuid(SERVICE_UUID)
                .addServiceData(SERVICE_UUID, data)
                .setIncludeTxPowerLevel(true)
                .build();
        mCallbacks = callbacks;

        mBluetoothLeAdvertiser.startAdvertisingSet(mAdvertisingSetParameters, mData, null, null,
                null, mAdvertisingSetCallback);
        return true;
    }

    /**
     * Stop advertising any data.
     *
     * This must be called on the Advertising Handler.
     */
    private void stopAdvertisingInternal() {
        if (DBG) {
            Slogf.d(TAG, "stoppingAdvertisingInternal");
        }

        if (mBluetoothLeAdvertiser == null) return;

        mBluetoothLeAdvertiser.stopAdvertisingSet(mAdvertisingSetCallback);
        mTxPower = 0;
        mBluetoothLeAdvertiser = null;
    }

    public boolean isAdvertising() {
        return getAdvertisingState() == STATE_STARTED;
    }

    public int getAdvertisingState() {
        return mAdvertisingHandler.getState();
    }

    private void initializeAdvertisingSetCallback() {
        // Certain functionality of {@link AdvertisingSetCallback} were disabled in
        // {@code TIRAMISU} (major == 33, minor == 0) due to hidden API usage. These functionality
        // were later restored, but require platform version to be at least TM-QPR-1
        // (major == 33, minor == 1).
        PlatformVersion version = Car.getPlatformVersion();
        if (DBG) {
            Slogf.d(TAG, "AdvertisingSetCallback running on platform version (major=%d, minor=%d)",
                    version.getMajorVersion(), version.getMinorVersion());
        }
        if (version.isAtLeast(PlatformVersion.VERSION_CODES.TIRAMISU_1)) {
            AdvertisingSetCallbackHelper.Callback proxy =
                    new AdvertisingSetCallbackHelper.Callback() {
                @Override
                public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower,
                        int status) {
                    onAdvertisingSetStartedHandler(advertisingSet, txPower, status);
                    if (advertisingSet != null) {
                        AdvertisingSetHelper.getOwnAddress(advertisingSet);
                    }
                }

                @Override
                public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
                    onAdvertisingSetStoppedHandler(advertisingSet);
                }

                @Override
                public void onOwnAddressRead(AdvertisingSet advertisingSet, int addressType,
                        String address) {
                    onOwnAddressReadHandler(addressType, address);
                }
            };

            mAdvertisingSetCallback =
                    AdvertisingSetCallbackHelper.createRealCallbackFromProxy(proxy);
        } else {
            mAdvertisingSetCallback = new AdvertisingSetCallback() {
                @Override
                public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower,
                        int status) {
                    onAdvertisingSetStartedHandler(advertisingSet, txPower, status);
                    // TODO(b/241933163): once there are formal APIs to get own address, this
                    // warning can be removed.
                    Slogf.w(TAG, "AdvertisingSet#getOwnAddress not called."
                            + " This feature is not supported in this platform version.");
                }

                @Override
                public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
                    onAdvertisingSetStoppedHandler(advertisingSet);
                }
            };
        }
    }

    // For {@link AdvertisingSetCallback#onAdvertisingSetStarted} and its proxy
    private void onAdvertisingSetStartedHandler(AdvertisingSet advertisingSet, int txPower,
            int status) {
        if (DBG) {
            Slogf.d(TAG, "onAdvertisingSetStarted(): txPower: %d, status: %d", txPower, status);
        }
        if (status != AdvertisingSetCallback.ADVERTISE_SUCCESS || advertisingSet == null) {
            Slogf.w(TAG, "Failed to start advertising, status=%s, advertiser=%s",
                    BluetoothUtils.getAdvertisingCallbackStatusName(status), advertisingSet);
            mAdvertisingHandler.advertisingStopped();
            return;
        }
        mTxPower = txPower;
        mAdvertisingHandler.advertisingStarted();
    }

    // For {@link AdvertisingSetCallback#onAdvertisingSetStopped} and its proxy
    private void onAdvertisingSetStoppedHandler(AdvertisingSet advertisingSet) {
        if (DBG) Slogf.d(TAG, "onAdvertisingSetStopped()");
        mAdvertisingHandler.advertisingStopped();
    }

    // For {@link AdvertisingSetCallback#onOwnAddressRead} and its proxy
    private void onOwnAddressReadHandler(int addressType, String address) {
        if (DBG) Slogf.d(TAG, "onOwnAddressRead Type= %d, Address= %s", addressType, address);
        mCallbacks.onRpaUpdated(mBluetoothAdapter.getRemoteDevice(address));
    }

    /**
     * A handler that synchronizes advertising events
     */
    // TODO (243161113): Clean this handler up to make it more clear and enable direct advertising
    // data changes without stopping
    private class AdvertisingHandler extends Handler {
        private static final int MSG_ADVERTISING_STOPPED = 0;
        private static final int MSG_START_ADVERTISING = 1;
        private static final int MSG_ADVERTISING_STARTED = 2;
        private static final int MSG_STOP_ADVERTISING = 3;
        private static final int MSG_TIMEOUT = 4;

        private static final int OPERATION_TIMEOUT_MS = 4000;

        private int mState = STATE_STOPPED;
        private final ArrayList<Message> mDeferredMessages = new ArrayList<Message>();

        private class AdvertisingRequest {
            public final byte[] mData;
            public final int mInterval;
            public final Callbacks mCallback;

            AdvertisingRequest(byte[] data, int interval, Callbacks callback) {
                mInterval = interval;
                mData = data;
                mCallback = callback;
            }
        }

        AdvertisingHandler() {
            super(CarServiceUtils.getHandlerThread(FastPairProvider.THREAD_NAME).getLooper());
        }

        public void startAdvertising(byte[] data, int interval, Callbacks callback) {
            if (DBG) Slogf.d(TAG, "HANDLER: startAdvertising(data=%s)", Arrays.toString(data));
            AdvertisingRequest request = new AdvertisingRequest(data, interval, callback);
            sendMessage(obtainMessage(MSG_START_ADVERTISING, request));
        }

        public void advertisingStarted() {
            if (DBG) Slogf.d(TAG, "HANDLER: advertisingStart()");
            sendMessage(obtainMessage(MSG_ADVERTISING_STARTED));
        }

        public void stopAdvertising() {
            if (DBG) Slogf.d(TAG, "HANDLER: stopAdvertising()");
            sendMessage(obtainMessage(MSG_STOP_ADVERTISING));
        }

        public void advertisingStopped() {
            if (DBG) Slogf.d(TAG, "HANDLER: advertisingStop()");
            sendMessage(obtainMessage(MSG_ADVERTISING_STOPPED));
        }

        private void queueOperationTimeout() {
            removeMessages(MSG_TIMEOUT);
            sendMessageDelayed(obtainMessage(MSG_TIMEOUT), OPERATION_TIMEOUT_MS);
        }

        @Override
        public void handleMessage(Message msg) {
            if (DBG) {
                Slogf.i(TAG, "HANDLER: Received message %s, state=%s", messageToString(msg.what),
                        stateToString(mState));
            }
            switch (msg.what) {
                case MSG_ADVERTISING_STOPPED:
                    removeMessages(MSG_TIMEOUT);
                    transitionTo(STATE_STOPPED);
                    processDeferredMessages();
                    break;

                case MSG_START_ADVERTISING:
                    if (mState == STATE_STARTED) {
                        break;
                    } else if (mState != STATE_STOPPED) {
                        deferMessage(msg);
                        return;
                    }
                    AdvertisingRequest request = (AdvertisingRequest) msg.obj;
                    if (startAdvertisingInternal(request.mData, request.mInterval,
                            request.mCallback)) {
                        transitionTo(STATE_STARTING);
                    }
                    queueOperationTimeout();
                    break;

                case MSG_ADVERTISING_STARTED:
                    removeMessages(MSG_TIMEOUT);
                    transitionTo(STATE_STARTED);
                    processDeferredMessages();
                    break;

                case MSG_STOP_ADVERTISING:
                    if (mState == STATE_STOPPED) {
                        break;
                    } else if (mState != STATE_STARTED) {
                        deferMessage(msg);
                        return;
                    }
                    stopAdvertisingInternal();
                    transitionTo(STATE_STOPPING);
                    queueOperationTimeout();
                    break;
                case MSG_TIMEOUT:
                    if (mState == STATE_STARTING) {
                        Slogf.w(TAG, "HANDLER: Timed out waiting for startAdvertising");
                        stopAdvertisingInternal();
                    } else if (mState == STATE_STOPPING) {
                        Slogf.w(TAG, "HANDLER: Timed out waiting for stopAdvertising");
                    } else {
                        Slogf.e(TAG, "HANDLER: Unexpected timeout in state %s",
                                stateToString(mState));
                    }
                    transitionTo(STATE_STOPPED);
                    processDeferredMessages();
                    break;

                default:
                    Slogf.e(TAG, "HANDLER: Unexpected message: %d", msg.what);
            }
        }

        private void transitionTo(int state) {
            if (DBG) Slogf.d(TAG, "HANDLER: %s -> %s", stateToString(mState), stateToString(state));
            mState = state;
        }

        private void deferMessage(Message message) {
            if (DBG) {
                Slogf.i(TAG, "HANDLER: Deferred message, message=%s",
                        messageToString(message.what));
            }

            Message copy = obtainMessage();
            copy.copyFrom(message);
            mDeferredMessages.add(copy);

            if (DBG) {
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                for (Message m : mDeferredMessages) {
                    sb.append(" ").append(messageToString(m.what));
                }
                sb.append(" ]");
                Slogf.d(TAG, "HANDLER: Deferred List: %s", sb.toString());
            }
        }

        private void processDeferredMessages() {
            if (DBG) {
                Slogf.d(TAG, "HANDLER: Process deferred Messages, size=%d",
                        mDeferredMessages.size());
            }
            for (int i = mDeferredMessages.size() - 1; i >= 0; i--) {
                Message message = mDeferredMessages.get(i);
                if (DBG) {
                    Slogf.i(TAG, "HANDLER: Adding deferred message to front, message=%s",
                            messageToString(message.what));
                }
                sendMessageAtFrontOfQueue(message);
            }
            mDeferredMessages.clear();
        }

        public int getState() {
            return mState;
        }

        private String messageToString(int message) {
            switch (message) {
                case MSG_ADVERTISING_STOPPED:
                    return "MSG_ADVERTISING_STOPPED";
                case MSG_START_ADVERTISING:
                    return "MSG_START_ADVERTISING";
                case MSG_ADVERTISING_STARTED:
                    return "MSG_ADVERTISING_STARTED";
                case MSG_STOP_ADVERTISING:
                    return "MSG_STOP_ADVERTISING";
                case MSG_TIMEOUT:
                    return "MSG_TIMEOUT";
                default:
                    return "Unknown";
            }
        }
    }

    private String stateToString(int state) {
        switch (state) {
            case STATE_STOPPED:
                return "STATE_STOPPED";
            case STATE_STARTING:
                return "STATE_STARTING";
            case STATE_STARTED:
                return "STATE_STARTED";
            case STATE_STOPPING:
                return "STATE_STOPPING";
            default:
                return "Unknown";
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("FastPairAdvertiser:");
        writer.increaseIndent();
        writer.println("AdvertisingState     : " + stateToString(getAdvertisingState()));
        if (isAdvertising()) {
            writer.println("Advertising Interval : " + mAdvertisingSetParameters.getInterval());
            writer.println("TX Power             : " + mTxPower + "/"
                    + mAdvertisingSetParameters.getTxPowerLevel());
            writer.println("Advertising Data     : " + mData);
        }
        writer.decreaseIndent();
    }
}
