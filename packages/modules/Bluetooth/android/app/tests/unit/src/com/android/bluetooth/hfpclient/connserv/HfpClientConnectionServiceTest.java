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

package com.android.bluetooth.hfpclient;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.R;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class HfpClientConnectionServiceTest {
    private static final String TEST_DEVICE_ADDRESS = "00:11:22:33:44:55";
    private static final BluetoothDevice TEST_DEVICE =
            ((BluetoothManager) InstrumentationRegistry.getTargetContext()
                    .getSystemService(Context.BLUETOOTH_SERVICE))
            .getAdapter().getRemoteDevice(TEST_DEVICE_ADDRESS);
    private static final String TEST_NUMBER = "000-111-2222";

    @Mock private AdapterService mAdapterService;
    @Mock private HeadsetClientService mMockHeadsetClientService;
    @Mock private TelecomManager mMockTelecomManager;
    @Mock private Resources mMockResources;

    // @Rule private final ServiceTestRule mConnectionServiceRule = new ServiceTestRule();
    private HfpClientConnectionService mHfpClientConnectionService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        Context targetContext = InstrumentationRegistry.getTargetContext();

        TestUtils.setAdapterService(mAdapterService);
        // Setup a mock TelecomManager so our calls don't start a real instance of this service
        doNothing().when(mMockTelecomManager).addNewIncomingCall(any(), any());
        doNothing().when(mMockTelecomManager).addNewUnknownCall(any(), any());

        // Set a mocked HeadsetClientService for testing so we can insure the right functions were
        // called through the service interface
        when(mMockHeadsetClientService.isAvailable()).thenReturn(true);
        HeadsetClientService.setHeadsetClientService(mMockHeadsetClientService);

        // Spy the connection service under test so we can mock some of the system services and keep
        // them from impacting the actual system. Note: Another way to do this would be to extend
        // the class under test with a constructor taking a mock context that we inject using
        // attachBaseContext, but until we need a full context this is simpler.
        mHfpClientConnectionService = spy(new HfpClientConnectionService());

        doReturn("com.android.bluetooth.hfpclient").when(mHfpClientConnectionService)
                .getPackageName();
        doReturn(mHfpClientConnectionService).when(mHfpClientConnectionService)
                .getApplicationContext();
        doReturn(mMockResources).when(mHfpClientConnectionService).getResources();
        doReturn(true).when(mMockResources)
                .getBoolean(R.bool.hfp_client_connection_service_support_emergency_call);

        doReturn(Context.TELECOM_SERVICE).when(mHfpClientConnectionService)
                .getSystemServiceName(TelecomManager.class);
        doReturn(mMockTelecomManager).when(mHfpClientConnectionService)
                .getSystemService(Context.TELECOM_SERVICE);
        doReturn(getPhoneAccount(TEST_DEVICE)).when(mMockTelecomManager).getPhoneAccount(any());

        doReturn(Context.BLUETOOTH_SERVICE).when(mHfpClientConnectionService)
                .getSystemServiceName(BluetoothManager.class);
        doReturn(targetContext.getSystemService(BluetoothManager.class))
                .when(mHfpClientConnectionService).getSystemService(Context.BLUETOOTH_SERVICE);
    }

    @After
    public void tearDown() {
        TestUtils.clearAdapterService(mAdapterService);
    }

    private void createService() {
        mHfpClientConnectionService.onCreate();
    }

    private PhoneAccountHandle getPhoneAccountHandle(BluetoothDevice device) {
        return new PhoneAccountHandle(new ComponentName(mHfpClientConnectionService,
                HfpClientConnectionService.class), device.getAddress());
    }

    private PhoneAccount getPhoneAccount(BluetoothDevice device) {
        PhoneAccountHandle handle = getPhoneAccountHandle(device);
        Uri uri = Uri.fromParts(HfpClientConnectionService.HFP_SCHEME, device.getAddress(), null);
        return new PhoneAccount.Builder(handle, "HFP " + device.toString())
                .setAddress(uri)
                .setSupportedUriSchemes(Arrays.asList(PhoneAccount.SCHEME_TEL))
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();
    }

    private void setupDeviceConnection(BluetoothDevice device) throws Exception {
        mHfpClientConnectionService.onConnectionStateChanged(device,
                BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING);
        HfpClientDeviceBlock block = mHfpClientConnectionService.findBlockForDevice(TEST_DEVICE);
        assertThat(block).isNotNull();
        assertThat(block.getDevice()).isEqualTo(TEST_DEVICE);
    }

    @Test
    public void startServiceWithAlreadyConnectedDevice_blockIsCreated() throws Exception {
        when(mMockHeadsetClientService.getConnectedDevices()).thenReturn(List.of(TEST_DEVICE));
        createService();
        HfpClientDeviceBlock block = mHfpClientConnectionService.findBlockForDevice(TEST_DEVICE);
        assertThat(block).isNotNull();
        assertThat(block.getDevice()).isEqualTo(TEST_DEVICE);
    }

    @Test
    public void ConnectDevice_blockIsCreated() throws Exception {
        createService();
        setupDeviceConnection(TEST_DEVICE);
    }

    @Test
    public void disconnectDevice_blockIsRemoved() throws Exception {
        createService();
        setupDeviceConnection(TEST_DEVICE);
        HfpClientConnectionService.onConnectionStateChanged(TEST_DEVICE,
                BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTED);
        assertThat(mHfpClientConnectionService.findBlockForDevice(TEST_DEVICE)).isNull();
    }

    @Test
    public void callChanged_callAdded() throws Exception {
        createService();
        setupDeviceConnection(TEST_DEVICE);
        HfpClientCall call = new HfpClientCall(TEST_DEVICE, /* id= */0,
                HfpClientCall.CALL_STATE_ACTIVE, /* number= */ TEST_NUMBER,
                /* multiParty= */ false, /* outgoing= */false, /* inBandRing= */true);
        HfpClientConnectionService.onCallChanged(TEST_DEVICE, call);
        HfpClientDeviceBlock block = mHfpClientConnectionService.findBlockForDevice(TEST_DEVICE);
        assertThat(block).isNotNull();
        assertThat(block.getDevice()).isEqualTo(TEST_DEVICE);
        assertThat(block.getCalls().containsKey(call.getUUID())).isTrue();
    }

    @Test
    public void audioStateChanged_scoStateChanged() throws Exception {
        createService();
        setupDeviceConnection(TEST_DEVICE);
        HfpClientConnectionService.onAudioStateChanged(TEST_DEVICE,
                HeadsetClientHalConstants.AUDIO_STATE_CONNECTED,
                HeadsetClientHalConstants.AUDIO_STATE_CONNECTING);
        HfpClientDeviceBlock block = mHfpClientConnectionService.findBlockForDevice(TEST_DEVICE);
        assertThat(block).isNotNull();
        assertThat(block.getDevice()).isEqualTo(TEST_DEVICE);
        assertThat(block.getAudioState())
                .isEqualTo(HeadsetClientHalConstants.AUDIO_STATE_CONNECTED);
    }

    @Test
    public void onCreateIncomingConnection() throws Exception {
        createService();
        setupDeviceConnection(TEST_DEVICE);

        HfpClientCall call = new HfpClientCall(TEST_DEVICE, /* id= */0,
                HfpClientCall.CALL_STATE_ACTIVE, /* number= */ TEST_NUMBER,
                /* multiParty= */ false, /* outgoing= */false, /* inBandRing= */true);

        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS,
                new ParcelUuid(call.getUUID()));
        ConnectionRequest connectionRequest = new ConnectionRequest.Builder().setExtras(
                extras).build();

        HfpClientConnectionService.onCallChanged(TEST_DEVICE, call);

        Connection connection = mHfpClientConnectionService.onCreateIncomingConnection(
                getPhoneAccountHandle(TEST_DEVICE),
                connectionRequest);

        assertThat(connection).isNotNull();
        assertThat(((HfpClientConnection) connection).getDevice()).isEqualTo(TEST_DEVICE);
        assertThat(((HfpClientConnection) connection).getUUID()).isEqualTo(call.getUUID());
    }

    @Test
    public void onCreateOutgoingConnection() throws Exception {
        createService();
        setupDeviceConnection(TEST_DEVICE);

        HfpClientCall call = new HfpClientCall(TEST_DEVICE, /* id= */0,
                HfpClientCall.CALL_STATE_ACTIVE, /* number= */ TEST_NUMBER,
                /* multiParty= */ false, /* outgoing= */true, /* inBandRing= */true);

        doReturn(call).when(mMockHeadsetClientService).dial(TEST_DEVICE, TEST_NUMBER);

        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS,
                new ParcelUuid(call.getUUID()));
        ConnectionRequest connectionRequest = new ConnectionRequest.Builder().setExtras(
                extras).setAddress(Uri.fromParts(
                PhoneAccount.SCHEME_TEL, TEST_NUMBER, null)).build();

        Connection connection = mHfpClientConnectionService.onCreateOutgoingConnection(
                getPhoneAccountHandle(TEST_DEVICE),
                connectionRequest);

        assertThat(connection).isNotNull();
        assertThat(((HfpClientConnection) connection).getDevice()).isEqualTo(TEST_DEVICE);
        assertThat(((HfpClientConnection) connection).getUUID()).isEqualTo(call.getUUID());
    }

    @Test
    public void onCreateUnknownConnection() throws Exception {
        createService();
        setupDeviceConnection(TEST_DEVICE);

        HfpClientCall call = new HfpClientCall(TEST_DEVICE, /* id= */0,
                HfpClientCall.CALL_STATE_ACTIVE, /* number= */ TEST_NUMBER,
                /* multiParty= */ false, /* outgoing= */true, /* inBandRing= */true);

        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS,
                new ParcelUuid(call.getUUID()));
        ConnectionRequest connectionRequest = new ConnectionRequest.Builder().setExtras(
                extras).setAddress(Uri.fromParts(
                PhoneAccount.SCHEME_TEL, TEST_NUMBER, null)).build();

        HfpClientConnectionService.onCallChanged(TEST_DEVICE, call);

        Connection connection = mHfpClientConnectionService.onCreateUnknownConnection(
                getPhoneAccountHandle(TEST_DEVICE),
                connectionRequest);

        assertThat(connection).isNotNull();
        assertThat(((HfpClientConnection) connection).getDevice()).isEqualTo(TEST_DEVICE);
        assertThat(((HfpClientConnection) connection).getUUID()).isEqualTo(call.getUUID());
    }

    @Test
    public void onCreateIncomingConnection_phoneAccountIsNull_returnsNull() throws Exception{
        doReturn(null).when(mMockTelecomManager).getPhoneAccount(any());
        createService();
        setupDeviceConnection(TEST_DEVICE);

        HfpClientCall call = new HfpClientCall(TEST_DEVICE, /* id= */0,
                HfpClientCall.CALL_STATE_ACTIVE, /* number= */ TEST_NUMBER,
                /* multiParty= */ false, /* outgoing= */false, /* inBandRing= */true);

        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS,
                new ParcelUuid(call.getUUID()));
        ConnectionRequest connectionRequest = new ConnectionRequest.Builder().setExtras(
                extras).build();

        HfpClientConnectionService.onCallChanged(TEST_DEVICE, call);

        Connection connection = mHfpClientConnectionService.onCreateIncomingConnection(
                getPhoneAccountHandle(TEST_DEVICE),
                connectionRequest);

        assertThat(connection).isNull();
    }


    @Test
    public void onCreateOutgoingConnection_phoneAccountIsNull_returnsNull() throws Exception{
        doReturn(null).when(mMockTelecomManager).getPhoneAccount(any());
        createService();
        setupDeviceConnection(TEST_DEVICE);

        HfpClientCall call = new HfpClientCall(TEST_DEVICE, /* id= */0,
                HfpClientCall.CALL_STATE_ACTIVE, /* number= */ TEST_NUMBER,
                /* multiParty= */ false, /* outgoing= */true, /* inBandRing= */true);

        doReturn(call).when(mMockHeadsetClientService).dial(TEST_DEVICE, TEST_NUMBER);

        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS,
                new ParcelUuid(call.getUUID()));
        ConnectionRequest connectionRequest = new ConnectionRequest.Builder().setExtras(
                extras).setAddress(Uri.fromParts(
                PhoneAccount.SCHEME_TEL, TEST_NUMBER, null)).build();

        Connection connection = mHfpClientConnectionService.onCreateOutgoingConnection(
                getPhoneAccountHandle(TEST_DEVICE),
                connectionRequest);

        assertThat(connection).isNull();
    }


    @Test
    public void onCreateUnknownConnection_phoneAccountIsNull_returnsNull() throws Exception{
        doReturn(null).when(mMockTelecomManager).getPhoneAccount(any());
        createService();
        setupDeviceConnection(TEST_DEVICE);

        HfpClientCall call = new HfpClientCall(TEST_DEVICE, /* id= */0,
                HfpClientCall.CALL_STATE_ACTIVE, /* number= */ TEST_NUMBER,
                /* multiParty= */ false, /* outgoing= */true, /* inBandRing= */true);

        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS,
                new ParcelUuid(call.getUUID()));
        ConnectionRequest connectionRequest = new ConnectionRequest.Builder().setExtras(
                extras).setAddress(Uri.fromParts(
                PhoneAccount.SCHEME_TEL, TEST_NUMBER, null)).build();

        HfpClientConnectionService.onCallChanged(TEST_DEVICE, call);

        Connection connection = mHfpClientConnectionService.onCreateUnknownConnection(
                getPhoneAccountHandle(TEST_DEVICE),
                connectionRequest);

        assertThat(connection).isNull();
    }

}
