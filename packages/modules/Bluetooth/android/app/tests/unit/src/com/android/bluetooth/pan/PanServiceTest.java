/*
 * Copyright 2018 The Android Open Source Project
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
package com.android.bluetooth.pan;

import static android.bluetooth.BluetoothPan.PAN_ROLE_NONE;
import static android.net.TetheringManager.TETHERING_BLUETOOTH;
import static android.net.TetheringManager.TETHER_ERROR_SERVICE_UNAVAIL;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.net.TetheringInterface;
import android.os.UserManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.pan.PanService.BluetoothPanDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class PanServiceTest {
    private static final String REMOTE_DEVICE_ADDRESS = "00:00:00:00:00:00";
    private static final byte[] REMOTE_DEVICE_ADDRESS_AS_ARRAY = new byte[] {0, 0, 0, 0, 0, 0};

    private PanService mService = null;
    private BluetoothAdapter mAdapter = null;
    private BluetoothDevice mRemoteDevice;


    @Mock private AdapterService mAdapterService;
    @Mock private DatabaseManager mDatabaseManager;
    @Mock private PanNativeInterface mNativeInterface;
    @Mock private UserManager mMockUserManager;

    @Before
    public void setUp() throws Exception {
        Context targetContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();
        PanNativeInterface.setInstance(mNativeInterface);
        mService = new PanService(targetContext);
        mService.start();
        mService.setAvailable(true);

        // Try getting the Bluetooth adapter
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertThat(mAdapter).isNotNull();
        mService.mUserManager = mMockUserManager;
        mRemoteDevice = mAdapter.getRemoteDevice(REMOTE_DEVICE_ADDRESS);
    }

    @After
    public void tearDown() throws Exception {
        mService.stop();
        mService.cleanup();
        PanNativeInterface.setInstance(null);
        mService = PanService.getPanService();
        assertThat(mService).isNull();
        TestUtils.clearAdapterService(mAdapterService);
    }

    @Test
    public void initialize() {
        assertThat(PanService.getPanService()).isNotNull();
    }

    @Test
    public void connect_whenGuestUser_returnsFalse() {
        when(mMockUserManager.isGuestUser()).thenReturn(true);
        assertThat(mService.connect(mRemoteDevice)).isFalse();
    }

    @Test
    public void connect_inConnectedState_returnsFalse() {
        when(mMockUserManager.isGuestUser()).thenReturn(false);
        mService.mPanDevices.put(mRemoteDevice, new BluetoothPanDevice(
                BluetoothProfile.STATE_CONNECTED, "iface", PAN_ROLE_NONE, PAN_ROLE_NONE));

        assertThat(mService.connect(mRemoteDevice)).isFalse();
    }

    @Test
    public void connect() {
        when(mMockUserManager.isGuestUser()).thenReturn(false);
        mService.mPanDevices.put(mRemoteDevice, new BluetoothPanDevice(
                BluetoothProfile.STATE_DISCONNECTED, "iface", PAN_ROLE_NONE, PAN_ROLE_NONE));

        assertThat(mService.connect(mRemoteDevice)).isTrue();
    }

    @Test
    public void disconnect_returnsTrue() {
        assertThat(mService.disconnect(mRemoteDevice)).isTrue();
    }

    @Test
    public void convertHalState() {
        assertThat(PanNativeInterface.convertHalState(PanNativeInterface.CONN_STATE_CONNECTED))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);
        assertThat(PanNativeInterface.convertHalState(PanNativeInterface.CONN_STATE_CONNECTING))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);
        assertThat(PanNativeInterface.convertHalState(PanNativeInterface.CONN_STATE_DISCONNECTED))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        assertThat(PanNativeInterface.convertHalState(PanNativeInterface.CONN_STATE_DISCONNECTING))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTING);
        assertThat(PanNativeInterface.convertHalState(-24664)) // illegal value
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void dump() {
        mService.mPanDevices.put(mRemoteDevice, new BluetoothPanDevice(
                BluetoothProfile.STATE_DISCONNECTED, "iface", PAN_ROLE_NONE, PAN_ROLE_NONE));

        mService.dump(new StringBuilder());
    }

    @Test
    public void onConnectStateChanged_doesNotCrash() {
        mService.onConnectStateChanged(REMOTE_DEVICE_ADDRESS_AS_ARRAY, 1, 2, 3, 4);
    }

    @Test
    public void onConnectStateChanged_doesNotCrashAfterStop() {
        mService.stop();
        mService.onConnectStateChanged(REMOTE_DEVICE_ADDRESS_AS_ARRAY, 1, 2, 3, 4);
    }

    @Test
    public void onControlStateChanged_doesNotCrash() {
        mService.onControlStateChanged(1, 2, 3, "ifname");
    }

    @Test
    public void setConnectionPolicy_whenDatabaseManagerRefuses_returnsFalse() {
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_ALLOWED;
        when(mDatabaseManager.setProfileConnectionPolicy(
                mRemoteDevice, BluetoothProfile.PAN, connectionPolicy)).thenReturn(false);

        assertThat(mService.setConnectionPolicy(mRemoteDevice, connectionPolicy)).isFalse();
    }

    @Test
    public void setConnectionPolicy_returnsTrue() {
        when(mDatabaseManager.setProfileConnectionPolicy(
                mRemoteDevice, BluetoothProfile.PAN, BluetoothProfile.CONNECTION_POLICY_ALLOWED))
                .thenReturn(true);
        assertThat(mService.setConnectionPolicy(
                mRemoteDevice, BluetoothProfile.CONNECTION_POLICY_ALLOWED)).isTrue();

        when(mDatabaseManager.setProfileConnectionPolicy(
                mRemoteDevice, BluetoothProfile.PAN, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN))
                .thenReturn(true);
        assertThat(mService.setConnectionPolicy(
                mRemoteDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN)).isTrue();
    }

    @Test
    public void connectState_constructor() {
        int state = 1;
        int error = 2;
        int localRole = 3;
        int remoteRole = 4;

        PanService.ConnectState connectState = new PanService.ConnectState(
                REMOTE_DEVICE_ADDRESS_AS_ARRAY, state, error, localRole, remoteRole);

        assertThat(connectState.addr).isEqualTo(REMOTE_DEVICE_ADDRESS_AS_ARRAY);
        assertThat(connectState.state).isEqualTo(state);
        assertThat(connectState.error).isEqualTo(error);
        assertThat(connectState.local_role).isEqualTo(localRole);
        assertThat(connectState.remote_role).isEqualTo(remoteRole);
    }

    @Test
    public void tetheringCallback_onError_clearsPanDevices() {
        mService.mIsTethering = true;
        mService.mPanDevices.put(mRemoteDevice, new BluetoothPanDevice(
                BluetoothProfile.STATE_DISCONNECTED, "iface", PAN_ROLE_NONE, PAN_ROLE_NONE));
        TetheringInterface iface = new TetheringInterface(TETHERING_BLUETOOTH, "iface");

        mService.mTetheringCallback.onError(iface, TETHER_ERROR_SERVICE_UNAVAIL);

        assertThat(mService.mPanDevices).isEmpty();
        assertThat(mService.mIsTethering).isFalse();
    }
}
