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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.UserManager;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link FastPairProvider}
 *
 * Run: atest FastPairProviderTest
 */
@RunWith(MockitoJUnitRunner.class)
public class FastPairProviderTest {
    private static final String KEY_NUM_ACCOUNT_KEYS = "AccountKeysCount";
    private static final String FAST_PAIR_PREFERENCES = "com.candroid.car.bluetooth";

    public static final ParcelUuid SERVICE_UUID = ParcelUuid
            .fromString("0000FE2C-0000-1000-8000-00805f9b34fb");

    static final String DEVICE_NAME = "name";
    static final String DEVICE_ADDRESS_STRING = "11:22:33:44:55:66";

    static final int TEST_TX_POWER = 50;
    static final int ADVERTISING_EVENT_TIMEOUT_MS = 3000;

    static final int TEST_MODEL_ID = 0x112233;
    static final int TEST_EMPTY_MODEL_ID = 0x000000;

    static final byte[] TEST_MODEL_ID_ADVERTISEMENT = new byte[]{0x11, 0x22, 0x33};
    static final byte[] TEST_ACCOUNT_KEY_FILTER_ADVERTISEMENT = new byte[]{0x00, 0x00};

    static final byte[] TEST_PRIVATE_KEY = {0x02, (byte) 0xB4, 0x37, (byte) 0xB0, (byte) 0xED,
            (byte) 0xD6, (byte) 0xBB, (byte) 0xD4, 0x29, 0x06, 0x4A, 0x4E, 0x52, (byte) 0x9F,
            (byte) 0xCB, (byte) 0xF1, (byte) 0xC4, (byte) 0x8D, 0x0D, 0x62, 0x49, 0x24, (byte) 0xD5,
            (byte) 0x92, 0x27, 0x4B, 0x7E, (byte) 0xD8, 0x11, (byte) 0x93, (byte) 0xD7, 0x63
    };
    static final String TEST_PRIVATE_KEY_BASE64 = Base64.getEncoder()
            .encodeToString(TEST_PRIVATE_KEY);
    static final String TEST_EMPTY_PRIVATE_KEY = "";

    static final byte[] TEST_PUBLIC_KEY = {0x36, (byte) 0xAC, 0x68, 0x2C, 0x50, (byte) 0x82, 0x15,
            0x66, (byte) 0x8F, (byte) 0xBE, (byte) 0xFE, 0x24, 0x7D, 0x01, (byte) 0xD5, (byte) 0xEB,
            (byte) 0x96, (byte) 0xE6, 0x31, (byte) 0x8E, (byte) 0x85, 0x5B, 0x2D, 0x64, (byte) 0xB5,
            0x19, 0x5D, 0x38, (byte) 0xEE, 0x7E, 0x37, (byte) 0xBE, 0x18, 0x38, (byte) 0xC0,
            (byte) 0xB9, 0x48, (byte) 0xC3, (byte) 0xF7, 0x55, 0x20, (byte) 0xE0, 0x7E, 0x70,
            (byte) 0xF0, 0x72, (byte) 0x91, 0x41, (byte) 0x9A, (byte) 0xCE, 0x2D, 0x28, 0x14, 0x3C,
            0x5A, (byte) 0xDB, 0x2D, (byte) 0xBD, (byte) 0x98, (byte) 0xEE, 0x3C, (byte) 0x8E, 0x4F,
            (byte) 0xBF};
    static final String TEST_PUBLIC_KEY_BASE64 = Base64.getEncoder()
            .encodeToString(TEST_PUBLIC_KEY);

    MockitoSession mMockitoSession;

    @Mock Context mMockContext;
    @Mock Resources mMockResources;
    @Mock UserManager mMockUserManager;
    @Mock SharedPreferences mMockSharedPreferences;
    @Mock SharedPreferences.Editor mMockSharedPreferencesEditor;
    @Mock BluetoothAdapter mMockBluetoothAdapter;
    @Mock BluetoothManager mMockBluetoothManager;
    @Mock BluetoothDevice mMockBluetoothDevice;
    @Mock BluetoothGattServer mMockBluetoothGattServer;
    @Mock BluetoothLeAdvertiser mMockBluetoothLeAdvertiser;
    @Mock AdvertisingSet mMockAdvertisingSet;

    @Captor ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor;
    BroadcastReceiver mReceiver;

    private int mSharedPreferencesContentCount = 0;
    private Map<String, String> mSharedPreferencesContent;

    private BluetoothGattService mFastPairGattService;

    private AdvertisingSetCallback mAdvertisingSetCallback;

    FastPairAdvertiser.Callbacks mAdvertiserCallbacks;
    FastPairGattServer.Callbacks mGattServerCallbacks;

