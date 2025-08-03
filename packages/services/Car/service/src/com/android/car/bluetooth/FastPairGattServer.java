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
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.car.builtin.util.Slogf;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Base64;
import android.util.Log;

import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.SecretKeySpec;

/**
 * The FastPairGattServer is responsible for all 2 way communications with the Fast Pair Seeker.
 * It is running in the background over BLE whenever the Fast Pair Service is running, waiting for a
 * Seeker to connect, after which time it manages the authentication an performs the steps as
 * required by the Fast Pair Specification.
 */
public class FastPairGattServer {
    // Service ID assigned for FastPair.
    public static final ParcelUuid FAST_PAIR_SERVICE_UUID = ParcelUuid
            .fromString("0000FE2C-0000-1000-8000-00805f9b34fb");
    public static final ParcelUuid FAST_PAIR_MODEL_ID_UUID = ParcelUuid
            .fromString("FE2C1233-8366-4814-8EB0-01DE32100BEA");
    public static final ParcelUuid KEY_BASED_PAIRING_UUID = ParcelUuid
            .fromString("FE2C1234-8366-4814-8EB0-01DE32100BEA");
    public static final ParcelUuid PASSKEY_UUID = ParcelUuid
            .fromString("FE2C1235-8366-4814-8EB0-01DE32100BEA");
    public static final ParcelUuid ACCOUNT_KEY_UUID = ParcelUuid
            .fromString("FE2C1236-8366-4814-8EB0-01DE32100BEA");
    public static final ParcelUuid CLIENT_CHARACTERISTIC_CONFIG = ParcelUuid
            .fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final ParcelUuid DEVICE_NAME_CHARACTERISTIC_CONFIG = ParcelUuid
            .fromString("00002A00-0000-1000-8000-00805f9b34fb");
    private static final String TAG = CarLog.tagFor(FastPairGattServer.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    private static final int MAX_KEY_COUNT = 10;
    private static final int KEY_LIFESPAN_AWAIT_PAIRING = 60_000;
    // Spec *does* say indefinitely but not having a timeout is risky. This matches the BT stack's
    // internal pairing timeout
    private static final int KEY_LIFESPAN_PAIRING = 35_000;
    private static final int KEY_LIFESPAN_AWAIT_ACCOUNT_KEY = 10_000;
    private static final int INVALID = -1;

    private final boolean mAutomaticPasskeyConfirmation;
    private final byte[] mModelId;
    private final String mPrivateAntiSpoof;
    private final Context mContext;

    private final FastPairAccountKeyStorage mFastPairAccountKeyStorage;

    private BluetoothGattServer mBluetoothGattServer;
    private final BluetoothManager mBluetoothManager;
    private final BluetoothAdapter mBluetoothAdapter;
    private final Object mPasskeyLock = new Object();
    private int mSeekerPasskey = INVALID;
    private int mPairingPasskey = INVALID;
    private final DecryptionFailureCounter mFailureCounter = new DecryptionFailureCounter();
    private BluetoothGattService mFastPairService = new BluetoothGattService(
            FAST_PAIR_SERVICE_UUID.getUuid(), BluetoothGattService.SERVICE_TYPE_PRIMARY);
    private Callbacks mCallbacks;
    private SecretKeySpec mSharedSecretKey;
    private BluetoothDevice mLocalRpaDevice;
    private BluetoothDevice mRemotePairingDevice;
    private BluetoothDevice mRemoteGattDevice;

    interface Callbacks {
        /**
         * Notify the Provider of completion to a GATT session
         * @param successful
         */
        void onPairingCompleted(boolean successful);
    }

    private class DecryptionFailureCounter {
        public static final int FAILURE_LIMIT = 10;
        private static final int FAILURE_RESET_TIMEOUT = 300_000; // 5 minutes

        private int mCount = 0;

        private Runnable mResetRunnable = new Runnable() {
            @Override
            public void run() {
                Slogf.i(TAG, "Five minutes have expired. Reset failure count to 0");
                reset();
            }
        };

        public void increment() {
            if (hasExceededLimit()) {
                Slogf.w(TAG, "Failure count is already at the limit.");
                return;
            }

            mCount++;
            Slogf.i(TAG, "Failure count increased, failures=%d", mCount);
            if (hasExceededLimit()) {
                Slogf.w(TAG, "Failure count has reached 10, wait 5 minutes for more tries");
                mHandler.postDelayed(mResetRunnable, FAILURE_RESET_TIMEOUT);
            }
        }

        public void reset() {
            Slogf.i(TAG, "Reset failure count");
            mHandler.removeCallbacks(mResetRunnable);
            mCount = 0;
        }

        public boolean hasExceededLimit() {
            return mCount >= FAILURE_LIMIT;
        }

        @Override
        public String toString() {
            return String.valueOf(mCount);
        }
    }

    /**
     * Notify this FastPairGattServer of a new RPA from the FastPairAdvertiser
     */
    public void updateLocalRpa(BluetoothDevice device) {
        mLocalRpaDevice = device;
    }

    private Runnable mClearSharedSecretKey = new Runnable() {
        @Override
        public void run() {
            Slogf.w(TAG, "Shared secret key has expired. Clearing key material.");
            clearSharedSecretKey();
        }
    };

    private final Handler mHandler = new Handler(
            CarServiceUtils.getHandlerThread(FastPairProvider.THREAD_NAME).getLooper());
    private BluetoothGattCharacteristic mModelIdCharacteristic;
    private BluetoothGattCharacteristic mKeyBasedPairingCharacteristic;
    private BluetoothGattCharacteristic mPasskeyCharacteristic;
    private BluetoothGattCharacteristic mAccountKeyCharacteristic;
    private BluetoothGattCharacteristic mDeviceNameCharacteristic;

    /**
     * GATT server callbacks responsible for servicing read and write calls from the remote device
     */
    private BluetoothGattServerCallback mBluetoothGattServerCallback =
            new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (DBG) {
                Slogf.d(TAG, "onConnectionStateChange %d Device: %s", newState, device);
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                invalidatePairingPasskeys();
                clearSharedSecretKey();
                mRemoteGattDevice = null;
                mRemotePairingDevice = null;
                mCallbacks.onPairingCompleted(false);
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                mRemoteGattDevice = device;
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            if (DBG) {
                Slogf.d(TAG, "onCharacteristicReadRequest");
            }
            if (characteristic == mModelIdCharacteristic) {
                if (DBG) {
                    Slogf.d(TAG, "reading model ID");
                }
            }
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                    characteristic.getValue());
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                boolean responseNeeded,
                int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
                    responseNeeded, offset, value);
            if (DBG) {
                Slogf.d(TAG, "onWrite, uuid=%s, length=%d", characteristic.getUuid(),
                        (value != null ? value.length : -1));
            }

