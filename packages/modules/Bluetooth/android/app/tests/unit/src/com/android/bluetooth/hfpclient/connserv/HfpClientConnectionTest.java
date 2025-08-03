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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.net.Uri;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;

import androidx.test.filters.MediumTest;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

import java.util.Set;

@MediumTest
@RunWith(MockitoJUnitRunner.class)
public class HfpClientConnectionTest {
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    private static final String EVENT_SCO_CONNECT = "com.android.bluetooth.hfpclient.SCO_CONNECT";
    private static final String EVENT_SCO_DISCONNECT =
            "com.android.bluetooth.hfpclient.SCO_DISCONNECT";
    private static final String TEST_NUMBER = "000-111-2222";
    private static final String TEST_NUMBER_2 = "444-555-6666";
    private static final String TEST_DEVICE_ADDRESS = "00:11:22:33:44:55";

    private HfpClientConnection mHfpClientConnection;
    private BluetoothDevice mBluetoothDevice;
    private HfpClientCall mCall;

    @Mock
    private HeadsetClientServiceInterface mMockServiceInterface;
    @Mock
    private Context mContext;
    @Mock
    private HfpClientConnectionService mHfpClientConnectionService;

    @Before
    public void setUp() {
        mBluetoothDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(
                TEST_DEVICE_ADDRESS);
        mCall = new HfpClientCall(mBluetoothDevice, /* id= */0,
                HfpClientCall.CALL_STATE_ACTIVE, TEST_NUMBER, /* multiParty= */
                false, /* outgoing= */false, /* inBandRing= */true);
    }

    @Test
    public void constructorWithCall() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();

