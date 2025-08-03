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

package com.android.bluetooth.avrcpcontroller;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AvrcpBipClientTest {
    private static final int TEST_PSM = 1;

    @Rule
    public final ServiceTestRule mBluetoothBrowserMediaServiceTestRule = new ServiceTestRule();

    @Mock private AdapterService mAdapterService;
    @Mock private AvrcpControllerNativeInterface mNativeInterface;

    private BluetoothAdapter mAdapter;
    private BluetoothDevice mTestDevice;
    private AvrcpControllerService mService = null;
    private AvrcpCoverArtManager mArtManager;
    private AvrcpBipClient mClient;

    @Before
    public void setUp() throws Exception {
        Context targetContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);
        AvrcpControllerNativeInterface.setInstance(mNativeInterface);
        mService = new AvrcpControllerService(targetContext, mNativeInterface);
        mService.start();
        final Intent bluetoothBrowserMediaServiceStartIntent =
                TestUtils.prepareIntentToStartBluetoothBrowserMediaService();
        mBluetoothBrowserMediaServiceTestRule.startService(bluetoothBrowserMediaServiceStartIntent);

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTestDevice = mAdapter.getRemoteDevice("00:01:02:03:04:05");

        AvrcpCoverArtManager.Callback callback = (device, event) -> {
        };
        mArtManager = new AvrcpCoverArtManager(mService, callback);

        mClient = new AvrcpBipClient(mTestDevice, TEST_PSM,
                mArtManager.new BipClientCallback(mTestDevice));
    }

    @After
    public void tearDown() throws Exception {
        mService.stop();
        AvrcpControllerNativeInterface.setInstance(null);
        mService = AvrcpControllerService.getAvrcpControllerService();
        assertThat(mService).isNull();
        TestUtils.clearAdapterService(mAdapterService);
        mArtManager.cleanup();
    }

    @Test
    public void constructor() {
        AvrcpBipClient client = new AvrcpBipClient(mTestDevice, TEST_PSM,
                mArtManager.new BipClientCallback(mTestDevice));

        assertThat(client.getL2capPsm()).isEqualTo(TEST_PSM);
    }

    @Test
    public void constructor_withNullDevice() {
        assertThrows(NullPointerException.class, () -> new AvrcpBipClient(null, TEST_PSM,
                mArtManager.new BipClientCallback(mTestDevice)));
    }

    @Test
    public void constructor_withNullCallback() {
        assertThrows(NullPointerException.class, () -> new AvrcpBipClient(mTestDevice, TEST_PSM,
                null));
    }

    @Test
    public void setConnectionState() {
        mClient.setConnectionState(BluetoothProfile.STATE_CONNECTING);

        assertThat(mClient.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
    }

    @Test
    public void getConnectionState() {
        mClient.setConnectionState(BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mClient.getStateName()).isEqualTo("Disconnected");

        mClient.setConnectionState(BluetoothProfile.STATE_CONNECTING);
        assertThat(mClient.getStateName()).isEqualTo("Connecting");

        mClient.setConnectionState(BluetoothProfile.STATE_CONNECTED);
        assertThat(mClient.getStateName()).isEqualTo("Connected");

        mClient.setConnectionState(BluetoothProfile.STATE_DISCONNECTING);
        assertThat(mClient.getStateName()).isEqualTo("Disconnecting");

        int invalidState = 4;
        mClient.setConnectionState(invalidState);
        assertThat(mClient.getStateName()).isEqualTo("Unknown");
    }

    @Test
    public void toString_returnsClientInfo() {
        String expected =
                "<AvrcpBipClient"
                        + (" device=" + mTestDevice)
                        + (" psm=" + TEST_PSM)
                        + (" state=" + mClient.getStateName())
                        + ">";
        assertThat(mClient.toString()).isEqualTo(expected);
    }
}
