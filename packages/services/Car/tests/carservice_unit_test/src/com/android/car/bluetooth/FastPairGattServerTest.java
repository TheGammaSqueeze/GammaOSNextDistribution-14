/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.ParcelUuid;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.RequiresDevice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Unit tests for {@link FastPairGattServer}
 *
 * Run: atest FastPairGattServerTest
 */
@RequiresDevice
@RunWith(MockitoJUnitRunner.class)
public class FastPairGattServerTest {

    static final ParcelUuid FAST_PAIR_SERVICE_UUID = ParcelUuid
            .fromString("0000FE2C-0000-1000-8000-00805f9b34fb");
    static final ParcelUuid FAST_PAIR_MODEL_ID_UUID = ParcelUuid
            .fromString("FE2C1233-8366-4814-8EB0-01DE32100BEA");
    static final ParcelUuid KEY_BASED_PAIRING_UUID = ParcelUuid
            .fromString("FE2C1234-8366-4814-8EB0-01DE32100BEA");
    static final ParcelUuid PASSKEY_UUID = ParcelUuid
            .fromString("FE2C1235-8366-4814-8EB0-01DE32100BEA");
    static final ParcelUuid ACCOUNT_KEY_UUID = ParcelUuid
            .fromString("FE2C1236-8366-4814-8EB0-01DE32100BEA");
    static final ParcelUuid CLIENT_CHARACTERISTIC_CONFIG = ParcelUuid
            .fromString("00002902-0000-1000-8000-00805f9b34fb");
    static final ParcelUuid DEVICE_NAME_CHARACTERISTIC_CONFIG = ParcelUuid
            .fromString("00002A00-0000-1000-8000-00805f9b34fb");

    // Model ID Configuration
    static final int TEST_MODEL_ID = 4386;
    static final byte[] TEST_MODEL_ID_BYTES = {0x22, 0x11, 0x00};

    // Public/Private Key Pair Configuration and Expected Generated Keys
    static final byte[] TEST_PUBLIC_KEY_A = {0x36, (byte) 0xAC, 0x68, 0x2C, 0x50, (byte) 0x82, 0x15,
            0x66, (byte) 0x8F, (byte) 0xBE, (byte) 0xFE, 0x24, 0x7D, 0x01, (byte) 0xD5, (byte) 0xEB,
            (byte) 0x96, (byte) 0xE6, 0x31, (byte) 0x8E, (byte) 0x85, 0x5B, 0x2D, 0x64, (byte) 0xB5,
            0x19, 0x5D, 0x38, (byte) 0xEE, 0x7E, 0x37, (byte) 0xBE, 0x18, 0x38, (byte) 0xC0,
            (byte) 0xB9, 0x48, (byte) 0xC3, (byte) 0xF7, 0x55, 0x20, (byte) 0xE0, 0x7E, 0x70,
            (byte) 0xF0, 0x72, (byte) 0x91, 0x41, (byte) 0x9A, (byte) 0xCE, 0x2D, 0x28, 0x14, 0x3C,
            0x5A, (byte) 0xDB, 0x2D, (byte) 0xBD, (byte) 0x98, (byte) 0xEE, 0x3C, (byte) 0x8E, 0x4F,
            (byte) 0xBF};
    static final byte[] TEST_PRIVATE_KEY_B = {0x02, (byte) 0xB4, 0x37, (byte) 0xB0, (byte) 0xED,
            (byte) 0xD6, (byte) 0xBB, (byte) 0xD4, 0x29, 0x06, 0x4A, 0x4E, 0x52, (byte) 0x9F,
            (byte) 0xCB, (byte) 0xF1, (byte) 0xC4, (byte) 0x8D, 0x0D, 0x62, 0x49, 0x24, (byte) 0xD5,
            (byte) 0x92, 0x27, 0x4B, 0x7E, (byte) 0xD8, 0x11, (byte) 0x93, (byte) 0xD7, 0x63
    };
    static final String TEST_PRIVATE_KEY_B_BASE64 = Base64.getEncoder()
            .encodeToString(TEST_PRIVATE_KEY_B);
    static final byte[] TEST_GENERATED_KEY = {(byte) 0xB0, 0x7F, 0x1F, 0x17, (byte) 0xC2, 0x36,
            (byte) 0xCB, (byte) 0xD3, 0x35, 0x23, (byte) 0xC5, 0x15, (byte) 0xF3, 0x50, (byte) 0xAE,
            0x57};
    static final byte[] TEST_WRONG_GENERATED_KEY = {(byte) 0x00, 0x7F, 0x1F, 0x17, (byte) 0x00,
            0x36, (byte) 0xCB, (byte) 0xD3, 0x35, 0x23, (byte) 0xC5, 0x15, (byte) 0xF3, 0x50,
            (byte) 0xAE, 0x57};
    static final byte[] TEST_SHARED_SECRET = {(byte) 0xA0, (byte) 0xBA, (byte) 0xF0, (byte) 0xBB,
            (byte) 0x95, 0x1F, (byte) 0xF7, (byte) 0xB6, (byte) 0xCF, 0x5E, 0x3F, 0x45, 0x61,
            (byte) 0xC3, 0x32, 0x1D};

