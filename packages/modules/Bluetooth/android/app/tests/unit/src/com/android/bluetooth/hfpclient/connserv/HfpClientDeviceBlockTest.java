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

import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class HfpClientDeviceBlockTest {
    private static final String TEST_DEVICE_ADDRESS = "00:11:22:33:44:55";
    private static final String TEST_NUMBER = "000-111-2222";
    private static final String KEY_SCO_STATE = "com.android.bluetooth.hfpclient.SCO_STATE";
    private static final String TEST_PACKAGE = "test";

    @Mock
    private HeadsetClientService mHeadsetClientService;
    @Mock
    private HfpClientConnectionService mConnServ;
    @Mock
    private HeadsetClientServiceInterface mMockServiceInterface;
    @Mock
    private Context mApplicationContext;
    @Mock
    private Resources mResources;
    @Mock
    private TelecomManager mTelecomManager;

    private HfpClientDeviceBlock mHfpClientDeviceBlock;
    private BluetoothDevice mBluetoothDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // HfpClientConnectionService.createAccount is static and can't be mocked, so the
        // application context and resources must be mocked to avoid NPE when creating an
        // HfpClientDeviceBlock for testing.
        when(mResources.getBoolean(com.android.bluetooth.R.bool
                        .hfp_client_connection_service_support_emergency_call)).thenReturn(true);
        when(mApplicationContext.getResources()).thenReturn(mResources);
        when(mConnServ.getApplicationContext()).thenReturn(mApplicationContext);
        when(mConnServ.getPackageName()).thenReturn(TEST_PACKAGE);

        when(mConnServ.getSystemService(Context.TELECOM_SERVICE)).thenReturn(mTelecomManager);
        when(mConnServ.getSystemServiceName(TelecomManager.class))
                .thenReturn(Context.TELECOM_SERVICE);

        mBluetoothDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(
                TEST_DEVICE_ADDRESS);

        when(mHeadsetClientService.isAvailable()).thenReturn(true);
        HeadsetClientService.setHeadsetClientService(mHeadsetClientService);
    }

    @Test
    public void testCreateOutgoingConnection_scoStateIsSet() {
        setUpCall(new HfpClientCall(mBluetoothDevice, /* id= */0,
                        HfpClientCall.CALL_STATE_ACTIVE, TEST_NUMBER,
                /* multiParty= */false, /* outgoing= */false, /* inBandRing= */true));
        HfpClientConnection connection = createOutgoingConnectionWithScoState(
                HeadsetClientHalConstants.AUDIO_STATE_CONNECTED);

        assertThat(connection.getExtras().getInt(KEY_SCO_STATE))
                .isEqualTo(HeadsetClientHalConstants.AUDIO_STATE_CONNECTED);
    }

    @Test
    public void testOnAudioStateChanged() {
        setUpCall(new HfpClientCall(mBluetoothDevice, /* id= */0,
                HfpClientCall.CALL_STATE_ACTIVE, TEST_NUMBER,
                /* multiParty= */false, /* outgoing= */false, /* inBandRing= */true));
        HfpClientConnection connection = createOutgoingConnectionWithScoState(
                HeadsetClientHalConstants.AUDIO_STATE_CONNECTED);
        assertThat(connection.getExtras().getInt(KEY_SCO_STATE))
                .isEqualTo(HeadsetClientHalConstants.AUDIO_STATE_CONNECTED);

        mHfpClientDeviceBlock.onAudioStateChange(HeadsetClientHalConstants.AUDIO_STATE_DISCONNECTED,
                HeadsetClientHalConstants.AUDIO_STATE_CONNECTED);

        assertThat(connection.getExtras().getInt(KEY_SCO_STATE))
                .isEqualTo(HeadsetClientHalConstants.AUDIO_STATE_DISCONNECTED);
    }

    @Test
    @Ignore("b/191783947")
    public void testHandleMultiPartyCall_scoStateIsSetOnConference() {
        HfpClientCall call =
                new HfpClientCall(mBluetoothDevice, /* id= */0,
                HfpClientCall.CALL_STATE_ACTIVE, TEST_NUMBER, /* multiParty= */
                true, /* outgoing= */false, /* inBandRing= */true);
        setUpCall(call);
        createOutgoingConnectionWithScoState(HeadsetClientHalConstants.AUDIO_STATE_CONNECTING);

        mHfpClientDeviceBlock.handleCall(call);

        ArgumentCaptor<HfpClientConference> conferenceCaptor =
                ArgumentCaptor.forClass(HfpClientConference.class);
        // TODO(b/191783947): addConference is final and cannot be mocked
        verify(mConnServ).addConference(conferenceCaptor.capture());

        HfpClientConference conference = conferenceCaptor.getValue();
        assertThat(conference.getExtras().getInt(KEY_SCO_STATE))
                .isEqualTo(HeadsetClientHalConstants.AUDIO_STATE_CONNECTING);
    }

    private void setUpCall(HfpClientCall call) {
        when(mMockServiceInterface.dial(mBluetoothDevice, TEST_NUMBER)).thenReturn(call);
    }

    private HfpClientConnection createOutgoingConnectionWithScoState(int scoState) {
        when(mHeadsetClientService.getAudioState(mBluetoothDevice)).thenReturn(scoState);
        doCallRealMethod()
                .when(mConnServ)
                .createAccount(any());
        mHfpClientDeviceBlock =
                new HfpClientDeviceBlock(mBluetoothDevice, mConnServ, mMockServiceInterface);
        return mHfpClientDeviceBlock.onCreateOutgoingConnection(
                Uri.fromParts(PhoneAccount.SCHEME_TEL, TEST_NUMBER, /* fragment= */ null));
    }
}