    private FastPairProvider mFastPairProvider;

    @Rule
    public final TestRule mClearInlineMocksRule = new TestRule() {
        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    // When using inline mock maker, clean up inline mocks to prevent OutOfMemory
                    // errors. See https://github.com/mockito/mockito/issues/1614 and b/259280359.
                    Mockito.framework().clearInlineMocks();
                }
            };
        }
    };

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        when(mMockContext.getSystemService(BluetoothManager.class))
                .thenReturn(mMockBluetoothManager);
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_OFF);
        when(mMockBluetoothAdapter.getBluetoothLeAdvertiser())
                .thenReturn(mMockBluetoothLeAdvertiser);
        when(mMockBluetoothAdapter.getName()).thenReturn(DEVICE_NAME);
        when(mMockBluetoothAdapter.getAddress()).thenReturn(DEVICE_ADDRESS_STRING);
        when(mMockBluetoothManager.openGattServer(any(), any()))
                .thenReturn(mMockBluetoothGattServer);
        when(mMockBluetoothAdapter.getRemoteDevice(any(String.class)))
                .thenReturn(mMockBluetoothDevice);
        when(mMockBluetoothAdapter.getRemoteDevice(any(byte[].class)))
                .thenReturn(mMockBluetoothDevice);

        doAnswer(invocation -> {
            mAdvertisingSetCallback = (AdvertisingSetCallback) invocation.getArguments()[5];
            return true;
        }).when(mMockBluetoothLeAdvertiser).startAdvertisingSet(
                any(), any(), any(), any(), any(), any());

        doAnswer(invocation -> {
            mAdvertisingSetCallback = null;
            return true;
        }).when(mMockBluetoothLeAdvertiser).stopAdvertisingSet(any());

        setupMockKeyStorage();

        mFastPairGattService = null;
        doAnswer(invocation -> {
            mFastPairGattService = (BluetoothGattService) invocation.getArgument(0);
            return true;
        }).when(mMockBluetoothGattServer).addService(any());
        doAnswer(invocation -> {
            mFastPairGattService = null;
            return true;
        }).when(mMockBluetoothGattServer).removeService(any());
        doAnswer(invocation -> {
            return mFastPairGattService;
        }).when(mMockBluetoothGattServer).getService(any());

        mBroadcastReceiverCaptor = ArgumentCaptor.forClass(BroadcastReceiver.class);

        mMockitoSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.WARN)
                .startMocking();

        Looper looper = Looper.myLooper();
        if (looper == null) {
            Looper.prepare();
        }
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

    private void setupMockKeyStorage() {
        mSharedPreferencesContentCount = 0;
        mSharedPreferencesContent = new HashMap<>();

        when(mMockContext.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mMockSharedPreferences);
        when(mMockSharedPreferences.edit()).thenReturn(mMockSharedPreferencesEditor);

        // SharedPreferencesEditor.putInt
        doAnswer(invocation -> {
            String key = (String) invocation.getArgument(0);
            int value = (Integer) invocation.getArgument(1);
            if (KEY_NUM_ACCOUNT_KEYS.equals(key)) {
                mSharedPreferencesContentCount = value;
            }
            return invocation.getMock();
        }).when(mMockSharedPreferencesEditor).putInt(anyString(), anyInt());

        // SharedPreferencesEditor.putString
        doAnswer(invocation -> {
            String key = (String) invocation.getArgument(0);
            String value = (String) invocation.getArgument(1);
            mSharedPreferencesContent.put(key, value);
            return invocation.getMock();
        }).when(mMockSharedPreferencesEditor).putString(anyString(), anyString());

        // SharedPreferencesEditor.remove
        doAnswer(invocation -> {
            String key = (String) invocation.getArgument(0);
            mSharedPreferencesContent.remove(key);
            return invocation.getMock();
        }).when(mMockSharedPreferencesEditor).remove(anyString());

        // SharedPreferences.getInt
        doAnswer(invocation -> {
            String key = (String) invocation.getArgument(0);
            int defaultValue = (Integer) invocation.getArgument(1);
            if (KEY_NUM_ACCOUNT_KEYS.equals(key)) {
                return mSharedPreferencesContentCount;
            }
            return defaultValue;
        }).when(mMockSharedPreferences).getInt(anyString(), anyInt());

        // SharedPreferences.getString
        doAnswer(invocation -> {
            String key = (String) invocation.getArgument(0);
            String defaultValue = (String) invocation.getArgument(1);
            return mSharedPreferencesContent.getOrDefault(key, defaultValue);
        }).when(mMockSharedPreferences).getString(anyString(), nullable(String.class));
    }

    private void setModelId(int modelId) {
        when(mMockResources.getInteger(anyInt())).thenReturn(modelId);
    }

    private void setPrivateKey(String keyBase64) {
        when(mMockResources.getString(anyInt())).thenReturn(keyBase64);
    }

    private void setAutomaticAcceptance(boolean shouldAcceptAutomatically) {
        when(mMockResources.getBoolean(anyInt())).thenReturn(shouldAcceptAutomatically);
    }

    private void createProviderUnderTest() {
        mFastPairProvider = new FastPairProvider(mMockContext);
        mAdvertiserCallbacks = mFastPairProvider.mAdvertiserCallbacks;
        mGattServerCallbacks = mFastPairProvider.mGattServerCallbacks;
    }

    private void startFastPairProvider() {
        setModelId(TEST_MODEL_ID);
        setPrivateKey(TEST_PRIVATE_KEY_BASE64);
        createProviderUnderTest();
        assertThat(mFastPairProvider.isEnabled()).isTrue();
        assertThat(mFastPairProvider.isStarted()).isFalse();
        mFastPairProvider.start();
        assertThat(mFastPairProvider.isStarted()).isTrue();

        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(), any());
        mReceiver = mBroadcastReceiverCaptor.getValue();
        clearInvocations(mMockContext);
    }

    private void sendAdapterStateChange(int newState, int fromState) {
        when(mMockBluetoothAdapter.getState()).thenReturn(newState);
        assertThat(mReceiver).isNotNull();
        Intent intent = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);
        intent.putExtra(BluetoothAdapter.EXTRA_STATE, newState);
        intent.putExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, fromState);
        mReceiver.onReceive(mMockContext, intent);
    }

    private void sendUserUnlocked() {
        assertThat(mReceiver).isNotNull();
        Intent intent = new Intent(Intent.ACTION_USER_UNLOCKED);
        mReceiver.onReceive(mMockContext, intent);
    }

    private void sendScanModeChange(int mode) {
        assertThat(mReceiver).isNotNull();
        Intent intent = new Intent(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        intent.putExtra(BluetoothAdapter.EXTRA_SCAN_MODE, mode);
        mReceiver.onReceive(mMockContext, intent);
    }

    private void sendAdvertisingStateChange(boolean isAdvertising, int state) {
        assertThat(mAdvertisingSetCallback).isNotNull();
        if (isAdvertising) {
            mAdvertisingSetCallback
                    .onAdvertisingSetStarted(mMockAdvertisingSet, TEST_TX_POWER, state);
        } else {
            mAdvertisingSetCallback.onAdvertisingSetStopped(mMockAdvertisingSet);
        }
    }

    private void verifyAdvertisementData(byte[] data) {
        verify(mMockBluetoothAdapter, timeout(ADVERTISING_EVENT_TIMEOUT_MS))
                .getBluetoothLeAdvertiser();
        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .addServiceUuid(SERVICE_UUID)
                .addServiceData(SERVICE_UUID, data)
                .setIncludeTxPowerLevel(true)
                .build();
        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_TIMEOUT_MS))
                .startAdvertisingSet(any(), eq(advertiseData), any(), any(), any(), any());
    }

    @Test
    public void testStartNoModelId_fastPairNotStarted() {
        setModelId(TEST_EMPTY_MODEL_ID);
        setPrivateKey(TEST_PRIVATE_KEY_BASE64);
        createProviderUnderTest();
        assertThat(mFastPairProvider.isEnabled()).isFalse();
        assertThat(mFastPairProvider.isStarted()).isFalse();
        mFastPairProvider.start();
        assertThat(mFastPairProvider.isStarted()).isFalse();
    }

    @Test
    public void testStartNoPrivateKey_fastPairNotStarted() {
        setModelId(TEST_MODEL_ID);
        setPrivateKey(TEST_EMPTY_PRIVATE_KEY);
        createProviderUnderTest();
        assertThat(mFastPairProvider.isEnabled()).isFalse();
        assertThat(mFastPairProvider.isStarted()).isFalse();
        mFastPairProvider.start();
        assertThat(mFastPairProvider.isStarted()).isFalse();
    }

    @Test
    public void testStartNoModelIdAndNoPrivateKey_fastPairNotStarted() {
        setModelId(TEST_EMPTY_MODEL_ID);
        setPrivateKey(TEST_EMPTY_PRIVATE_KEY);
        createProviderUnderTest();
        assertThat(mFastPairProvider.isEnabled()).isFalse();
        assertThat(mFastPairProvider.isStarted()).isFalse();
        mFastPairProvider.start();
        assertThat(mFastPairProvider.isStarted()).isFalse();
    }

    @Test
    public void testStart_fastPairStarted() {
        startFastPairProvider();
    }

    @Test
    public void testStartWhileStarted_doNothing() {
        startFastPairProvider();
        mFastPairProvider.start();
        assertThat(mFastPairProvider.isStarted()).isTrue();
        verifyNoMoreInteractions(mMockContext);
    }

    @Test
    public void testStopWhileEnabledAndStopped_doNothing() {
        setModelId(TEST_MODEL_ID);
        setPrivateKey(TEST_PRIVATE_KEY_BASE64);
        createProviderUnderTest();
        clearInvocations(mMockContext);
        assertThat(mFastPairProvider.isEnabled()).isTrue();
        assertThat(mFastPairProvider.isStarted()).isFalse();
        mFastPairProvider.stop();
        assertThat(mFastPairProvider.isStarted()).isFalse();
        verifyNoMoreInteractions(mMockContext);
    }

    @Test
    public void testStopWhileStarted_fastPairStopped() {
        startFastPairProvider();
        mFastPairProvider.stop();
        assertThat(mFastPairGattService).isNull();
        assertThat(mAdvertisingSetCallback).isNull();
        assertThat(mFastPairProvider.isStarted()).isFalse();
        assertThat(mFastPairProvider.isEnabled()).isTrue();
    }

    @Test
    public void testReceiveUserUnlocked_storageLoaded() {
        startFastPairProvider();
        sendUserUnlocked();
        // Verify *something* got the key count, which signals an attempt to load.
        // Full loading functionality tested in FastPairAccountKeyStorageTest
        verify(mMockSharedPreferences, atLeastOnce()).getInt(anyString(), anyInt());
    }

    @Test
    public void testReceiveAdapterOn_startGattService() {
        startFastPairProvider();
        sendAdapterStateChange(BluetoothAdapter.STATE_TURNING_ON, BluetoothAdapter.STATE_OFF);
        sendAdapterStateChange(BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_TURNING_ON);
        verify(mMockBluetoothGattServer).addService(any());
    }

    @Test
    public void testReceiveAdapterOff_stopGattServer() {
        testReceiveAdapterOn_startGattService();
        sendAdapterStateChange(BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_ON);
        sendAdapterStateChange(BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF);
        verify(mMockBluetoothGattServer).removeService(any());
    }

    @Test
    public void testReceiveScanModeConnectableDiscoverable_advertiseModelId() {
        startFastPairProvider();
        when(mMockBluetoothAdapter.isDiscovering()).thenReturn(true);
        sendScanModeChange(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
        verifyAdvertisementData(TEST_MODEL_ID_ADVERTISEMENT);
    }

    @Test
    public void testReceiveScanModeConnectableDiscoverableNotDiscovering_advertisingStopped() {
        testReceiveScanModeConnectableAdapterOn_advertiseAccountKeyFilter();
        clearInvocations(mMockBluetoothLeAdvertiser);
        sendAdvertisingStateChange(true, AdvertisingSetCallback.ADVERTISE_SUCCESS);
        when(mMockBluetoothAdapter.isDiscovering()).thenReturn(false);
        sendScanModeChange(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_TIMEOUT_MS))
                .stopAdvertisingSet(any());
        verify(mMockBluetoothLeAdvertiser, never())
                .startAdvertisingSet(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testReceiveScanModeConnectableAdapterOn_advertiseAccountKeyFilter() {
        startFastPairProvider();
        sendAdapterStateChange(BluetoothAdapter.STATE_TURNING_ON, BluetoothAdapter.STATE_OFF);
        sendAdapterStateChange(BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_TURNING_ON);
        sendScanModeChange(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        verifyAdvertisementData(TEST_ACCOUNT_KEY_FILTER_ADVERTISEMENT);
    }

    @Test
    public void testReceiveScanModeConnectableAdapterOff_noAdvertisingChange() {
        startFastPairProvider();
        sendScanModeChange(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        verify(mMockBluetoothLeAdvertiser, never())
                .startAdvertisingSet(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testReceiveScanModeNoneAdapterOff_noAdvertisingChange() {
        startFastPairProvider();
        sendScanModeChange(BluetoothAdapter.SCAN_MODE_NONE);
        verify(mMockBluetoothLeAdvertiser, never())
                .startAdvertisingSet(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testReceiveScanModeNoneAdapterOn_advertisingAccountKeyFilter() {
        startFastPairProvider();
        sendAdapterStateChange(BluetoothAdapter.STATE_TURNING_ON, BluetoothAdapter.STATE_OFF);
        sendAdapterStateChange(BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_TURNING_ON);
        sendScanModeChange(BluetoothAdapter.SCAN_MODE_NONE);
        verifyAdvertisementData(TEST_ACCOUNT_KEY_FILTER_ADVERTISEMENT);
    }
}