    static final String TEST_DEVICE_ADDRESS_STRING = "00:11:22:33:FF:EE";
    static final byte[] TEST_DEVICE_ADDRESS_BYTES =
            new byte[]{0x00, 0x11, 0x22, 0x33, (byte) 0xFF, (byte) 0xEE};
    static final String TEST_RPA_STRING = "11:22:33:44:55:66";
    static final byte[] TEST_RPA_BYTES = new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66};
    static final String TEST_DEVICE_NAME = "name";
    static final String TEST_DEVICE_NAME_2 = "name2";

    static final String TEST_REMOTE_ADDRESS_STRING = "66:77:88:99:aa:bb";
    static final byte[] TEST_REMOTE_ADDRESS_BYTES =
            {0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xAA, (byte) 0xBB};

    static final byte KEY_BASED_PAIRING_REQUEST_MSG_TYPE = (byte) 0x00;
    static final byte ACTION_REQUEST_MSG_TYPE =  (byte) 0x10;
    static final byte KEY_BASED_PAIRING_RESPONSE_MSG_TYPE = (byte) 0x01;
    static final byte[] TEST_SALT_8 = new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77};

    static final byte PASSKEY_REQUEST_SEEKER = (byte) 0x02;
    static final byte PASSKEY_REQUEST_PROVIDER = (byte) 0x03;
    static final int TEST_PAIRING_KEY = 66051;
    static final byte[] TEST_PAIRING_KEY_BYTES = {0x01, 0x02, 0x03};
    static final int TEST_WRONG_PAIRING_KEY = 263430;
    static final byte[] TEST_WRONG_PAIRING_KEY_BYTES = {0x04, 0x05, 0x06};
    static final byte[] TEST_SALT_12 =
            new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x00, 0x11, 0x22, 0x33};

    static final byte[] TEST_INVALID_ACCOUNT_KEY = new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66,
            0x77, (byte) 0x88, (byte) 0x99, 0x00, (byte) 0xAA, (byte) 0xBB, (byte) 0xCC,
            (byte) 0xDD, (byte) 0xEE, (byte) 0xFF};
    static final byte[] TEST_VALID_ACCOUNT_KEY = new byte[]{0x04, 0x11, 0x22, 0x22, 0x33, 0x33,
            0x44, 0x44, 0x55, 0x55, 0x66, 0x66, 0x77, 0x77, (byte) 0x88, (byte) 0x88};

    static final byte[] TEST_REQUEST_SHORT = new byte[]{0x11, 0x22, 0x33, 0x44};

    static final int ASYNC_CALL_TIMEOUT_MILLIS = 200;

    // GATT Service code requires BluetoothDevice.equals() calls, which cannot be mocked. We'll use
    // real device objects from the device under test's BluetoothAdapter where equals() would be
    // required instead
    Context mTargetContext;
    BluetoothAdapter mTargetBluetoothAdapter;

    @Mock Context mMockContext;
    @Mock BluetoothManager mMockBluetoothManager;
    @Mock BluetoothAdapter mMockBluetoothAdapter;
    @Mock BluetoothDevice mMockBluetoothDevice;
    @Mock BluetoothGattServer mMockBluetoothGattServer;
    @Mock BluetoothGattDescriptor mMockGattDescriptor;
    @Mock FastPairGattServer.Callbacks mMockFastPairCallbacks;
    @Mock FastPairAccountKeyStorage mMockFastPairAccountKeyStorage;

    @Captor ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor;
    BroadcastReceiver mReceiver;

    @Captor ArgumentCaptor<BluetoothGattServerCallback> mBluetoothGattServerCallbackCaptor;
    BluetoothGattServerCallback mBluetoothGattServerCallback;

    BluetoothGattService mBluetoothGattService;

    @Captor ArgumentCaptor<byte[]> mBytesCaptor;

    FastPairGattServer mTestGattServer;

    BluetoothGattCharacteristic mModelIdCharacteristic;
    BluetoothGattCharacteristic mKeyBasedPairingCharacteristic;
    BluetoothGattCharacteristic mPasskeyCharacteristic;
    BluetoothGattCharacteristic mAccountKeyCharacteristic;
    BluetoothGattCharacteristic mDeviceNameCharacteristic;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mTargetContext = InstrumentationRegistry.getTargetContext();
        BluetoothManager btManager = mTargetContext.getSystemService(BluetoothManager.class);
        mTargetBluetoothAdapter = btManager.getAdapter();

        when(mMockContext.getSystemService(BluetoothManager.class))
                .thenReturn(mMockBluetoothManager);

        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothManager.openGattServer(any(), any()))
                .thenReturn(mMockBluetoothGattServer);

        when(mMockBluetoothAdapter.getName()).thenReturn(TEST_DEVICE_NAME);
        when(mMockBluetoothAdapter.getAddress()).thenReturn(TEST_DEVICE_ADDRESS_STRING);

        doAnswer(invocation -> {
            String address = (String) invocation.getArguments()[0];
            return mTargetBluetoothAdapter.getRemoteDevice(address);
        }).when(mMockBluetoothAdapter).getRemoteDevice(any(String.class));

        doAnswer(invocation -> {
            byte[] address = (byte[]) invocation.getArguments()[0];
            if (address == null || address.length != 6) {
                return null;
            }
            String addrStr = String.format(Locale.US, "%02X:%02X:%02X:%02X:%02X:%02X", address[0],
                    address[1], address[2], address[3], address[4], address[5]);
            return mTargetBluetoothAdapter.getRemoteDevice(addrStr);
        }).when(mMockBluetoothAdapter).getRemoteDevice(any(byte[].class));

        doAnswer(invocation -> {
            mBluetoothGattService = (BluetoothGattService) invocation.getArguments()[0];
            return true;
        }).when(mMockBluetoothGattServer).addService(any());

        doAnswer(invocation -> {
            mBluetoothGattService = null;
            return true;
        }).when(mMockBluetoothGattServer).removeService(any());

        doAnswer(invocation -> {
            return mBluetoothGattService;
        }).when(mMockBluetoothGattServer).getService(any());

        Looper looper = Looper.myLooper();
        if (looper == null) {
            Looper.prepare();
        }

        mBroadcastReceiverCaptor = ArgumentCaptor.forClass(BroadcastReceiver.class);
        mBluetoothGattServerCallbackCaptor =
                ArgumentCaptor.forClass(BluetoothGattServerCallback.class);
        mBytesCaptor = ArgumentCaptor.forClass(byte[].class);

        mTestGattServer = new FastPairGattServer(mMockContext, TEST_MODEL_ID,
                TEST_PRIVATE_KEY_B_BASE64, mMockFastPairCallbacks, true,
                mMockFastPairAccountKeyStorage);
    }

    private void setAvailableAccountKeys(List<AccountKey> keys) {
        when(mMockFastPairAccountKeyStorage.getAllAccountKeys())
                .thenReturn(new ArrayList<>(keys));
    }

    private void setCurrentRpa(String address) {
        mTestGattServer.updateLocalRpa(mTargetBluetoothAdapter.getRemoteDevice(address));
    }

    private void setDeviceName(String name) {
        assertThat(mReceiver).isNotNull();
        assertThat(name).isNotNull();
        Intent nameChange = new Intent(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        nameChange.putExtra(BluetoothAdapter.EXTRA_LOCAL_NAME, name);
        mReceiver.onReceive(mMockContext, nameChange);
    }

    private void startAndVerifyServer() {
        assertThat(mTestGattServer.isStarted()).isFalse();
        mTestGattServer.start();

        verify(mMockBluetoothManager)
                .openGattServer(any(), mBluetoothGattServerCallbackCaptor.capture());
        mBluetoothGattServerCallback = mBluetoothGattServerCallbackCaptor.getValue();

        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(), any());
        mReceiver = mBroadcastReceiverCaptor.getValue();
        verify(mMockBluetoothGattServer).addService(eq(mBluetoothGattService));

        assertThat(mBluetoothGattService).isNotNull();
        assertThat(mBluetoothGattService.getUuid()).isEqualTo(FAST_PAIR_SERVICE_UUID.getUuid());

        mModelIdCharacteristic =
                mBluetoothGattService.getCharacteristic(FAST_PAIR_MODEL_ID_UUID.getUuid());
        mKeyBasedPairingCharacteristic =
                mBluetoothGattService.getCharacteristic(KEY_BASED_PAIRING_UUID.getUuid());
        mPasskeyCharacteristic =
                mBluetoothGattService.getCharacteristic(PASSKEY_UUID.getUuid());
        mAccountKeyCharacteristic =
                mBluetoothGattService.getCharacteristic(ACCOUNT_KEY_UUID.getUuid());
        mDeviceNameCharacteristic =
                mBluetoothGattService.getCharacteristic(
                        DEVICE_NAME_CHARACTERISTIC_CONFIG.getUuid());

        assertThat(mModelIdCharacteristic).isNotNull();
        assertThat(mKeyBasedPairingCharacteristic).isNotNull();
        assertThat(mPasskeyCharacteristic).isNotNull();
        assertThat(mAccountKeyCharacteristic).isNotNull();
        assertThat(mDeviceNameCharacteristic).isNotNull();

        assertThat(mTestGattServer.isStarted()).isTrue();
        assertThat(mTestGattServer.isConnected()).isFalse();
        clearInvocations(mMockContext);
    }

    private void connectDevice(BluetoothDevice device) {
        assertThat(mBluetoothGattServerCallback).isNotNull();
        mBluetoothGattServerCallback.onConnectionStateChange(device,
                BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
    }

    private void disconnectDevice(BluetoothDevice device) {
        assertThat(mBluetoothGattServerCallback).isNotNull();
        mBluetoothGattServerCallback.onConnectionStateChange(device,
                BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED);
    }

    private byte[] sendReadModelIdRequest(BluetoothDevice device) {
        assertThat(mBluetoothGattServerCallback).isNotNull();
        assertThat(mModelIdCharacteristic).isNotNull();
        mBluetoothGattServerCallback.onCharacteristicReadRequest(device, 0, 0,
                mModelIdCharacteristic);
        verify(mMockBluetoothGattServer).sendResponse(eq(device), eq(0), anyInt(), anyInt(),
                mBytesCaptor.capture());
        return mBytesCaptor.getValue();
    }

    private byte[] sendReadDeviceNameRequest(BluetoothDevice device) {
        assertThat(mBluetoothGattServerCallback).isNotNull();
        assertThat(mDeviceNameCharacteristic).isNotNull();
        mBluetoothGattServerCallback.onCharacteristicReadRequest(device, 0, 0,
                mDeviceNameCharacteristic);
        verify(mMockBluetoothGattServer).sendResponse(eq(device), eq(0), anyInt(), anyInt(),
                mBytesCaptor.capture());
        return mBytesCaptor.getValue();
    }

    private byte[] sendKeyBasedPairingRequest(BluetoothDevice device, byte[] value) {
        assertThat(mBluetoothGattServerCallback).isNotNull();
        assertThat(mKeyBasedPairingCharacteristic).isNotNull();
        return sendCharacteristicWriteRequest(device, mKeyBasedPairingCharacteristic, value);
    }

    private byte[] buildKeyBasedPairingRequest(byte type, byte flags, byte[] provider,
            byte[] seeker, byte[] salt, byte[] publicKey, byte[] key) {

        // Make request and encrypt it
        byte[] request = new byte[16];
        request[0] = type;
        request[1] = flags;

        request[2] = provider[0];
        request[3] = provider[1];
        request[4] = provider[2];
        request[5] = provider[3];
        request[6] = provider[4];
        request[7] = provider[5];

        int start = 8;
        if (seeker != null) {
            request[8] = seeker[0];
            request[9] = seeker[1];
            request[10] = seeker[2];
            request[11] = seeker[3];
            request[12] = seeker[4];
            request[13] = seeker[5];
            start = 14;
        }

        for (int i = start; i < 16; i++) {
            request[i] = salt[i - start];
        }

        byte[] encryptedRequest = encrypt(request, key);

        if (publicKey != null) {
            byte[] requestWithKey = new byte[80];
            for (int i = 0; i < 16; i++) {
                requestWithKey[i] = encryptedRequest[i];
            }
            for (int i = 16; i < 80; i++) {
                requestWithKey[i] = publicKey[i - 16];
            }
            return requestWithKey;
        }

        return encryptedRequest;
    }

    private byte[] sendPasskeyRequest(BluetoothDevice device, byte[] value) {
        assertThat(mBluetoothGattServerCallback).isNotNull();
        assertThat(mPasskeyCharacteristic).isNotNull();
        return sendCharacteristicWriteRequest(device, mPasskeyCharacteristic, value);
    }

    private byte[] buildPasskeyRequest(byte type, byte[] passkey, byte[] salt, byte[] key) {
        byte[] request = new byte[16];
        request[0] = type;
        request[1] = passkey[0];
        request[2] = passkey[1];
        request[3] = passkey[2];

        for (int i = 4; i < 16; i++) {
            request[i] = salt[i - 4];
        }

        return encrypt(request, key);
    }

    private byte[] sendAccountKeyRequest(BluetoothDevice device, byte[] value) {
        assertThat(mBluetoothGattServerCallback).isNotNull();
        assertThat(mAccountKeyCharacteristic).isNotNull();
        return sendCharacteristicWriteRequest(device, mAccountKeyCharacteristic, value);
    }

    private byte[] buildAccountKeyRequest(byte[] accountKeyBytes, byte[] key) {
        byte[] request = Arrays.copyOf(accountKeyBytes, 16);
        return encrypt(request, key);
    }

    private byte[] sendCharacteristicWriteRequest(BluetoothDevice device,
            BluetoothGattCharacteristic characteristic, byte[] value) {
        mBluetoothGattServerCallback.onCharacteristicWriteRequest(device, 0, characteristic, false,
                false, 0, value);
        verify(mMockBluetoothGattServer).sendResponse(eq(device), eq(0), anyInt(), anyInt(),
                mBytesCaptor.capture());
        return mBytesCaptor.getValue();
    }

    private byte[] sendDescriptorWriteRequest(BluetoothDevice device,
            BluetoothGattDescriptor descriptor, byte[] value) {
        mBluetoothGattServerCallback.onDescriptorWriteRequest(device, 0, descriptor, false,
                false, 0, value);
        verify(mMockBluetoothGattServer).sendResponse(eq(device), eq(0), anyInt(), anyInt(),
                mBytesCaptor.capture());
        return mBytesCaptor.getValue();
    }

    private void sendPairingRequestBroadcast(BluetoothDevice device, int key) {
        assertThat(mReceiver).isNotNull();
        Intent pairingRequest = new Intent(BluetoothDevice.ACTION_PAIRING_REQUEST);
        pairingRequest.putExtra(BluetoothDevice.EXTRA_PAIRING_KEY, key);
        pairingRequest.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        mReceiver.onReceive(mMockContext, pairingRequest);
    }

    private void sendBondStateChangeBroadcast(BluetoothDevice device, int newState, int oldState) {
        assertThat(mReceiver).isNotNull();
        Intent bondStateChange = new Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        bondStateChange.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        bondStateChange.putExtra(BluetoothDevice.EXTRA_BOND_STATE, newState);
        bondStateChange.putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, oldState);
        mReceiver.onReceive(mMockContext, bondStateChange);
    }

    private byte[] encrypt(byte[] payload, byte[] keyBytes) {
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] decrypt(byte[] encrypted, byte[] keyBytes) {
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            return null;
        }
    }

    @Test
    public void testStartWhileStarted_startIgnored() {
        startAndVerifyServer();
        mTestGattServer.start();
        verifyNoMoreInteractions(mMockContext);
        assertThat(mTestGattServer.isStarted()).isTrue();
        assertThat(mTestGattServer.isConnected()).isFalse();
    }

    @Test
    public void testStopWhileStopped_stopIgnored() {
        clearInvocations(mMockContext);
        mTestGattServer.stop();
        verifyNoMoreInteractions(mMockContext);
        assertThat(mTestGattServer.isStarted()).isFalse();
    }

    @Test
    public void testStopWhileDeviceConnected_deviceDisconnected() {
        startAndVerifyServer();
        connectDevice(mMockBluetoothDevice);
        assertThat(mTestGattServer.isConnected()).isTrue();
        mTestGattServer.stop();
        verify(mMockBluetoothGattServer).cancelConnection(eq(mMockBluetoothDevice));
        assertThat(mTestGattServer.isConnected()).isFalse();
        assertThat(mTestGattServer.isStarted()).isFalse();
    }

    @Test
    public void testProcessKeyBasedPairingRequestWithAntiSpoofKey_responseValid() {
        startAndVerifyServer();
        setCurrentRpa(TEST_RPA_STRING);
        connectDevice(mMockBluetoothDevice);
        byte[] request = buildKeyBasedPairingRequest(KEY_BASED_PAIRING_REQUEST_MSG_TYPE,
                /* flags= */ (byte) 0x00, TEST_RPA_BYTES, /* seeker_address= */ null, TEST_SALT_8,
                TEST_PUBLIC_KEY_A, TEST_GENERATED_KEY);
        byte[] encryptedResponse = sendKeyBasedPairingRequest(mMockBluetoothDevice, request);
        assertThat(encryptedResponse).isNotNull();

        byte[] response = decrypt(encryptedResponse, TEST_GENERATED_KEY);
        assertThat(response).isNotNull();
        assertThat(response.length).isEqualTo(16);

        byte type = response[0];
        byte[] addressBytes = Arrays.copyOfRange(response, 1, 7);
        byte[] salt = Arrays.copyOfRange(response, 7, 15);

        assertThat(type).isEqualTo(KEY_BASED_PAIRING_RESPONSE_MSG_TYPE);
        assertThat(addressBytes).isEqualTo(TEST_DEVICE_ADDRESS_BYTES);
        assertThat(salt).isNotNull();

        verify(mMockBluetoothGattServer).notifyCharacteristicChanged(eq(mMockBluetoothDevice),
                eq(mDeviceNameCharacteristic), eq(false));
        verify(mMockBluetoothGattServer).notifyCharacteristicChanged(eq(mMockBluetoothDevice),
                eq(mKeyBasedPairingCharacteristic), eq(false));
    }

    @Test
    public void testProcessKeyBasedPairingRequestUsingAccountKey_responseValid() {
        startAndVerifyServer();
        setCurrentRpa(TEST_RPA_STRING);
        setAvailableAccountKeys(List.of(new AccountKey(TEST_VALID_ACCOUNT_KEY)));
        connectDevice(mMockBluetoothDevice);
        byte[] request = buildKeyBasedPairingRequest(KEY_BASED_PAIRING_REQUEST_MSG_TYPE,
                /* flags= */ (byte) 0x00, TEST_RPA_BYTES, /* seeker_address= */ null, TEST_SALT_8,
                TEST_PUBLIC_KEY_A, TEST_VALID_ACCOUNT_KEY);
        request = Arrays.copyOfRange(request, 0, 16);
        byte[] encryptedResponse = sendKeyBasedPairingRequest(mMockBluetoothDevice, request);
        assertThat(encryptedResponse).isNotNull();

        byte[] response = decrypt(encryptedResponse, TEST_VALID_ACCOUNT_KEY);
        assertThat(response).isNotNull();
        assertThat(response.length).isEqualTo(16);

        byte type = response[0];
        byte[] addressBytes = Arrays.copyOfRange(response, 1, 7);
        byte[] salt = Arrays.copyOfRange(response, 7, 15);

        assertThat(type).isEqualTo(KEY_BASED_PAIRING_RESPONSE_MSG_TYPE);
        assertThat(addressBytes).isEqualTo(TEST_DEVICE_ADDRESS_BYTES);
        assertThat(salt).isNotNull();

        verify(mMockBluetoothGattServer).notifyCharacteristicChanged(eq(mMockBluetoothDevice),
                eq(mDeviceNameCharacteristic), eq(false));
        verify(mMockBluetoothGattServer).notifyCharacteristicChanged(eq(mMockBluetoothDevice),
                eq(mKeyBasedPairingCharacteristic), eq(false));
    }

    @Test
    public void testProcessKeyBasedPairingRequestUsingInvalidKey_requestIgnored() {
        startAndVerifyServer();
        setCurrentRpa(TEST_RPA_STRING);
        connectDevice(mMockBluetoothDevice);
        byte[] request = buildKeyBasedPairingRequest(KEY_BASED_PAIRING_REQUEST_MSG_TYPE,
                /* flags= */ (byte) 0x00, TEST_RPA_BYTES, /* seeker_address= */ null, TEST_SALT_8,
                TEST_PUBLIC_KEY_A, TEST_WRONG_GENERATED_KEY);
        byte[] encryptedResponse = sendKeyBasedPairingRequest(mMockBluetoothDevice, request);
        assertThat(encryptedResponse).isNull();
        assertThat(mTestGattServer.isFastPairSessionActive()).isFalse();
        verify(mMockBluetoothGattServer, never()).notifyCharacteristicChanged(any(), any(),
                anyBoolean());
    }

    @Test
    public void testProcessKeyBasedPairingRequestTenFailures_allRequestsIgnored() {
        startAndVerifyServer();
        setCurrentRpa(TEST_RPA_STRING);

        for (int i = 0; i < 10; i++) {
            connectDevice(mMockBluetoothDevice);
            byte[] request = buildKeyBasedPairingRequest(KEY_BASED_PAIRING_REQUEST_MSG_TYPE,
                    /* flags= */ (byte) 0x00, TEST_RPA_BYTES, /* seeker_address= */ null,
                    TEST_SALT_8, TEST_PUBLIC_KEY_A, TEST_WRONG_GENERATED_KEY);
            byte[] encryptedResponse = sendKeyBasedPairingRequest(mMockBluetoothDevice, request);
            assertThat(encryptedResponse).isNull();
            clearInvocations(mMockBluetoothGattServer);
            assertThat(mTestGattServer.isFastPairSessionActive()).isFalse();
        }

        connectDevice(mMockBluetoothDevice);
        byte[] request = buildKeyBasedPairingRequest(KEY_BASED_PAIRING_REQUEST_MSG_TYPE,
                /* flags= */ (byte) 0x00, TEST_RPA_BYTES, /* seeker_address= */ null, TEST_SALT_8,
                TEST_PUBLIC_KEY_A, TEST_GENERATED_KEY);
        byte[] encryptedResponse = sendKeyBasedPairingRequest(mMockBluetoothDevice, request);
        assertThat(encryptedResponse).isNull();
        assertThat(mTestGattServer.isFastPairSessionActive()).isFalse();
        verify(mMockBluetoothGattServer, never()).notifyCharacteristicChanged(any(), any(),
                anyBoolean());
    }

    @Test
    public void testProcessPasskeyRequest_pairingConfirmed() {
        testProcessKeyBasedPairingRequestWithAntiSpoofKey_responseValid();
        clearInvocations(mMockBluetoothGattServer);
        sendBondStateChangeBroadcast(mMockBluetoothDevice,
                BluetoothDevice.BOND_BONDING, BluetoothDevice.BOND_NONE);
        sendPairingRequestBroadcast(mMockBluetoothDevice, TEST_PAIRING_KEY);
        byte[] request = buildPasskeyRequest(PASSKEY_REQUEST_SEEKER, TEST_PAIRING_KEY_BYTES,
                TEST_SALT_12, TEST_GENERATED_KEY);
        byte[] unused = sendPasskeyRequest(mMockBluetoothDevice, request);

        byte[] encryptedResponse = mPasskeyCharacteristic.getValue();
        byte[] passkeyResponse = decrypt(encryptedResponse, TEST_GENERATED_KEY);

        assertThat(passkeyResponse.length).isEqualTo(16);
        byte type = passkeyResponse[0];
        byte[] passkey = Arrays.copyOfRange(passkeyResponse, 1, 4);
        byte[] salt = Arrays.copyOfRange(passkeyResponse, 4, 15);

        assertThat(type).isEqualTo(PASSKEY_REQUEST_PROVIDER);
        assertThat(passkey).isEqualTo(TEST_PAIRING_KEY_BYTES);
        assertThat(salt).isNotNull();

        verify(mMockBluetoothDevice).setPairingConfirmation(eq(true));
        assertThat(mTestGattServer.isFastPairSessionActive()).isTrue(); // lifespan 10000
    }

    @Test
    public void testProcessPasskeyRequestWrongPasskey_pairingCancelled() {
        testProcessKeyBasedPairingRequestWithAntiSpoofKey_responseValid();
        clearInvocations(mMockBluetoothGattServer);
        sendBondStateChangeBroadcast(mMockBluetoothDevice,
                BluetoothDevice.BOND_BONDING, BluetoothDevice.BOND_NONE);
        sendPairingRequestBroadcast(mMockBluetoothDevice, TEST_PAIRING_KEY);
        byte[] request = buildPasskeyRequest(PASSKEY_REQUEST_SEEKER, TEST_WRONG_PAIRING_KEY_BYTES,
                TEST_SALT_12, TEST_GENERATED_KEY);
        byte[] encryptedResponse = sendPasskeyRequest(mMockBluetoothDevice, request);
        verify(mMockBluetoothDevice).setPairingConfirmation(eq(false));
        assertThat(mTestGattServer.isFastPairSessionActive()).isFalse();
    }

    @Test
    public void testProcessPasskeyRequestShortPasskey_pairingCancelled() {
        testProcessKeyBasedPairingRequestWithAntiSpoofKey_responseValid();
        clearInvocations(mMockBluetoothGattServer);
        sendBondStateChangeBroadcast(mMockBluetoothDevice,
                BluetoothDevice.BOND_BONDING, BluetoothDevice.BOND_NONE);
        sendPairingRequestBroadcast(mMockBluetoothDevice, TEST_PAIRING_KEY);
        byte[] encryptedResponse = sendPasskeyRequest(mMockBluetoothDevice, TEST_REQUEST_SHORT);
        assertThat(mTestGattServer.isFastPairSessionActive()).isFalse();
    }

    @Test
    public void testProcessPasskeyRequestNullPasskey_pairingCancelled() {
        testProcessKeyBasedPairingRequestWithAntiSpoofKey_responseValid();
        clearInvocations(mMockBluetoothGattServer);
        sendBondStateChangeBroadcast(mMockBluetoothDevice,
                BluetoothDevice.BOND_BONDING, BluetoothDevice.BOND_NONE);
        sendPairingRequestBroadcast(mMockBluetoothDevice, TEST_PAIRING_KEY);
        byte[] encryptedResponse = sendPasskeyRequest(mMockBluetoothDevice, null);
        assertThat(mTestGattServer.isFastPairSessionActive()).isFalse();
    }

    @Test
    public void testProcessPasskeyBeforePairingCodeReceived_pairingContinues() {
        testProcessKeyBasedPairingRequestWithAntiSpoofKey_responseValid();
        clearInvocations(mMockBluetoothGattServer);
        sendBondStateChangeBroadcast(mMockBluetoothDevice,
                BluetoothDevice.BOND_BONDING, BluetoothDevice.BOND_NONE);
        byte[] request = buildPasskeyRequest(PASSKEY_REQUEST_SEEKER, TEST_PAIRING_KEY_BYTES,
                TEST_SALT_12, TEST_GENERATED_KEY);
        sendPasskeyRequest(mMockBluetoothDevice, request);
        verify(mMockBluetoothDevice, never()).setPairingConfirmation(anyBoolean());
        assertThat(mTestGattServer.isFastPairSessionActive()).isTrue(); // lifespan 10000
    }

    @Test
    public void testReceivePairingCodeBeforeSeekerPasskey_noPairingResponseSent() {
        testProcessKeyBasedPairingRequestWithAntiSpoofKey_responseValid();
        clearInvocations(mMockBluetoothGattServer);
        sendBondStateChangeBroadcast(mMockBluetoothDevice,
                BluetoothDevice.BOND_BONDING, BluetoothDevice.BOND_NONE);
        sendPairingRequestBroadcast(mMockBluetoothDevice, TEST_PAIRING_KEY);
        verify(mMockBluetoothGattServer, never()).notifyCharacteristicChanged(
                eq(mMockBluetoothDevice), eq(mPasskeyCharacteristic), eq(false));
        verify(mMockBluetoothDevice, never()).setPairingConfirmation(anyBoolean());
        assertThat(mTestGattServer.isFastPairSessionActive()).isTrue(); // Lifespan 35000
    }

    @Test
    public void testReceivePairingCodeAfterSeekerPasskey_sendsPairingResponse() {
        testProcessKeyBasedPairingRequestWithAntiSpoofKey_responseValid();
        clearInvocations(mMockBluetoothGattServer);
        sendBondStateChangeBroadcast(mMockBluetoothDevice,
                BluetoothDevice.BOND_BONDING, BluetoothDevice.BOND_NONE);
        byte[] request = buildPasskeyRequest(PASSKEY_REQUEST_SEEKER, TEST_PAIRING_KEY_BYTES,
                TEST_SALT_12, TEST_GENERATED_KEY);
        byte[] unused = sendPasskeyRequest(mMockBluetoothDevice, request);
        sendPairingRequestBroadcast(mMockBluetoothDevice, TEST_PAIRING_KEY);
        byte[] encryptedResponse = mPasskeyCharacteristic.getValue();
        byte[] passkeyResponse = decrypt(encryptedResponse, TEST_GENERATED_KEY);

        assertThat(passkeyResponse.length).isEqualTo(16);
        byte type = passkeyResponse[0];
        byte[] passkey = Arrays.copyOfRange(passkeyResponse, 1, 4);
        byte[] salt = Arrays.copyOfRange(passkeyResponse, 4, 15);

        assertThat(type).isEqualTo(PASSKEY_REQUEST_PROVIDER);
        assertThat(passkey).isEqualTo(TEST_PAIRING_KEY_BYTES);
        assertThat(salt).isNotNull();

        verify(mMockBluetoothGattServer).notifyCharacteristicChanged(eq(mMockBluetoothDevice),
                eq(mPasskeyCharacteristic), eq(false));
        verify(mMockBluetoothDevice).setPairingConfirmation(eq(true));
        assertThat(mTestGattServer.isFastPairSessionActive()).isTrue(); // Lifespan 35000
    }

    @Test
    public void testReceivePairingCodeWhileDisconnected_nothingSent() {
        startAndVerifyServer();
        assertThat(mTestGattServer.isConnected()).isFalse();
        assertThat(mTestGattServer.isFastPairSessionActive()).isFalse();
        sendBondStateChangeBroadcast(mMockBluetoothDevice,
                BluetoothDevice.BOND_BONDING, BluetoothDevice.BOND_NONE);
        sendPairingRequestBroadcast(mMockBluetoothDevice, TEST_PAIRING_KEY);
        byte[] encryptedResponse = mPasskeyCharacteristic.getValue();
        byte[] passkeyResponse = decrypt(encryptedResponse, TEST_GENERATED_KEY);
        assertThat(passkeyResponse).isNull();
        verify(mMockBluetoothGattServer, never()).notifyCharacteristicChanged(any(), any(),
                anyBoolean());
        assertThat(mTestGattServer.isFastPairSessionActive()).isFalse();
    }

    @Test
    public void testProcessAccountKeyRequestWithValidKey_keyAdded() {
        testProcessPasskeyRequest_pairingConfirmed();
        clearInvocations(mMockBluetoothGattServer);
        sendBondStateChangeBroadcast(mMockBluetoothDevice,
                BluetoothDevice.BOND_BONDED, BluetoothDevice.BOND_BONDING);
        assertThat(mTestGattServer.isFastPairSessionActive()).isTrue();
        byte[] request = buildAccountKeyRequest(TEST_VALID_ACCOUNT_KEY, TEST_GENERATED_KEY);
        byte[] encryptedResponse = sendAccountKeyRequest(mMockBluetoothDevice, request);
        verify(mMockFastPairAccountKeyStorage).add(eq(new AccountKey(TEST_VALID_ACCOUNT_KEY)));
        assertThat(mTestGattServer.isFastPairSessionActive()).isFalse();
    }

    @Test
    public void testProcessAccountKeyRequestWithInvalidKey_keyIgnored() {
        testProcessPasskeyRequest_pairingConfirmed();
        clearInvocations(mMockBluetoothGattServer);
        sendBondStateChangeBroadcast(mMockBluetoothDevice,
                BluetoothDevice.BOND_BONDED, BluetoothDevice.BOND_BONDING);
        assertThat(mTestGattServer.isFastPairSessionActive()).isTrue();
        byte[] request = buildAccountKeyRequest(TEST_INVALID_ACCOUNT_KEY, TEST_GENERATED_KEY);
        byte[] encryptedResponse = sendAccountKeyRequest(mMockBluetoothDevice, request);
        verify(mMockFastPairAccountKeyStorage, never()).add(any());
        assertThat(mTestGattServer.isFastPairSessionActive()).isFalse();
    }

    @Test
    public void testProcessAccountKeyRequestWithEmptyKey_requestIgnored() {
        testProcessPasskeyRequest_pairingConfirmed();
        clearInvocations(mMockBluetoothGattServer);
        sendBondStateChangeBroadcast(mMockBluetoothDevice,
                BluetoothDevice.BOND_BONDED, BluetoothDevice.BOND_BONDING);
        assertThat(mTestGattServer.isFastPairSessionActive()).isTrue();
        byte[] encryptedResponse = sendAccountKeyRequest(mMockBluetoothDevice, new byte[]{});
        verify(mMockFastPairAccountKeyStorage, never()).add(any());
        assertThat(mTestGattServer.isFastPairSessionActive()).isFalse();
    }

    @Test
    public void testProcessAccountKeyRequestWithShortKey_requestIgnored() {
        testProcessPasskeyRequest_pairingConfirmed();
        clearInvocations(mMockBluetoothGattServer);
        sendBondStateChangeBroadcast(mMockBluetoothDevice,
                BluetoothDevice.BOND_BONDED, BluetoothDevice.BOND_BONDING);
        assertThat(mTestGattServer.isFastPairSessionActive()).isTrue();
        byte[] encryptedResponse = sendAccountKeyRequest(mMockBluetoothDevice, TEST_REQUEST_SHORT);
        verify(mMockFastPairAccountKeyStorage, never()).add(any());
        assertThat(mTestGattServer.isFastPairSessionActive()).isFalse();
    }

    @Test
    public void testProcessAccountKeyRequestWithNullKey_requestIgnored() {
        testProcessPasskeyRequest_pairingConfirmed();
        clearInvocations(mMockBluetoothGattServer);
        sendBondStateChangeBroadcast(mMockBluetoothDevice,
                BluetoothDevice.BOND_BONDED, BluetoothDevice.BOND_BONDING);
        assertThat(mTestGattServer.isFastPairSessionActive()).isTrue();
        byte[] encryptedResponse = sendAccountKeyRequest(mMockBluetoothDevice, null);
        verify(mMockFastPairAccountKeyStorage, never()).add(any());
        assertThat(mTestGattServer.isFastPairSessionActive()).isFalse();
    }

    @Test
    public void testDeviceDisconnectsMidSession_keysClearedAndPairingStopped() {
        testProcessKeyBasedPairingRequestWithAntiSpoofKey_responseValid();
        clearInvocations(mMockBluetoothGattServer);
        disconnectDevice(mMockBluetoothDevice);
        assertThat(mTestGattServer.isConnected()).isFalse();
        assertThat(mTestGattServer.isFastPairSessionActive()).isFalse();
        verify(mMockFastPairCallbacks).onPairingCompleted(eq(false));
    }

    @Test
    public void testReadDeviceModelId() {
        testProcessKeyBasedPairingRequestWithAntiSpoofKey_responseValid();
        clearInvocations(mMockBluetoothGattServer);
        byte[] response = sendReadModelIdRequest(mMockBluetoothDevice);
        assertThat(response).isEqualTo(TEST_MODEL_ID_BYTES);
    }

    @Test
    public void testReadDeviceName() {
        testProcessKeyBasedPairingRequestWithAntiSpoofKey_responseValid();
        clearInvocations(mMockBluetoothGattServer);
        byte[] response = sendReadDeviceNameRequest(mMockBluetoothDevice);
        String name = new String(response, StandardCharsets.UTF_8);
        assertThat(name).isEqualTo(TEST_DEVICE_NAME);
    }

    @Test
    public void testDeviceNameChanged() {
        startAndVerifyServer();
        setDeviceName(TEST_DEVICE_NAME_2);
        assertThat(mDeviceNameCharacteristic).isNotNull();
        assertThat(mDeviceNameCharacteristic.getValue()).isEqualTo(TEST_DEVICE_NAME_2.getBytes());
    }

    @Test
    public void testDescriptorWriteRequest_responseValid() {
        startAndVerifyServer();
        sendDescriptorWriteRequest(mMockBluetoothDevice, mMockGattDescriptor, null);
        verify(mMockBluetoothGattServer).sendResponse(eq(mMockBluetoothDevice), anyInt(),
                eq(BluetoothGatt.GATT_SUCCESS), anyInt(), any());
    }

    @Test
    public void testUnknownBroadcastAction_broadcastIgnored() {
        startAndVerifyServer();
        clearInvocations(mMockBluetoothGattServer);
        assertThat(mReceiver).isNotNull();
        Intent unknown = new Intent(BluetoothDevice.ACTION_UUID);
        mReceiver.onReceive(mMockContext, unknown);
        verifyNoMoreInteractions(mMockBluetoothGattServer);
    }
}
