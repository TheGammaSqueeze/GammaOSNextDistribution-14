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

import static android.car.test.mocks.AndroidMockitoHelper.mockCarGetPlatformVersion;

import static com.android.car.bluetooth.FastPairAccountKeyStorage.AccountKey;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.when;

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
import android.content.Context;
import android.os.Looper;
import android.os.ParcelUuid;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link FastPairAdvertiser}
 *
 * Run: atest FastPairAdvertiserTest
 */
@RunWith(MockitoJUnitRunner.class)
public class FastPairAdvertiserTest {
    public static final ParcelUuid SERVICE_UUID = ParcelUuid
            .fromString("0000FE2C-0000-1000-8000-00805f9b34fb");

    private static final int TEST_MODEL_ID = 0x112233;
    private static final byte[] TEST_MODEL_ID_DATA = new byte[]{0x11, 0x22, 0x33};
    private static final int MODEL_ID_ADVERTISING_INTERVAL =
            AdvertisingSetParameters.INTERVAL_LOW;

    private static final byte[] TEST_ACCOUNT_KEY_1 = new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66,
            0x77, (byte) 0x88, (byte) 0x99, 0x00, (byte) 0xAA, (byte) 0xBB, (byte) 0xCC,
            (byte) 0xDD, (byte) 0xEE, (byte) 0xFF};
    private static final byte[] TEST_ACCOUNT_KEY_2 = new byte[]{0x11, 0x11, 0x22, 0x22, 0x33, 0x33,
            0x44, 0x44, 0x55, 0x55, 0x66, 0x66, 0x77, 0x77, (byte) 0x88, (byte) 0x88};
    private static final List<AccountKey> TEST_ACCOUNT_KEYS = new ArrayList<AccountKey>(List.of(
            new AccountKey(TEST_ACCOUNT_KEY_1),
            new AccountKey(TEST_ACCOUNT_KEY_2)
    ));
    private static final List<AccountKey> TEST_EMPTY_ACCOUNT_KEYS = new ArrayList<>();

    static final byte TEST_SALT = (byte) 0x00;
    private static final byte[] TEST_ACCOUNT_KEY_FILTER_DATA_NO_KEYS = new byte[]{0x00, 0x00};
    private static final int TEST_ACCOUNT_KEY_FILTER_DATA_WITH_KEYS_LENGTH = 9;
    private static final byte TEST_ACCOUNT_KEY_FILTER_ADVERTISEMENT_RESERVED_BYTE = 0x00;
    private static final byte TEST_ACCOUNT_KEY_FILTER_ADVERTISEMENT_FILTER_FLAGS_BYTE = (byte) 0x50;
    private static final byte[] TEST_ACCOUNT_KEY_FILTER_ADVERTISEMENT_WITH_KEYS_FILTER_BYTES =
            new byte[]{(byte) 0xC3, 0x15, 0x22, 0x08, 0x3A};
    private static final byte TEST_ACCOUNT_KEY_FILTER_ADVERTISEMENT_SALT_FLAGS_BYTE = 0x11;
    private static final int ACCOUNT_KEY_FILTER_ADVERTISING_INTERVAL =
            AdvertisingSetParameters.INTERVAL_MEDIUM;

    private static final int ADVERTISING_EVENT_SETTLE_MS = 3000;
    private static final int ADVERTISING_EVENT_TIMEOUT_MS = 4500;
    private static final int ADVERTISING_STATE_CHANGE_MS = 150;

    MockitoSession mMockitoSession;

    @Mock Context mMockContext;
    @Mock BluetoothAdapter mMockBluetoothAdapter;
    @Mock BluetoothManager mMockBluetoothManager;
    @Mock BluetoothDevice mMockBluetoothDevice;
    @Mock BluetoothLeAdvertiser mMockBluetoothLeAdvertiser;
    @Mock AdvertisingSet mMockAdvertisingSet;

    @Captor ArgumentCaptor<AdvertisingSetCallback> mAdvertisingSetCallbackCaptor;
    @Captor ArgumentCaptor<AdvertisingSetParameters> mAdvertisingSetParametersCaptor;
    @Captor ArgumentCaptor<AdvertiseData> mAdvertiseDataCaptor;

    private FastPairAdvertiser mFastPairAdvertiser;
    private final FastPairAdvertiser.Callbacks mCallback = new FastPairAdvertiser.Callbacks() {
        @Override
        public void onRpaUpdated(BluetoothDevice device) {
            // TODO(196233989): Add tests for this when the API becomes available and the code can
            // be uncommented.
        }
    };

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

        when(mMockContext.getSystemService(BluetoothManager.class))
                .thenReturn(mMockBluetoothManager);
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeAdvertiser())
                .thenReturn(mMockBluetoothLeAdvertiser);

        when(mMockBluetoothAdapter.getRemoteDevice(any(String.class)))
                .thenReturn(mMockBluetoothDevice);
        when(mMockBluetoothAdapter.getRemoteDevice(any(byte[].class)))
                .thenReturn(mMockBluetoothDevice);

        mAdvertisingSetCallbackCaptor = ArgumentCaptor.forClass(AdvertisingSetCallback.class);
        mAdvertiseDataCaptor = ArgumentCaptor.forClass(AdvertiseData.class);
        mAdvertisingSetParametersCaptor = ArgumentCaptor.forClass(AdvertisingSetParameters.class);

        mMockitoSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.WARN)
                .spyStatic(BluetoothAdapter.class)
                .spyStatic(Car.class)
                .spyStatic(AdvertisingSetCallbackHelper.class)
                .spyStatic(AdvertisingSetHelper.class)
                .startMocking();

        Looper looper = Looper.myLooper();
        if (looper == null) {
            Looper.prepare();
        }

        mFastPairAdvertiser = new FastPairAdvertiser(mMockContext);
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

    private void waitForAdvertisingHandlerToSettle() {
        // TODO (243518804): Remove the need for this by adding a way to wait on state transitions
        try {
            Thread.sleep(ADVERTISING_EVENT_SETTLE_MS);
        } catch (InterruptedException e) {
            // pass
        }
    }

    private void waitForAdvertisingHandlerStateChange() {
        // TODO (243518804): Remove the need for this by adding a way to wait on state transitions
        try {
            Thread.sleep(ADVERTISING_STATE_CHANGE_MS);
        } catch (InterruptedException e) {
            // pass
        }
    }

    private void assertAdvertisingParameters(AdvertisingSetParameters params, int interval) {
        assertThat(params.isLegacy()).isTrue();
        assertThat(params.isScannable()).isTrue();
        assertThat(params.isConnectable()).isTrue();
        assertThat(params.getInterval()).isEqualTo(interval);
    }

    private void assertAdvertisingData(AdvertiseData actual, byte[] data) {
        AdvertiseData expected = new AdvertiseData.Builder()
                .addServiceUuid(SERVICE_UUID)
                .addServiceData(SERVICE_UUID, data)
                .setIncludeTxPowerLevel(true)
                .build();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testAdvertiseModelIdFromStopped_advertisingSucceeds() {
        mFastPairAdvertiser.advertiseModelId(TEST_MODEL_ID, mCallback);

        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_SETTLE_MS))
                .startAdvertisingSet(mAdvertisingSetParametersCaptor.capture(),
                        mAdvertiseDataCaptor.capture(), any(), any(), any(),
                        mAdvertisingSetCallbackCaptor.capture());

        waitForAdvertisingHandlerStateChange();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STARTING);

        mAdvertisingSetCallbackCaptor.getValue().onAdvertisingSetStarted(mMockAdvertisingSet,
                mAdvertisingSetParametersCaptor.getValue().getTxPowerLevel(),
                AdvertisingSetCallback.ADVERTISE_SUCCESS);
        waitForAdvertisingHandlerToSettle();

        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STARTED);
        assertThat(mFastPairAdvertiser.isAdvertising()).isTrue();
        assertAdvertisingParameters(mAdvertisingSetParametersCaptor.getValue(),
                MODEL_ID_ADVERTISING_INTERVAL);
        assertAdvertisingData(mAdvertiseDataCaptor.getValue(), TEST_MODEL_ID_DATA);
    }

    @Test
    public void testAdvertiseModelIdWhileStarted_doNothing() {
        testAdvertiseModelIdFromStopped_advertisingSucceeds();
        clearInvocations(mMockBluetoothLeAdvertiser);
        mFastPairAdvertiser.advertiseModelId(TEST_MODEL_ID, mCallback);
        verify(mMockBluetoothLeAdvertiser, after(ADVERTISING_EVENT_SETTLE_MS).never())
                .startAdvertisingSet(any(), any(), any(), any(), any(), any());
        verify(mMockBluetoothLeAdvertiser, never()).stopAdvertisingSet(any());
        waitForAdvertisingHandlerStateChange();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STARTED);
        assertThat(mFastPairAdvertiser.isAdvertising()).isTrue();
    }

    @Test
    public void testAdvertiseAccountKeyFilterNoKeys_advertisingSucceeds() {
        mFastPairAdvertiser.advertiseAccountKeys(TEST_EMPTY_ACCOUNT_KEYS, mCallback);

        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_SETTLE_MS))
                .startAdvertisingSet(mAdvertisingSetParametersCaptor.capture(),
                        mAdvertiseDataCaptor.capture(), any(), any(), any(),
                        mAdvertisingSetCallbackCaptor.capture());

        mAdvertisingSetCallbackCaptor.getValue().onAdvertisingSetStarted(mMockAdvertisingSet,
                mAdvertisingSetParametersCaptor.getValue().getTxPowerLevel(),
                AdvertisingSetCallback.ADVERTISE_SUCCESS);
        waitForAdvertisingHandlerToSettle();

        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STARTED);
        assertThat(mFastPairAdvertiser.isAdvertising()).isTrue();
        assertAdvertisingParameters(mAdvertisingSetParametersCaptor.getValue(),
                ACCOUNT_KEY_FILTER_ADVERTISING_INTERVAL);
        assertAdvertisingData(mAdvertiseDataCaptor.getValue(),
                TEST_ACCOUNT_KEY_FILTER_DATA_NO_KEYS);
    }

    @Test
    public void testCreateAccountKeyFilterWithKeys_returnsBytes() {
        byte[] filter = mFastPairAdvertiser.getAccountKeyFilter(TEST_ACCOUNT_KEYS, TEST_SALT);
        assertThat(filter).isEqualTo(TEST_ACCOUNT_KEY_FILTER_ADVERTISEMENT_WITH_KEYS_FILTER_BYTES);
    }

    @Test
    public void testCreateAccountKeyFilterNoKeys_returnsNull() {
        byte[] filter = mFastPairAdvertiser.getAccountKeyFilter(TEST_EMPTY_ACCOUNT_KEYS, TEST_SALT);
        assertThat(filter).isEqualTo(null);
    }

    @Test
    public void testCreateAccountKeyFilterNullKeys_returnsNull() {
        byte[] filter = mFastPairAdvertiser.getAccountKeyFilter(null, TEST_SALT);
        assertThat(filter).isEqualTo(null);
    }

    @Test
    public void testAdvertiseAccountKeyFilterWithKeys_advertisingSucceeds() {
        mFastPairAdvertiser.advertiseAccountKeys(TEST_ACCOUNT_KEYS, mCallback);

        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_SETTLE_MS))
                .startAdvertisingSet(mAdvertisingSetParametersCaptor.capture(),
                        mAdvertiseDataCaptor.capture(), any(), any(), any(),
                        mAdvertisingSetCallbackCaptor.capture());

        waitForAdvertisingHandlerStateChange();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STARTING);

        mAdvertisingSetCallbackCaptor.getValue().onAdvertisingSetStarted(mMockAdvertisingSet,
                mAdvertisingSetParametersCaptor.getValue().getTxPowerLevel(),
                AdvertisingSetCallback.ADVERTISE_SUCCESS);
        waitForAdvertisingHandlerToSettle();

        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STARTED);
        assertThat(mFastPairAdvertiser.isAdvertising()).isTrue();
        assertAdvertisingParameters(mAdvertisingSetParametersCaptor.getValue(),
                ACCOUNT_KEY_FILTER_ADVERTISING_INTERVAL);
        AdvertiseData actual = mAdvertiseDataCaptor.getValue();

        // The filter created relies on the salt used, which is random. We cannot mock that, so
        // instead we'll check the other parts of the packet that matter and test the filter
        // creation itself in other tests
        assertThat(actual).isNotNull();
        Map<ParcelUuid, byte[]> actualServiceData = actual.getServiceData();
        assertThat(actualServiceData).isNotNull();
        byte[] actualData = actualServiceData.get(SERVICE_UUID);

        int actualSize = actualData.length;
        assertThat(actualData).isNotNull();
        assertThat(actualSize).isEqualTo(TEST_ACCOUNT_KEY_FILTER_DATA_WITH_KEYS_LENGTH);
        assertThat(actualData[0]).isEqualTo(TEST_ACCOUNT_KEY_FILTER_ADVERTISEMENT_RESERVED_BYTE);
        assertThat(actualData[1])
                .isEqualTo(TEST_ACCOUNT_KEY_FILTER_ADVERTISEMENT_FILTER_FLAGS_BYTE);
        assertThat(actualData[actualSize - 2])
                .isEqualTo(TEST_ACCOUNT_KEY_FILTER_ADVERTISEMENT_SALT_FLAGS_BYTE);
        byte salt = actualData[actualSize - 1];
        byte[] filter = mFastPairAdvertiser.getAccountKeyFilter(TEST_ACCOUNT_KEYS, salt);
        assertThat(Arrays.copyOfRange(actualData, 2, 7)).isEqualTo(filter);
    }

    @Test
    public void testAdvertiseAccountKeyFilterWhileStarted_doNothing() {
        testAdvertiseModelIdFromStopped_advertisingSucceeds();
        clearInvocations(mMockBluetoothLeAdvertiser);
        mFastPairAdvertiser.advertiseAccountKeys(TEST_ACCOUNT_KEYS, mCallback);
        verify(mMockBluetoothLeAdvertiser, after(ADVERTISING_EVENT_SETTLE_MS).never())
                .startAdvertisingSet(any(), any(), any(), any(), any(), any());
        waitForAdvertisingHandlerStateChange();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STARTED);
        assertThat(mFastPairAdvertiser.isAdvertising()).isTrue();
    }

    @Test
    public void testAdvertiseNewDataWhileStarted_doNothing() {
        testAdvertiseModelIdFromStopped_advertisingSucceeds();
        clearInvocations(mMockBluetoothLeAdvertiser);
        mFastPairAdvertiser.advertiseModelId(TEST_MODEL_ID, mCallback);
        verify(mMockBluetoothLeAdvertiser, after(ADVERTISING_EVENT_SETTLE_MS).never())
                .startAdvertisingSet(any(), any(), any(), any(), any(), any());
        verify(mMockBluetoothLeAdvertiser, never()).stopAdvertisingSet(any());
        waitForAdvertisingHandlerStateChange();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STARTED);
        assertThat(mFastPairAdvertiser.isAdvertising()).isTrue();
    }

    @Test
    public void testFailToGetAdvertiserOnStart_doesNotAdvertise() {
        when(mMockBluetoothAdapter.getBluetoothLeAdvertiser()).thenReturn(null);
        mFastPairAdvertiser.advertiseModelId(TEST_MODEL_ID, mCallback);
        waitForAdvertisingHandlerToSettle();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STOPPED);
        assertThat(mFastPairAdvertiser.isAdvertising()).isFalse();
    }

    @Test
    public void testAdvertisingStartCallbackUnsuccessful_advertisingStops() {
        mFastPairAdvertiser.advertiseModelId(TEST_MODEL_ID, mCallback);

        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_SETTLE_MS))
                .startAdvertisingSet(mAdvertisingSetParametersCaptor.capture(),
                        mAdvertiseDataCaptor.capture(), any(), any(), any(),
                        mAdvertisingSetCallbackCaptor.capture());

        waitForAdvertisingHandlerStateChange();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STARTING);

        mAdvertisingSetCallbackCaptor.getValue().onAdvertisingSetStarted(mMockAdvertisingSet,
                0, AdvertisingSetCallback.ADVERTISE_FAILED_INTERNAL_ERROR);
        waitForAdvertisingHandlerToSettle();

        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STOPPED);
        assertThat(mFastPairAdvertiser.isAdvertising()).isFalse();
    }

    @Test
    public void testAdvertisingStartCallbackNullSet_advertisingStops() {
        mFastPairAdvertiser.advertiseModelId(TEST_MODEL_ID, mCallback);

        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_SETTLE_MS))
                .startAdvertisingSet(mAdvertisingSetParametersCaptor.capture(),
                        mAdvertiseDataCaptor.capture(), any(), any(), any(),
                        mAdvertisingSetCallbackCaptor.capture());

        waitForAdvertisingHandlerStateChange();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STARTING);

        mAdvertisingSetCallbackCaptor.getValue().onAdvertisingSetStarted(null,
                0, AdvertisingSetCallback.ADVERTISE_SUCCESS);
        waitForAdvertisingHandlerToSettle();

        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STOPPED);
        assertThat(mFastPairAdvertiser.isAdvertising()).isFalse();
    }

    @Test
    public void testAdvertisingStartTimeout_doesNotAdvertise() {
        mFastPairAdvertiser.advertiseModelId(TEST_MODEL_ID, mCallback);
        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_SETTLE_MS))
                .startAdvertisingSet(any(), any(), any(), any(), any(), any());
        waitForAdvertisingHandlerStateChange();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STARTING);

        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_TIMEOUT_MS))
                .stopAdvertisingSet(any());
        waitForAdvertisingHandlerStateChange();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STOPPED);
        assertThat(mFastPairAdvertiser.isAdvertising()).isFalse();
    }

    @Test
    public void testStopAdvertising() {
        testAdvertiseModelIdFromStopped_advertisingSucceeds();
        clearInvocations(mMockBluetoothLeAdvertiser);
        mFastPairAdvertiser.stopAdvertising();
        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_SETTLE_MS))
                .stopAdvertisingSet(mAdvertisingSetCallbackCaptor.capture());
        waitForAdvertisingHandlerStateChange();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STOPPING);
        mAdvertisingSetCallbackCaptor.getValue().onAdvertisingSetStopped(mMockAdvertisingSet);
        waitForAdvertisingHandlerToSettle();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STOPPED);
        assertThat(mFastPairAdvertiser.isAdvertising()).isFalse();
    }

    @Test
    public void testStopAdvertisingWhileStopped() {
        testStopAdvertising();
        clearInvocations(mMockBluetoothLeAdvertiser);
        mFastPairAdvertiser.stopAdvertising();
        waitForAdvertisingHandlerToSettle();
        verify(mMockBluetoothLeAdvertiser, after(ADVERTISING_EVENT_SETTLE_MS).never())
                .stopAdvertisingSet(any());
        assertThat(mFastPairAdvertiser.isAdvertising()).isFalse();
    }

    @Test
    public void testAdvertisingStartWhileStopping_startProcessed() {
        testAdvertiseModelIdFromStopped_advertisingSucceeds();
        clearInvocations(mMockBluetoothLeAdvertiser);

        mFastPairAdvertiser.stopAdvertising();
        mFastPairAdvertiser.advertiseModelId(TEST_MODEL_ID, mCallback);

        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_SETTLE_MS))
                .stopAdvertisingSet(mAdvertisingSetCallbackCaptor.capture());
        waitForAdvertisingHandlerStateChange();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STOPPING);

        mAdvertisingSetCallbackCaptor.getValue().onAdvertisingSetStopped(mMockAdvertisingSet);

        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_SETTLE_MS))
                .startAdvertisingSet(mAdvertisingSetParametersCaptor.capture(),
                        mAdvertiseDataCaptor.capture(), any(), any(), any(),
                        mAdvertisingSetCallbackCaptor.capture());
    }

    @Test
    public void testAdvertisingStopWhileStarting_stopProcessed() {
        mFastPairAdvertiser.advertiseModelId(TEST_MODEL_ID, mCallback);

        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_SETTLE_MS))
                .startAdvertisingSet(mAdvertisingSetParametersCaptor.capture(),
                        mAdvertiseDataCaptor.capture(), any(), any(), any(),
                        mAdvertisingSetCallbackCaptor.capture());

        waitForAdvertisingHandlerStateChange();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STARTING);

        mFastPairAdvertiser.stopAdvertising();

        mAdvertisingSetCallbackCaptor.getValue().onAdvertisingSetStarted(mMockAdvertisingSet,
                mAdvertisingSetParametersCaptor.getValue().getTxPowerLevel(),
                AdvertisingSetCallback.ADVERTISE_SUCCESS);

        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_SETTLE_MS))
                .stopAdvertisingSet(any());
    }

    @Test
    public void testAdvertisingStartWhileStoppingTimeout_startProcessed() {
        testAdvertiseModelIdFromStopped_advertisingSucceeds();
        clearInvocations(mMockBluetoothLeAdvertiser);

        mFastPairAdvertiser.stopAdvertising();
        mFastPairAdvertiser.advertiseModelId(TEST_MODEL_ID, mCallback);

        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_SETTLE_MS))
                .stopAdvertisingSet(mAdvertisingSetCallbackCaptor.capture());
        waitForAdvertisingHandlerStateChange();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STOPPING);

        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_TIMEOUT_MS))
                .startAdvertisingSet(mAdvertisingSetParametersCaptor.capture(),
                        mAdvertiseDataCaptor.capture(), any(), any(), any(),
                        mAdvertisingSetCallbackCaptor.capture());
    }

    @Test
    public void testAdvertisingStopWhileStartingTimeout_stopProcessed() {
        mFastPairAdvertiser.advertiseModelId(TEST_MODEL_ID, mCallback);

        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_SETTLE_MS))
                .startAdvertisingSet(mAdvertisingSetParametersCaptor.capture(),
                        mAdvertiseDataCaptor.capture(), any(), any(), any(),
                        mAdvertisingSetCallbackCaptor.capture());
        waitForAdvertisingHandlerStateChange();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STARTING);

        mFastPairAdvertiser.stopAdvertising();

        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_TIMEOUT_MS))
                .stopAdvertisingSet(any());
    }

    @Test
    public void testAdvertisingStopTimeoutNothingQueue_advertisingStateStopped() {
        testAdvertiseModelIdFromStopped_advertisingSucceeds();
        clearInvocations(mMockBluetoothLeAdvertiser);

        mFastPairAdvertiser.stopAdvertising();
        verify(mMockBluetoothLeAdvertiser, timeout(ADVERTISING_EVENT_SETTLE_MS))
                .stopAdvertisingSet(mAdvertisingSetCallbackCaptor.capture());
        waitForAdvertisingHandlerStateChange();
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STOPPING);
        clearInvocations(mMockBluetoothLeAdvertiser);

        verify(mMockBluetoothLeAdvertiser, after(ADVERTISING_EVENT_TIMEOUT_MS).never())
                .stopAdvertisingSet(any());
        verify(mMockBluetoothLeAdvertiser, never())
                .startAdvertisingSet(any(), any(), any(), any(), any(), any());
        assertThat(mFastPairAdvertiser.getAdvertisingState())
                .isEqualTo(FastPairAdvertiser.STATE_STOPPED);
    }

    /**
     * {@link AdvertisingSetCallbackHelper} and {@link AdvertisingSetHelper} were introduced in
     * TM-QPR-1 (maj=33, min=1) to help with {@link FastPairAdvertiser} hidden API usages. A
     * version check was added to the constructor of {@link FastPairAdvertiser} to ensure backwards
     * compatibility with respect to the availability of these helper classes. One way to test
     * which branch the check took is to check whether
     * {@link AdvertisingSetCallbackHelper#createRealCallbackFromProxy} was invoked or not.
     */
    @Test
    public void testFPAdvertiserBackCompat_tiramisu1_createRealCallbackFromProxyInvoked() {
        mockCarGetPlatformVersion(PlatformVersion.VERSION_CODES.TIRAMISU_1);
        // reset invocation count
        clearInvocations(staticMockMarker(AdvertisingSetCallbackHelper.class));

        // version check lies in constructor
        new FastPairAdvertiser(mMockContext);

        verify(() -> AdvertisingSetCallbackHelper.createRealCallbackFromProxy(any()));
    }

    /**
     * {@link AdvertisingSetCallbackHelper} and {@link AdvertisingSetHelper} were introduced in
     * TM-QPR-1 (maj=33, min=1) to help with {@link FastPairAdvertiser} hidden API usages. A
     * version check was added to the constructor of {@link FastPairAdvertiser} to ensure backwards
     * compatibility with respect to the availability of these helper classes. One way to test
     * which branch the check took is to check whether
     * {@link AdvertisingSetCallbackHelper#createRealCallbackFromProxy} was invoked or not.
     */
    @Test
    public void testFPAdvertiserBackCompat_tiramisu0_createRealCallbackFromProxyNotInvoked() {
        mockCarGetPlatformVersion(PlatformVersion.VERSION_CODES.TIRAMISU_0);
        // reset invocation count
        clearInvocations(staticMockMarker(AdvertisingSetCallbackHelper.class));

        // version check lies in constructor
        new FastPairAdvertiser(mMockContext);

        verify(() -> AdvertisingSetCallbackHelper.createRealCallbackFromProxy(any()), never());
    }
}
