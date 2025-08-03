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

package android.car.builtin.bluetooth.le;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.RequiresDevice;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Objects;

@RunWith(MockitoJUnitRunner.class)
public final class AdvertisingSetCallbackHelperTest {

    private static final String TAG = AdvertisingSetCallbackHelperTest.class.getSimpleName();

    private AdvertisingSetCallbackHelper.Callback mProxy =
            spy(new AdvertisingSetCallbackHelper.Callback() {});

    @Mock
    private AdvertisingSet mMockExpectedAdvertisingSet;

    private static final int EXPECTED_TX_POWER = 123;
    private static final int EXPECTED_STATUS = -123;
    private static final boolean EXPECTED_ENABLE_FLAG = true;
    private static final int EXPECTED_ADDRESS_TYPE = 314;
    private static final String EXPECTED_ADDRESS = "1600 Amphitheatre Parkway";
    // {@code 1 sec} should be more than enough time for a callback to be invoked.
    private static final int CALLBACK_TIMEOUT_MS = 1000;

    @Test
    public void createRealCallbackFromProxy_delegateOnAdvertisingSetStarted() {
        AdvertisingSetCallback realCallback =
                AdvertisingSetCallbackHelper.createRealCallbackFromProxy(mProxy);

        realCallback.onAdvertisingSetStarted(mMockExpectedAdvertisingSet, EXPECTED_TX_POWER,
                EXPECTED_STATUS);

        verify(mProxy).onAdvertisingSetStarted(mMockExpectedAdvertisingSet, EXPECTED_TX_POWER,
                EXPECTED_STATUS);
    }

    @Test
    public void createRealCallbackFromProxy_delegateOnAdvertisingSetStopped() {
        AdvertisingSetCallback realCallback =
                AdvertisingSetCallbackHelper.createRealCallbackFromProxy(mProxy);

        realCallback.onAdvertisingSetStopped(mMockExpectedAdvertisingSet);

        verify(mProxy).onAdvertisingSetStopped(mMockExpectedAdvertisingSet);
    }

    @Test
    public void createRealCallbackFromProxy_delegateOnAdvertisingEnabled() {
        AdvertisingSetCallback realCallback =
                AdvertisingSetCallbackHelper.createRealCallbackFromProxy(mProxy);

        realCallback.onAdvertisingEnabled(mMockExpectedAdvertisingSet, EXPECTED_ENABLE_FLAG,
                EXPECTED_STATUS);

        verify(mProxy).onAdvertisingEnabled(mMockExpectedAdvertisingSet, EXPECTED_ENABLE_FLAG,
                EXPECTED_STATUS);
    }

    @Test
    public void createRealCallbackFromProxy_delegateOnAdvertisingDataSet() {
        AdvertisingSetCallback realCallback =
                AdvertisingSetCallbackHelper.createRealCallbackFromProxy(mProxy);

        realCallback.onAdvertisingDataSet(mMockExpectedAdvertisingSet, EXPECTED_STATUS);

        verify(mProxy).onAdvertisingDataSet(mMockExpectedAdvertisingSet, EXPECTED_STATUS);
    }

    @Test
    public void createRealCallbackFromProxy_delegateOnScanResponseDataSet() {
        AdvertisingSetCallback realCallback =
                AdvertisingSetCallbackHelper.createRealCallbackFromProxy(mProxy);

        realCallback.onScanResponseDataSet(mMockExpectedAdvertisingSet, EXPECTED_STATUS);

        verify(mProxy).onScanResponseDataSet(mMockExpectedAdvertisingSet, EXPECTED_STATUS);
    }

    @Test
    public void createRealCallbackFromProxy_delegateOnAdvertisingParametersUpdated() {
        AdvertisingSetCallback realCallback =
                AdvertisingSetCallbackHelper.createRealCallbackFromProxy(mProxy);

        realCallback.onAdvertisingParametersUpdated(mMockExpectedAdvertisingSet, EXPECTED_TX_POWER,
                EXPECTED_STATUS);

        verify(mProxy).onAdvertisingParametersUpdated(mMockExpectedAdvertisingSet,
                EXPECTED_TX_POWER, EXPECTED_STATUS);
    }

    @Test
    public void createRealCallbackFromProxy_delegateOnPeriodicAdvertisingParametersUpdated() {
        AdvertisingSetCallback realCallback =
                AdvertisingSetCallbackHelper.createRealCallbackFromProxy(mProxy);

        realCallback.onPeriodicAdvertisingParametersUpdated(mMockExpectedAdvertisingSet,
                EXPECTED_STATUS);

        verify(mProxy).onPeriodicAdvertisingParametersUpdated(mMockExpectedAdvertisingSet,
                EXPECTED_STATUS);
    }

    @Test
    public void createRealCallbackFromProxy_delegateOnPeriodicAdvertisingDataSet() {
        AdvertisingSetCallback realCallback =
                AdvertisingSetCallbackHelper.createRealCallbackFromProxy(mProxy);

        realCallback.onPeriodicAdvertisingDataSet(mMockExpectedAdvertisingSet,
                EXPECTED_STATUS);

        verify(mProxy).onPeriodicAdvertisingDataSet(mMockExpectedAdvertisingSet,
                EXPECTED_STATUS);
    }