        assertThat(mHfpClientConnection.getCall()).isEqualTo(mCall);
        assertThat(mHfpClientConnection.getDevice()).isEqualTo(mBluetoothDevice);
        assertThat(mHfpClientConnection.getUUID()).isEqualTo(mCall.getUUID());
        assertThat(mHfpClientConnection.getState()).isEqualTo(Connection.STATE_ACTIVE);
        assertThat(mHfpClientConnection.getAudioModeIsVoip()).isFalse();
        assertThat(mHfpClientConnection.getAddress()).isEqualTo(
                Uri.fromParts(PhoneAccount.SCHEME_TEL, TEST_NUMBER, /* fragment= */ null));
        assertThat(mHfpClientConnection.getAddressPresentation()).isEqualTo(
                TelecomManager.PRESENTATION_ALLOWED);
        assertThat(mHfpClientConnection.getConnectionCapabilities()).isEqualTo(
                Connection.CAPABILITY_SUPPORT_HOLD | Connection.CAPABILITY_MUTE
                        | Connection.CAPABILITY_SEPARATE_FROM_CONFERENCE
                        | Connection.CAPABILITY_DISCONNECT_FROM_CONFERENCE
                        | Connection.CAPABILITY_HOLD);
    }

    @Test
    public void constructorWithNumber() {
        when(mMockServiceInterface.dial(mBluetoothDevice, TEST_NUMBER)).thenReturn(mCall);

        mHfpClientConnection = initiateHfpClientConnectionWithNumber().build();

        assertThat(mHfpClientConnection.getCall()).isEqualTo(mCall);
        assertThat(mHfpClientConnection.getDevice()).isEqualTo(mBluetoothDevice);
        assertThat(mHfpClientConnection.getUUID()).isEqualTo(mCall.getUUID());
        assertThat(mHfpClientConnection.getExtras()).isNull();
        assertThat(mHfpClientConnection.getState()).isEqualTo(Connection.STATE_DIALING);
        assertThat(mHfpClientConnection.getAudioModeIsVoip()).isFalse();
        assertThat(mHfpClientConnection.getAddress()).isEqualTo(
                Uri.fromParts(PhoneAccount.SCHEME_TEL, TEST_NUMBER, /* fragment= */ null));
        assertThat(mHfpClientConnection.getAddressPresentation()).isEqualTo(
                TelecomManager.PRESENTATION_ALLOWED);
        assertThat(mHfpClientConnection.getConnectionCapabilities()).isEqualTo(
                Connection.CAPABILITY_SUPPORT_HOLD | Connection.CAPABILITY_MUTE
                        | Connection.CAPABILITY_SEPARATE_FROM_CONFERENCE
                        | Connection.CAPABILITY_DISCONNECT_FROM_CONFERENCE);

    }

    @Test
    public void onHfpDisconnected() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();

        mHfpClientConnection.onHfpDisconnected();

        assertThat(mHfpClientConnection.getState()).isEqualTo(Connection.STATE_DISCONNECTED);
        assertThat(mHfpClientConnection.getDisconnectCause())
                .isEqualTo(new DisconnectCause(DisconnectCause.ERROR));
        assertThat(mHfpClientConnection.getCall()).isNull();
    }

    @Test
    public void multiPartyCallOnAddedNotDisconnected_isInConference() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall()
                .setCall(new HfpClientCall(mBluetoothDevice, /* id= */0,
                        HfpClientCall.CALL_STATE_ACTIVE,
                        "444-555-6666", /* multiParty= */true, /* outgoing= */
                        false, /* inBandRing= */true)).build();
        mHfpClientConnection.onAdded();

        assertThat(mHfpClientConnection.inConference()).isTrue();
    }

    @Test
    public void singlePartyCallOnAddedNotDisconnected_notInConference() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        mHfpClientConnection.onAdded();

        assertThat(mHfpClientConnection.inConference()).isFalse();
    }

    @Test
    public void multiPartyCallNotAddedNotDisconnected_notInConference() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall()
                .setCall(new HfpClientCall(mBluetoothDevice, /* id= */0,
                        HfpClientCall.CALL_STATE_ACTIVE,
                        "444-555-6666", /* multiParty= */true, /* outgoing= */
                        false, /* inBandRing= */true)).build();

        assertThat(mHfpClientConnection.inConference()).isFalse();
    }

    @Test
    public void multiPartyCallOnAddedDisconnected_notInConference() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall()
                .setCall(new HfpClientCall(mBluetoothDevice, /* id= */0,
                        HfpClientCall.CALL_STATE_ACTIVE,
                        "444-555-6666", /* multiParty= */true, /* outgoing= */
                        false, /* inBandRing= */true)).build();
        mHfpClientConnection.onAdded();
        mHfpClientConnection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));

        assertThat(mHfpClientConnection.inConference()).isFalse();
    }

    @Test
    public void enterPrivateMode() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();

        mHfpClientConnection.enterPrivateMode();
        verify(mMockServiceInterface).enterPrivateMode(mBluetoothDevice, mCall.getId());
        assertThat(mHfpClientConnection.getState()).isEqualTo(Connection.STATE_ACTIVE);
    }

    @Test
    public void updateCall() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        assertThat(mHfpClientConnection.getCall()).isEqualTo(mCall);

        HfpClientCall newCall =
                new HfpClientCall(mBluetoothDevice, /* id= */0,
                HfpClientCall.CALL_STATE_ACTIVE,
                TEST_NUMBER_2, /* multiParty= */false, /* outgoing= */
                false, /* inBandRing= */true);
        mHfpClientConnection.updateCall(newCall);

        assertThat(mHfpClientConnection.getCall()).isEqualTo(newCall);
    }

    @Test
    public void handleCallChanged_active() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        mHfpClientConnection.getCall().setState(HfpClientCall.CALL_STATE_ACTIVE);
        mHfpClientConnection.handleCallChanged();

        assertThat(mHfpClientConnection.getState()).isEqualTo(Connection.STATE_ACTIVE);
    }

    @Test
    @Ignore("b/191783947")
    public void handleCallChanged_activeConference() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        HfpClientConference mockConference = mock(HfpClientConference.class);
        mHfpClientConnection.setConference(mockConference);
        mHfpClientConnection.getCall().setState(HfpClientCall.CALL_STATE_ACTIVE);
        mHfpClientConnection.handleCallChanged();

        verify(mockConference).setActive();
    }

    @Test
    public void handleCallChanged_heldByResponseAndHold() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        mHfpClientConnection.getCall()
                .setState(HfpClientCall.CALL_STATE_HELD_BY_RESPONSE_AND_HOLD);
        mHfpClientConnection.handleCallChanged();

        assertThat(mHfpClientConnection.getState()).isEqualTo(Connection.STATE_HOLDING);
    }

    @Test
    @Ignore("b/191783947")
    public void handleCallChanged_heldByResponseAndHoldConference() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        HfpClientConference mockConference = mock(HfpClientConference.class);
        mHfpClientConnection.setConference(mockConference);
        mHfpClientConnection.getCall()
                .setState(HfpClientCall.CALL_STATE_HELD_BY_RESPONSE_AND_HOLD);
        mHfpClientConnection.handleCallChanged();

        verify(mockConference).setOnHold();
    }

    @Test
    public void handleCallChanged_held() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        mHfpClientConnection.getCall().setState(HfpClientCall.CALL_STATE_HELD);
        mHfpClientConnection.handleCallChanged();

        assertThat(mHfpClientConnection.getState()).isEqualTo(Connection.STATE_HOLDING);
    }

    @Test
    @Ignore("b/191783947")
    public void handleCallChanged_heldConference() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        HfpClientConference mockConference = mock(HfpClientConference.class);
        mHfpClientConnection.setConference(mockConference);
        mHfpClientConnection.getCall().setState(HfpClientCall.CALL_STATE_HELD);
        mHfpClientConnection.handleCallChanged();

        verify(mockConference).setOnHold();
    }

    @Test
    public void handleCallChanged_dialing() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        mHfpClientConnection.getCall().setState(HfpClientCall.CALL_STATE_DIALING);
        mHfpClientConnection.handleCallChanged();

        assertThat(mHfpClientConnection.getState()).isEqualTo(Connection.STATE_DIALING);
    }

    @Test
    public void handleCallChanged_alerting() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        mHfpClientConnection.getCall().setState(HfpClientCall.CALL_STATE_ALERTING);
        mHfpClientConnection.handleCallChanged();

        assertThat(mHfpClientConnection.getState()).isEqualTo(Connection.STATE_DIALING);
    }

    @Test
    public void handleCallChanged_incoming() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        mHfpClientConnection.getCall().setState(HfpClientCall.CALL_STATE_INCOMING);
        mHfpClientConnection.handleCallChanged();

        assertThat(mHfpClientConnection.getState()).isEqualTo(Connection.STATE_RINGING);
    }

    @Test
    public void handleCallChanged_waiting() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        mHfpClientConnection.getCall().setState(HfpClientCall.CALL_STATE_WAITING);
        mHfpClientConnection.handleCallChanged();

        assertThat(mHfpClientConnection.getState()).isEqualTo(Connection.STATE_RINGING);
    }

    @Test
    public void handleCallChanged_terminated_missedDisconnect() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        // Allow previous call state to be set to CALL_STATE_WAITING
        mHfpClientConnection.getCall().setState(HfpClientCall.CALL_STATE_WAITING);
        mHfpClientConnection.handleCallChanged();

        mHfpClientConnection.getCall().setState(HfpClientCall.CALL_STATE_TERMINATED);
        mHfpClientConnection.handleCallChanged();

        assertThat(mHfpClientConnection.getState()).isEqualTo(Connection.STATE_DISCONNECTED);
        assertThat(mHfpClientConnection.getDisconnectCause())
                .isEqualTo(new DisconnectCause(DisconnectCause.MISSED));
    }

    @Test
    public void handleCallChanged_terminated_localDisconnect() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        // Set local disconnect variable to true
        mHfpClientConnection.onDisconnect();

        mHfpClientConnection.getCall().setState(HfpClientCall.CALL_STATE_TERMINATED);
        mHfpClientConnection.handleCallChanged();

        assertThat(mHfpClientConnection.getState()).isEqualTo(Connection.STATE_DISCONNECTED);
        assertThat(mHfpClientConnection.getDisconnectCause())
                .isEqualTo(new DisconnectCause(DisconnectCause.LOCAL));
    }

    @Test
    public void handleCallChanged_terminated_remoteDisconnect() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        mHfpClientConnection.getCall().setState(HfpClientCall.CALL_STATE_TERMINATED);
        mHfpClientConnection.handleCallChanged();

        assertThat(mHfpClientConnection.getState()).isEqualTo(Connection.STATE_DISCONNECTED);
        assertThat(mHfpClientConnection.getDisconnectCause())
                .isEqualTo(new DisconnectCause(DisconnectCause.REMOTE));
    }

    @Test
    public void close_disconnectsAndSetsCallToNull() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();

        mHfpClientConnection.close(DisconnectCause.ERROR);

        assertThat(mHfpClientConnection.getState()).isEqualTo(Connection.STATE_DISCONNECTED);
        assertThat(mHfpClientConnection.getDisconnectCause())
                .isEqualTo(new DisconnectCause(DisconnectCause.ERROR));
        assertThat(mHfpClientConnection.getCall()).isNull();
    }

    @Test
    public void onPlayDtmfTone() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();

        mHfpClientConnection.onPlayDtmfTone('a');

        verify(mMockServiceInterface).sendDTMF(mBluetoothDevice, (byte) 'a');
    }

    @Test
    public void onDisconnect_disconnects() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();

        mHfpClientConnection.onDisconnect();

        verify(mMockServiceInterface).terminateCall(mBluetoothDevice, mCall);
        assertThat(mHfpClientConnection.isClosing()).isTrue();
    }

    @Test
    public void onAbort_disconnects() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();

        mHfpClientConnection.onAbort();

        verify(mMockServiceInterface).terminateCall(mBluetoothDevice, mCall);
        assertThat(mHfpClientConnection.isClosing()).isTrue();
    }

    @Test
    public void onHold() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();

        mHfpClientConnection.onHold();

        verify(mMockServiceInterface).holdCall(mBluetoothDevice);
    }

    @Test
    @Ignore("b/191783947")
    public void onUnhold_connectionServiceHasOneConnection_acceptsHeldCall() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        when(mHfpClientConnectionService.getAllConnections()).thenReturn(
                Set.of(mHfpClientConnection));

        mHfpClientConnection.onUnhold();

        verify(mMockServiceInterface).acceptCall(mBluetoothDevice,
                HeadsetClientServiceInterface.CALL_ACCEPT_HOLD);
    }

    @Test
    @Ignore("b/191783947")
    public void onUnhold_connectionServiceHasMultipleConnections_doesNotAcceptHeldCall() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        HfpClientConnection secondConnection = createHfpClientConnectionWithExistingCall()
                .setCall(new HfpClientCall(mBluetoothDevice, /* id= */0,
                        HfpClientCall.CALL_STATE_ACTIVE,
                        "444-555-6666", /* multiParty= */false, /* outgoing= */
                        false, /* inBandRing= */true)).build();
        when(mHfpClientConnectionService.getAllConnections()).thenReturn(
                Set.of(mHfpClientConnection, secondConnection));

        mHfpClientConnection.onUnhold();

        verify(mMockServiceInterface, never()).acceptCall(mBluetoothDevice,
                HeadsetClientServiceInterface.CALL_ACCEPT_HOLD);
    }

    @Test
    public void onAnswer() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();

        mHfpClientConnection.onAnswer();

        verify(mMockServiceInterface).acceptCall(mBluetoothDevice,
                HeadsetClientServiceInterface.CALL_ACCEPT_NONE);
    }

    @Test
    public void onReject() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();

        mHfpClientConnection.onReject();

        verify(mMockServiceInterface).rejectCall(mBluetoothDevice);
    }

    @Test
    public void onCallEvent_scoConnect() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        mHfpClientConnection.onCallEvent(EVENT_SCO_CONNECT, /* extras= */ null);

        verify(mMockServiceInterface).connectAudio(mBluetoothDevice);
    }

    @Test
    public void onCallEvent_scoDisconnect() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        mHfpClientConnection.onCallEvent(EVENT_SCO_DISCONNECT, /* extras= */ null);

        verify(mMockServiceInterface).disconnectAudio(mBluetoothDevice);
    }

    @Test
    public void onCallEvent_unhandledAction() {
        mHfpClientConnection = createHfpClientConnectionWithExistingCall().build();
        mHfpClientConnection.onCallEvent("UNHANDLED_ACTION", /* extras= */ null);

        verify(mMockServiceInterface, never()).connectAudio(mBluetoothDevice);
        verify(mMockServiceInterface, never()).disconnectAudio(mBluetoothDevice);
    }

    private HfpClientConnectionBuilderFromExistingCall createHfpClientConnectionWithExistingCall() {
        return new HfpClientConnectionBuilderFromExistingCall();
    }

    private HfpClientConnectionBuilderWithNumber initiateHfpClientConnectionWithNumber() {
        return new HfpClientConnectionBuilderWithNumber();
    }

    public abstract class HfpClientConnectionBuilder {
        protected Context mContext = HfpClientConnectionTest.this.mContext;
        protected BluetoothDevice mBluetoothDevice = HfpClientConnectionTest.this.mBluetoothDevice;
        protected HeadsetClientServiceInterface mServiceInterface =
                HfpClientConnectionTest.this.mMockServiceInterface;

        public HfpClientConnectionBuilder setBluetoothHeadsetClient(
                HeadsetClientServiceInterface serviceInterface) {
            mServiceInterface = serviceInterface;
            return this;
        }

        public abstract HfpClientConnection build();
    }

    public class HfpClientConnectionBuilderFromExistingCall extends HfpClientConnectionBuilder {
        private HfpClientCall mCall = HfpClientConnectionTest.this.mCall;

        public HfpClientConnectionBuilder setCall(HfpClientCall call) {
            mCall = call;
            return this;
        }

        @Override
        public HfpClientConnection build() {
            return new HfpClientConnection(mBluetoothDevice, mCall, mHfpClientConnectionService,
                    mMockServiceInterface);
        }
    }

    public class HfpClientConnectionBuilderWithNumber extends HfpClientConnectionBuilder {
        private String mNumber = TEST_NUMBER;

        public HfpClientConnectionBuilder setNumber(String number) {
            mNumber = number;
            return this;
        }

        @Override
        public HfpClientConnection build() {
            return new HfpClientConnection(mBluetoothDevice, Uri.parse(mNumber),
                    mHfpClientConnectionService, mMockServiceInterface);
        }
    }
}
