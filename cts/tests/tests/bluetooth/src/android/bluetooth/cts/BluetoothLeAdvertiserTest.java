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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_ADVERTISE;
import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.bluetooth.le.AdvertisingSetCallback.ADVERTISE_SUCCESS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class BluetoothLeAdvertiserTest {
    private static final int TIMEOUT_MS = 5000;
    private static final AdvertisingSetParameters ADVERTISING_SET_PARAMETERS =
            new AdvertisingSetParameters.Builder().setLegacyMode(true).build();

    private static final AdvertisingSetParameters ADVERTISING_SET_USING_NRPA_PARAMETERS =
            new AdvertisingSetParameters.Builder()
                    .setLegacyMode(true)
                    .setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_RANDOM_NON_RESOLVABLE)
                    .build();

    private Context mContext;
    private UiAutomation mUiAutomation;
    private BluetoothManager mManager;
    private BluetoothAdapter mAdapter;
    private BluetoothLeAdvertiser mAdvertiser;
    private TestAdvertisingSetCallback mCallback;


    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE);

        mManager = mContext.getSystemService(BluetoothManager.class);
        mAdapter = mManager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        mAdvertiser = mAdapter.getBluetoothLeAdvertiser();
        mCallback = new TestAdvertisingSetCallback();
    }

    @After
    public void tearDown() throws Exception {
        if (mAdvertiser != null) {
            mAdvertiser.stopAdvertisingSet(mCallback);
            assertTrue(mCallback.mAdvertisingSetStoppedLatch.await(TIMEOUT_MS,
                    TimeUnit.MILLISECONDS));
            mAdvertiser = null;
            mAdapter = null;
        }

        if (mUiAutomation != null) {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void startAdvertisingSetWithCallbackAndHandler() throws InterruptedException {
        mAdvertiser.startAdvertisingSet(ADVERTISING_SET_PARAMETERS, null, null, null, null,
                mCallback, new Handler(Looper.getMainLooper()));
        assertTrue(mCallback.mAdvertisingSetStartedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mAdvertisingSetStartedStatus.get());
        assertNotNull(mCallback.mAdvertisingSet);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void startAdvertisingSetWithDurationAndCallback() throws InterruptedException {
        mAdvertiser.startAdvertisingSet(ADVERTISING_SET_PARAMETERS, null, null, null, null,
                0, 0, mCallback);
        assertTrue(mCallback.mAdvertisingSetStartedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mAdvertisingSetStartedStatus.get());
        assertNotNull(mCallback.mAdvertisingSet);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void startAdvertisingSetWithDurationCallbackAndHandler()
            throws InterruptedException {
        mAdvertiser.startAdvertisingSet(ADVERTISING_SET_PARAMETERS, null, null, null, null,
                0, 0, mCallback, new Handler(Looper.getMainLooper()));
        assertTrue(mCallback.mAdvertisingSetStartedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mAdvertisingSetStartedStatus.get());
        assertNotNull(mCallback.mAdvertisingSet);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void startAdvertisingSetWithDurationCallbackGattServerAndHandler()
            throws InterruptedException {
        BluetoothGattServer gattServer =
                mManager.openGattServer(mContext, new BluetoothGattServerCallback() {});

        assertThrows("No BLUETOOTH_PRIVILEGED permission", SecurityException.class,
                () -> mAdvertiser.startAdvertisingSet(ADVERTISING_SET_USING_NRPA_PARAMETERS, null, null,
                                null, null, 0, 0, gattServer, mCallback,
                                new Handler(Looper.getMainLooper())));

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_PRIVILEGED);

        mAdvertiser.startAdvertisingSet(ADVERTISING_SET_USING_NRPA_PARAMETERS, null, null,
                null, null, 0, 0, gattServer, mCallback,
                new Handler(Looper.getMainLooper()));
        assertTrue(mCallback.mAdvertisingSetStartedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mAdvertisingSetStartedStatus.get());
        assertNotNull(mCallback.mAdvertisingSet);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getAdvertisingSetId() throws InterruptedException {
        mAdvertiser.startAdvertisingSet(ADVERTISING_SET_PARAMETERS, null, null, null, null, 0, 0,
                mCallback, new Handler(Looper.getMainLooper()));
        mCallback.mAdvertisingSetStartedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertNotNull(mCallback.mAdvertisingSet);
        assertTrue(mCallback.mAdvertisingSet.get().getAdvertiserId() >= 0);
    }

    private static class TestAdvertisingSetCallback extends AdvertisingSetCallback {
        public CountDownLatch mAdvertisingSetStartedLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingEnabledLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingDisabledLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingSetStoppedLatch = new CountDownLatch(1);

        public AtomicInteger mAdvertisingSetStartedStatus = new AtomicInteger();
        public AtomicInteger mAdvertisingEnabledStatus = new AtomicInteger();
        public AtomicInteger mAdvertisingDisabledStatus = new AtomicInteger();

        public AtomicReference<AdvertisingSet> mAdvertisingSet = new AtomicReference();

        @Override
        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower,
                int status) {
            super.onAdvertisingSetStarted(advertisingSet, txPower, status);
            mAdvertisingSetStartedStatus.set(status);
            mAdvertisingSet.set(advertisingSet);
            mAdvertisingSetStartedLatch.countDown();
        }

        @Override
        public void onAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable,
                int status) {
            super.onAdvertisingEnabled(advertisingSet, enable, status);
            if (enable) {
                mAdvertisingEnabledStatus.set(status);
                mAdvertisingEnabledLatch.countDown();
            } else {
                mAdvertisingDisabledStatus.set(status);
                mAdvertisingDisabledLatch.countDown();
            }
        }

        @Override
        public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
            super.onAdvertisingSetStopped(advertisingSet);
            mAdvertisingSetStoppedLatch.countDown();
        }
    }
}