    @Test
    public void createRealCallbackFromProxy_delegateOnPeriodicAdvertisingEnabled() {
        AdvertisingSetCallback realCallback =
                AdvertisingSetCallbackHelper.createRealCallbackFromProxy(mProxy);

        realCallback.onPeriodicAdvertisingEnabled(mMockExpectedAdvertisingSet, EXPECTED_ENABLE_FLAG,
                EXPECTED_STATUS);

        verify(mProxy).onPeriodicAdvertisingEnabled(mMockExpectedAdvertisingSet,
                EXPECTED_ENABLE_FLAG, EXPECTED_STATUS);
    }

    @Test
    public void createRealCallbackFromProxy_delegateOnOwnAddressRead() {
        AdvertisingSetCallback realCallback =
                AdvertisingSetCallbackHelper.createRealCallbackFromProxy(mProxy);

        realCallback.onOwnAddressRead(mMockExpectedAdvertisingSet, EXPECTED_ADDRESS_TYPE,
                EXPECTED_ADDRESS);

        verify(mProxy).onOwnAddressRead(mMockExpectedAdvertisingSet, EXPECTED_ADDRESS_TYPE,
                EXPECTED_ADDRESS);
    }

    /**
     * Integration test of {@link AdvertisingSetCallbackHelper#Callback}.
     *   1. Use {@link BluetoothLeAdvertiser#startAdvertisingSet} to create a
     *      {@link AdvertisingSet} and trigger
     *      {@link AdvertisingSetCallback#onAdvertisingSetStarted}. In order to call
     *      {@link BluetoothLeAdvertiser#startAdvertisingSet}, we need to pass in
     *      {@code non-null}:
     *        a. {@link AdvertisingSetParameters} -- will get build error if {@code null}.
     *        b. {@link AdvertisingSetCallback} -- This is how we inject our proxy.
     *   2. In the {@link AdvertisingSetCallback#onAdvertisingSetStarted} callback, invoke
     *      {@link AdvertisingSet#getOwnAddress}. This should then trigger the
     *      {@link AdvertisingSetCallback#onOwnAddressRead} callback.
     *
     * Test start: invoke {@link BluetoothLeAdvertiser#startAdvertisingSet}.
     * Success criteria: {@link AdvertisingSetCallback#onOwnAddressRead} is invoked.
     */
    @Test
    @RequiresDevice
    public void integrationTest_onAdvertisingSetStarted_onOwnAddressRead() {
        Log.d(TAG, "integrationTest_onAdvertisingSetStarted_onOwnAddressRead");

        // Getting the {@link BluetoothLeAdvertiser}
        BluetoothManager bluetoothManager = Objects.requireNonNull(
                InstrumentationRegistry.getTargetContext()
                .getSystemService(BluetoothManager.class));
        BluetoothAdapter adapter = Objects.requireNonNull(bluetoothManager.getAdapter());
        BluetoothLeAdvertiser advertiser = Objects.requireNonNull(
                adapter.getBluetoothLeAdvertiser());

        // Creating {@link AdvertisingSetCallback} for
        // {@link BluetoothLeAdvertiser#startAdvertisingSet} (and its proxy for the test).
        AdvertisingSetCallbackHelper.Callback proxyForOnOwnAddressRead =
                new AdvertisingSetCallbackHelper.Callback() {
            @Override
            public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower,
                    int status) {
                Log.d(TAG, "onAdvertisingSetStarted invoked: advertisingSet="
                        + advertisingSet + ", txPower=" + txPower + ", status=" + status);

                AdvertisingSetHelper.getOwnAddress(advertisingSet);
            }

            @Override
            public void onOwnAddressRead(AdvertisingSet advertisingSet, int addressType,
                    String address) {
                Log.d(TAG, "onOwnAddressRead invoked: advertisingSet="
                        + advertisingSet + ", addressType=" + addressType + ", address="
                        + address);
            }
        };

        AdvertisingSetCallbackHelper.Callback spyCallbackProxy =
                spy(proxyForOnOwnAddressRead);

        AdvertisingSetCallback realCallback =
                AdvertisingSetCallbackHelper.createRealCallbackFromProxy(spyCallbackProxy);

        // Creating {@link AdvertisingSetParameters} for
        // {@link BluetoothLeAdvertiser#startAdvertisingSet}
        AdvertisingSetParameters advertisingSetParameters = new AdvertisingSetParameters.Builder()
                .setConnectable(true)
                .build();

        // Invoking {@link BluetoothLeAdvertiser#startAdvertisingSet} will create an
        // {@link AdvertisingSet} object, and invoke the callback
        // {@link AdvertisingSetCallback#onAdvertisingSetStarted}, from which
        // {@link AdvertisingSet#getOwnAddress} will be invoked, which in turn will invoke the
        // callback {@link AdvertisingSetCallback#onOwnAddressRead}.
        advertiser.startAdvertisingSet(advertisingSetParameters, /* advertiseData */ null,
                /* scanResponse */ null, /* periodicParameters */ null, /* periodicData */ null,
                realCallback);
        Log.d(TAG, "startAdvertisingSet invoked");

        verify(spyCallbackProxy, timeout(CALLBACK_TIMEOUT_MS))
                .onOwnAddressRead(any(), anyInt(), anyString());
    }
}