            if (characteristic == mKeyBasedPairingCharacteristic) {
                if (DBG) {
                    Slogf.d(TAG, "onWriteKeyBasedPairingCharacteristic");
                }
                byte[] response = processKeyBasedPairing(value);
                if (response == null) {
                    Slogf.w(TAG, "Could not process key based pairing request. Ignoring.");
                    mBluetoothGattServer
                        .sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                                null);
                    return;
                }
                mKeyBasedPairingCharacteristic.setValue(response);
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                        offset, response);
                mBluetoothGattServer
                        .notifyCharacteristicChanged(device, mDeviceNameCharacteristic, false);
                mBluetoothGattServer
                        .notifyCharacteristicChanged(device, mKeyBasedPairingCharacteristic, false);

            } else if (characteristic == mPasskeyCharacteristic) {
                if (DBG) {
                    Slogf.d(TAG, "onWritePasskey %s", characteristic.getUuid());
                }
                processPairingKey(value);
                mBluetoothGattServer
                        .sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
            } else if (characteristic == mAccountKeyCharacteristic) {
                if (DBG) {
                    Slogf.d(TAG, "onWriteAccountKeyCharacteristic");
                }
                processAccountKey(value);

                mBluetoothGattServer
                        .sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
            } else {
                Slogf.w(TAG, "onWriteOther %s", characteristic.getUuid());
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded,
                int offset, byte[] value) {
            if (DBG) {
                Slogf.d(TAG, "onDescriptorWriteRequest");
            }
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                    descriptor.getValue());
        }
    };

    /**
     *  Receive incoming pairing requests such that we can confirm Keys match.
     */
    BroadcastReceiver mPairingAttemptsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DBG) {
                Slogf.d(TAG, action);
            }

            switch (action) {
                case BluetoothDevice.ACTION_PAIRING_REQUEST:
                    mRemotePairingDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    synchronized (mPasskeyLock) {
                        mPairingPasskey =
                                intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, INVALID);
                        if (DBG) {
                            Slogf.d(TAG,
                                    "Pairing Request - device=%s, pin_code=%s, seeker_passkey=%s",
                                    mRemotePairingDevice, mPairingPasskey, mSeekerPasskey);
                        }

                        if (!isConnected()) {
                            Slogf.d(TAG, "Received pairing request outside of a Fast Pair Session");
                            break;
                        }

                        if (mPairingPasskey == INVALID) {
                            Slogf.w(TAG, "Received an invalid pin_code from the BT stack");
                            break;
                        }

                        // The Seeker registers for passkey characteristic notifications after
                        // pairing begins on incoming pairings, so we hold our passkey write until
                        // we receive their passkey so we can be sure they have registered for
                        // notifications and will receive our passkey.
                        if (mSeekerPasskey != INVALID) {
                            sendPairingResponse(mPairingPasskey);
                        } else {
                            Slogf.w(TAG, "Got code from BT stack before getting Seeker's passkey");
                        }

                        if (mSeekerPasskey != INVALID && mPairingPasskey != INVALID) {
                            comparePasskeys();
                        }
                    }
                    // TODO (243578517): Abort the broadcast when everything is valid and we support
                    // automatic acceptance.
                    break;

                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, INVALID);
                    int previousState =
                            intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, INVALID);

                    if (DBG) {
                        Slogf.d(TAG, "Bond State Change - device=%s, old_state=%s, new_state=%s",
                                device, BluetoothUtils.getBondStateName(previousState),
                                BluetoothUtils.getBondStateName(state));
                    }

                    // If the bond state has changed for the device we're current fast pairing with
                    // and it is now bonded, then pairing is complete. Reset the failure count to 0.
                    // Await a potential account key.
                    if (device != null && device.equals(mRemotePairingDevice)) {
                        if (state == BluetoothDevice.BOND_BONDED) {
                            if (DBG) {
                                Slogf.d(TAG, "Pairing complete, device=%s", mRemotePairingDevice);
                            }
                            setSharedSecretKeyLifespan(KEY_LIFESPAN_AWAIT_ACCOUNT_KEY);
                            mRemotePairingDevice = null;
                            invalidatePairingPasskeys();
                            mFailureCounter.reset();
                        } else if (state == BluetoothDevice.BOND_NONE) {
                            if (DBG) {
                                Slogf.d(TAG, "Pairing attempt failed, device=%s",
                                        mRemotePairingDevice);
                            }
                            mRemotePairingDevice = null;
                            invalidatePairingPasskeys();
                        }
                    }
                    break;

                case BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED:
                    String name = intent.getStringExtra(BluetoothAdapter.EXTRA_LOCAL_NAME);
                    updateLocalName(name);
                    break;

                default:
                    Slogf.w(TAG, "Unknown action. Skipped");
                    break;
            }
        }
    };

    /**
     * FastPairGattServer
     * @param context user specific context on which to make callse
     * @param modelId assigned Fast Pair Model ID
     * @param antiSpoof assigned Fast Pair private Anti Spoof key
     * @param callbacks callbacks used to report back current pairing status
     * @param automaticAcceptance automatically accept an incoming pairing request that has been
     *     authenticated through the Fast Pair protocol without further user interaction.
     */
    FastPairGattServer(Context context, int modelId, String antiSpoof,
            Callbacks callbacks, boolean automaticAcceptance,
            FastPairAccountKeyStorage fastPairAccountKeyStorage) {
        mContext = Objects.requireNonNull(context);
        mFastPairAccountKeyStorage = Objects.requireNonNull(fastPairAccountKeyStorage);
        mCallbacks = Objects.requireNonNull(callbacks);
        mPrivateAntiSpoof = antiSpoof;
        mAutomaticPasskeyConfirmation = automaticAcceptance;
        mBluetoothManager = context.getSystemService(BluetoothManager.class);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        ByteBuffer modelIdBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(
                modelId);
        mModelId = Arrays.copyOfRange(modelIdBytes.array(), 0, 3);
        setup();
    }

    /**
     * Initialize all of the GATT characteristics with appropriate default values and the required
     * configurations.
     */
    private void setup() {
        mModelIdCharacteristic = new BluetoothGattCharacteristic(FAST_PAIR_MODEL_ID_UUID.getUuid(),
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        mModelIdCharacteristic.setValue(mModelId);
        mFastPairService.addCharacteristic(mModelIdCharacteristic);

        mKeyBasedPairingCharacteristic =
                new BluetoothGattCharacteristic(KEY_BASED_PAIRING_UUID.getUuid(),
                        BluetoothGattCharacteristic.PROPERTY_WRITE
                                | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_WRITE);
        mKeyBasedPairingCharacteristic.setValue(mModelId);
        mKeyBasedPairingCharacteristic.addDescriptor(new BluetoothGattDescriptor(
                CLIENT_CHARACTERISTIC_CONFIG.getUuid(),
                BluetoothGattDescriptor.PERMISSION_READ
                        | BluetoothGattDescriptor.PERMISSION_WRITE));
        mFastPairService.addCharacteristic(mKeyBasedPairingCharacteristic);

        mPasskeyCharacteristic =
                new BluetoothGattCharacteristic(PASSKEY_UUID.getUuid(),
                        BluetoothGattCharacteristic.PROPERTY_WRITE
                                | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_WRITE);
        mPasskeyCharacteristic.setValue(mModelId);
        mPasskeyCharacteristic.addDescriptor(new BluetoothGattDescriptor(
                CLIENT_CHARACTERISTIC_CONFIG.getUuid(),
                BluetoothGattDescriptor.PERMISSION_READ
                        | BluetoothGattDescriptor.PERMISSION_WRITE));

        mFastPairService.addCharacteristic(mPasskeyCharacteristic);

        mAccountKeyCharacteristic =
                new BluetoothGattCharacteristic(ACCOUNT_KEY_UUID.getUuid(),
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                        BluetoothGattCharacteristic.PERMISSION_WRITE);
        mFastPairService.addCharacteristic(mAccountKeyCharacteristic);

        mDeviceNameCharacteristic =
                new BluetoothGattCharacteristic(DEVICE_NAME_CHARACTERISTIC_CONFIG.getUuid(),
                        BluetoothGattCharacteristic.PROPERTY_READ,
                        BluetoothGattCharacteristic.PERMISSION_READ);
        String name = mBluetoothAdapter.getName();
        if (name == null) {
            name = "";
        }
        mDeviceNameCharacteristic.setValue(name);
        mFastPairService.addCharacteristic(mDeviceNameCharacteristic);
    }

    void updateLocalName(String name) {
        Slogf.d(TAG, "Device name changed to '%s'", name);
        if (name != null) {
            mDeviceNameCharacteristic.setValue(name);
        }
    }

    /**
     * Start the FastPairGattServer
     *
     * This makes the underlying service and characteristics available and registers us for events.
     */
    public synchronized boolean start() {
        if (DBG) {
            Slogf.d(TAG, "start()");
        }

        if (isStarted()) {
            Slogf.w(TAG, "GATT service already started");
            return true;
        }

        mBluetoothGattServer = mBluetoothManager
                .openGattServer(mContext, mBluetoothGattServerCallback);

        if (mBluetoothGattServer == null) {
            Slogf.e(TAG, "Start failed, could not get a GATT server.");
            return false;
        }

        // Setup filter to receive pairing attempts and passkey. Make this a high priority broadcast
        // receiver so others can't intercept it before we can handle it.
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(mPairingAttemptsReceiver, filter);

        mBluetoothGattServer.addService(mFastPairService);
        return true;
    }

    /**
     * Stop the FastPairGattServer
     *
     * This removes our underlying service and clears our state.
     */
    public synchronized boolean stop() {
        if (DBG) {
            Slogf.d(TAG, "stop()");
        }

        if (!isStarted()) {
            Slogf.w(TAG, "GATT service already stopped");
            return true;
        }

        mContext.unregisterReceiver(mPairingAttemptsReceiver);

        if (isConnected()) {
            mBluetoothGattServer.cancelConnection(mRemoteGattDevice);
            mRemoteGattDevice = null;
            mCallbacks.onPairingCompleted(false);
        }

        invalidatePairingPasskeys();
        clearSharedSecretKey();

        mBluetoothGattServer.removeService(mFastPairService);
        mBluetoothGattServer.close();
        mBluetoothGattServer = null;
        return true;
    }

    /**
     * Check if this service is started
     */
    public boolean isStarted() {
        return mBluetoothGattServer != null;
    }

    /**
     * Check if a client is connected to this GATT server
     * @return true if connected;
     */
    public boolean isConnected() {
        if (DBG) {
            Slogf.d(TAG, "isConnected() -> %s", (mRemoteGattDevice != null));
        }
        return (mRemoteGattDevice != null);
    }

    private void setSharedSecretKey(SecretKeySpec key, int lifespan) {
        if (key == null) {
            Slogf.w(TAG, "Cannot set a null shared secret.");
            return;
        }
        Slogf.i(TAG, "Shared secret key set, key=%s lifespan=%d", key, lifespan);
        mSharedSecretKey = key;
        setSharedSecretKeyLifespan(lifespan);
    }

    private void setSharedSecretKeyLifespan(int lifespan) {
        if (mSharedSecretKey == null) {
            Slogf.w(TAG, "Ignoring lifespan on null key");
            return;
        }
        if (DBG) {
            Slogf.d(TAG, "Update key lifespan to %d", lifespan);
        }
        mHandler.removeCallbacks(mClearSharedSecretKey);
        if (lifespan > 0) {
            mHandler.postDelayed(mClearSharedSecretKey, lifespan);
        }
    }

    private void clearSharedSecretKey() {
        Slogf.i(TAG, "Shared secret key has been cleared");
        mHandler.removeCallbacks(mClearSharedSecretKey);
        mSharedSecretKey = null;
    }

    private void invalidatePairingPasskeys() {
        synchronized (mPasskeyLock) {
            mPairingPasskey = INVALID;
            mSeekerPasskey = INVALID;
        }
    }

    public boolean isFastPairSessionActive() {
        return mSharedSecretKey != null;
    }

    /**
     * Attempt to encrypt the provided data with the provided key
     *
     * @param data data to be encrypted
     * @param secretKeySpec key to ecrypt the data with
     * @return encrypted data upon success; null otherwise
     */
    private byte[] encrypt(byte[] data, SecretKeySpec secretKeySpec) {
        if (secretKeySpec == null) {
            Slogf.e(TAG, "Encryption failed: no key");
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            return cipher.doFinal(data);

        } catch (Exception e) {
            Slogf.e(TAG, "Encryption failed: %s", e);
        }
        return null;
    }
    /**
     * Attempt to decrypt the provided data with the provided key
     *
     * @param encryptedData data to be decrypted
     * @param secretKeySpec key to decrypt the data with
     * @return decrypted data upon success; null otherwise
     */
    private byte[] decrypt(byte[] encryptedData, SecretKeySpec secretKeySpec) {
        if (secretKeySpec == null) {
            Slogf.e(TAG, "Decryption failed: no key");
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            return cipher.doFinal(encryptedData);

        } catch (Exception e) {
            Slogf.e(TAG, "Decryption Failed: %s", e);
        }
        return null;
    }

    /**
     * Determine if this pairing request is based on the anti-spoof keys associated with the model
     * id or stored account keys.
     *
     * @param accountKey
     * @return
     */
    private byte[] processKeyBasedPairing(byte[] pairingRequest) {
        if (mFailureCounter.hasExceededLimit()) {
            Slogf.w(TAG, "Failure count has exceeded 10. Ignoring Key-Based Pairing requests");
            return null;
        }

        if (pairingRequest == null) {
            Slogf.w(TAG, "Received a null pairing request");
            mFailureCounter.increment();
            clearSharedSecretKey();
            return null;
        }

        List<SecretKeySpec> possibleKeys = new ArrayList<>();
        if (pairingRequest.length == 80) {
            if (DBG) {
                Slogf.d(TAG, "Use Anti-spoofing key");
            }
            // if the pairingRequest is 80 bytes long try the anit-spoof key
            final byte[] remotePublicKey = Arrays.copyOfRange(pairingRequest, 16, 80);

            possibleKeys
                    .add(calculateAntiSpoofing(Base64.decode(mPrivateAntiSpoof, 0), remotePublicKey)
                            .getKeySpec());
        } else if (pairingRequest.length == 16) {
            if (DBG) {
                Slogf.d(TAG, "Use stored account keys");
            }
            // otherwise the pairing request is the encrypted request, try all the stored account
            // keys
            List<AccountKey> storedAccountKeys = mFastPairAccountKeyStorage.getAllAccountKeys();
            for (AccountKey key : storedAccountKeys) {
                possibleKeys.add(new SecretKeySpec(key.toBytes(), "AES"));
            }
        } else {
            Slogf.w(TAG, "Received key based pairing request of invalid length %d",
                    pairingRequest.length);
            mFailureCounter.increment();
            clearSharedSecretKey();
            return null;
        }

        byte[] encryptedRequest = Arrays.copyOfRange(pairingRequest, 0, 16);
        if (DBG) {
            Slogf.d(TAG, "Checking %d Keys", possibleKeys.size());
        }
        // check all the keys for a valid pairing request
        for (SecretKeySpec key : possibleKeys) {
            if (DBG) {
                Slogf.d(TAG, "Checking possible key");
            }
            if (validateRequestAgainstKey(encryptedRequest, key)) {
                // If the key was able to decrypt the request and the addresses match then set it as
                // the shared secret and set a lifespan timeout
                setSharedSecretKey(key, KEY_LIFESPAN_AWAIT_PAIRING);

                // Use the key to craft encrypted response to the seeker with the local public
                // address and salt. If encryption goes wrong, move on to the next key
                String localAddress = mBluetoothAdapter.getAddress();
                byte[] localAddressBytes = BluetoothUtils.getBytesFromAddress(localAddress);
                byte[] rawResponse = new byte[16];
                new Random().nextBytes(rawResponse);
                rawResponse[0] = 0x01;
                System.arraycopy(localAddressBytes, 0, rawResponse, 1, 6);
                byte[] response = encrypt(rawResponse, key);
                if (response == null) {
                    clearSharedSecretKey();
                    return null;
                }
                return response;
            }
        }
        Slogf.w(TAG, "No matching key found");
        mFailureCounter.increment();
        clearSharedSecretKey();
        return null;
    }

    /**
     * New pairings based upon model ID requires the Fast Pair provider to authenticate to that the
     * seeker it is in possession of the private key associated with the model ID advertised. This
     * is accomplished via Eliptic-curve Diffie-Hellman
     *
     * @param localPrivateKey
     * @param remotePublicKey
     * @return
     */
    private AccountKey calculateAntiSpoofing(byte[] localPrivateKey, byte[] remotePublicKey) {
        try {
            if (DBG) {
                Slogf.d(TAG, "Calculating secret key from remote public key");
            }
            // Initialize the EC key generator
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            ECParameterSpec ecParameterSpec = ((ECPublicKey) kpg.generateKeyPair().getPublic())
                    .getParams();
            // Use the private anti-spoofing key
            ECPrivateKeySpec ecPrivateKeySpec = new ECPrivateKeySpec(
                    new BigInteger(1, localPrivateKey),
                    ecParameterSpec);
            // Calculate the public point utilizing the data received from the remote device
            ECPoint publicPoint = new ECPoint(new BigInteger(1, Arrays.copyOf(remotePublicKey, 32)),
                    new BigInteger(1, Arrays.copyOfRange(remotePublicKey, 32, 64)));
            ECPublicKeySpec ecPublicKeySpec = new ECPublicKeySpec(publicPoint, ecParameterSpec);
            PrivateKey privateKey = keyFactory.generatePrivate(ecPrivateKeySpec);
            PublicKey publicKey = keyFactory.generatePublic(ecPublicKeySpec);

            // Generate a shared secret
            KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
            keyAgreement.init(privateKey);
            keyAgreement.doPhase(publicKey, true);
            byte[] sharedSecret = keyAgreement.generateSecret();

            // Use the first 16 bytes of a hash of the shared secret as the session key
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(sharedSecret);

            byte[] AESAntiSpoofingKey = Arrays.copyOf(digest, 16);
            if (DBG) {
                Slogf.d(TAG, "Key calculated");
            }
            return new AccountKey(AESAntiSpoofingKey);
        } catch (Exception e) {
            Slogf.w(TAG, "Error calculating anti-spoofing key: %s", e);
            return null;
        }
    }

    /**
     * Check if the given key can be used to decrypt the pairing request and prove the request is
     * valid.
     *
     * A request is valid if its decrypted value is of type 0x00 or 0x10 and it contains either the
     * seekers public or current BLE address. If a key successfully decrypts and validates a request
     * then that is the key we should use as our shared secret key.
     *
     * @param encryptedRequest the request to decrypt and validate
     * @param secretKeySpec the key to use while attempting to decrypt the request
     * @return true if the key matches, false otherwise
     */
    private boolean validateRequestAgainstKey(byte[] encryptedRequest,
            SecretKeySpec secretKeySpec) {
        // Decrypt the request
        byte[] decryptedRequest = decrypt(encryptedRequest, secretKeySpec);
        if (decryptedRequest == null) {
            return false;
        }

        if (DBG) {
            StringBuilder sb = new StringBuilder();
            for (byte b : decryptedRequest) {
                sb.append(String.format("%02X ", b));
            }
            Slogf.d(TAG, "Decrypted Request=[ %s]", sb.toString());
        }
        // Check that the request is either a Key-based Pairing Request or an Action Request
        if (decryptedRequest[0] == 0x00 || decryptedRequest[0] == 0x10) {
            String localAddress = mBluetoothAdapter.getAddress();
            byte[] localAddressBytes = BluetoothUtils.getBytesFromAddress(localAddress);
            // Extract the remote address bytes from the message
            byte[] remoteAddressBytes = Arrays.copyOfRange(decryptedRequest, 2, 8);
            BluetoothDevice localDevice = mBluetoothAdapter.getRemoteDevice(localAddress);
            BluetoothDevice reportedDevice = mBluetoothAdapter.getRemoteDevice(remoteAddressBytes);
            if (DBG) {
                Slogf.d(TAG, "rpa=%s, public=%s, reported=%s", mLocalRpaDevice, localAddress,
                        reportedDevice);
            }
            if (mLocalRpaDevice == null) {
                Slogf.w(TAG, "Cannot get own address");
            }
            // Test that the received device address matches this devices address
            if (reportedDevice.equals(localDevice) || reportedDevice.equals(mLocalRpaDevice)) {
                if (DBG) {
                    Slogf.d(TAG, "SecretKey Validated");
                }
                return encryptedRequest != null;
            }
        }
        return false;
    }

    /**
     * Extract the 6 digit Bluetooth Simple Secure Passkey from the received message and confirm
     * it matches the key received through the Bluetooth pairing procedure.
     *
     * If the passkeys match and automatic passkey confirmation is enabled, approve of the pairing.
     * If the passkeys do not match reject the pairing and invalidate our key material.
     *
     * @param pairingKey
     * @return true if the procedure completed, although pairing may not have been approved
     */
    private boolean processPairingKey(byte[] pairingKey) {
        if (pairingKey == null || pairingKey.length != 16) {
            clearSharedSecretKey();
            return false;
        }

        byte[] decryptedRequest = decrypt(pairingKey, mSharedSecretKey);
        if (decryptedRequest == null) {
            clearSharedSecretKey();
            return false;
        }
        synchronized (mPasskeyLock) {
            mSeekerPasskey = Byte.toUnsignedInt(decryptedRequest[1]) * 65536
                    + Byte.toUnsignedInt(decryptedRequest[2]) * 256
                    + Byte.toUnsignedInt(decryptedRequest[3]);

            if (DBG) {
                Slogf.d(TAG, "Received passkey request, type=%s, passkey=%d, our_passkey=%d",
                        decryptedRequest[0], mSeekerPasskey, mPairingPasskey);
            }

            // The Seeker registers for passkey characteristic notifications after pairing begins
            // on incoming pairings, so we hold our passkey write until we receive their passkey so
            // we can be sure they have registered for notifications and will receive our passkey.
            if (mPairingPasskey != INVALID) {
                sendPairingResponse(mPairingPasskey);
            } else {
                if (DBG) {
                    Slogf.d(TAG, "Got Seeker's passkey before receiving pin code from BT stack");
                }
            }

            if (mSeekerPasskey != INVALID && mPairingPasskey != INVALID) {
                comparePasskeys();
            }
        }

        return true;
    }

    /**
     * Compares the BT Stack reported passkey to the Fast Pair Seeker reported passkey.
     */
    private void comparePasskeys() {
        synchronized (mPasskeyLock) {
            if (mPairingPasskey == INVALID || mSeekerPasskey == INVALID) {
                Slogf.w(TAG, "Mising passkey to compare, bt=%s, seeker=%s", mPairingPasskey,
                        mSeekerPasskey);
                return;
            }
            if (mPairingPasskey == mSeekerPasskey) {
                if (DBG) {
                    Slogf.d(TAG, "Passkeys match, auto_accept=%s", mAutomaticPasskeyConfirmation);
                }
                if (mAutomaticPasskeyConfirmation) {
                    mRemotePairingDevice.setPairingConfirmation(true);
                }
            } else {
                Slogf.w(TAG, "Passkeys don't match, rejecting");
                mRemotePairingDevice.setPairingConfirmation(false);
                clearSharedSecretKey();
            }
        }
    }

    /**
     * Send the seeker the pin code we received so they can validate it. Encrypt it with our shared
     * secret.
     *
     * @param passkey the key-based pairing passkey, as described by the core BT specification
     */
    private void sendPairingResponse(int passkey) {
        if (!isConnected()) return;
        if (DBG) {
            Slogf.d(TAG, "sendPairingResponse %d", passkey);
        }

        // Once pairing begins, we can hold on to the shared secret key until pairing
        // completes
        setSharedSecretKeyLifespan(KEY_LIFESPAN_PAIRING);

        // Send an encrypted response to the seeker with the Bluetooth passkey as required
        byte[] decryptedResponse = new byte[16];
        new Random().nextBytes(decryptedResponse);
        ByteBuffer pairingPasskeyBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(
                passkey);
        decryptedResponse[0] = 0x3;
        decryptedResponse[1] = pairingPasskeyBytes.get(1);
        decryptedResponse[2] = pairingPasskeyBytes.get(2);
        decryptedResponse[3] = pairingPasskeyBytes.get(3);

        byte[] response = encrypt(decryptedResponse, mSharedSecretKey);
        if (response == null) {
            clearSharedSecretKey();
            return;
        }
        mPasskeyCharacteristic.setValue(response);
        mBluetoothGattServer
                .notifyCharacteristicChanged(mRemoteGattDevice, mPasskeyCharacteristic, false);
    }

    /**
     * The final step of the Fast Pair procedure involves receiving an account key from the
     * Fast Pair seeker, authenticating it, and then storing it for future use. Only one attempt
     * at writing this key is allowed by the spec. Discard the shared secret after this one attempt.
     *
     * @param accountKey the account key, encrypted with our sharded secret
     */
    private void processAccountKey(byte[] accountKey) {
        if (accountKey == null || accountKey.length != 16) {
            clearSharedSecretKey();
            return;
        }

        byte[] decodedAccountKey = decrypt(accountKey, mSharedSecretKey);
        if (decodedAccountKey != null && decodedAccountKey[0] == 0x04) {
            AccountKey receivedKey = new AccountKey(decodedAccountKey);
            if (DBG) {
                Slogf.d(TAG, "Received Account Key, key=%s", receivedKey);
            }
            mFastPairAccountKeyStorage.add(receivedKey);
        } else {
            if (DBG) {
                Slogf.d(TAG, "Received invalid Account Key");
            }
        }

        // Always clear the shared secret key following any attempt to write an account key
        clearSharedSecretKey();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        writer.println("FastPairGattServer:");
        writer.increaseIndent();
        writer.println("Started                       : " + isStarted());
        writer.println("Active                        : " + isFastPairSessionActive());
        writer.println("Currently connected to        : " + mRemoteGattDevice);
        writer.println("Failure counter              : " + mFailureCounter);
        writer.decreaseIndent();
    }
}
