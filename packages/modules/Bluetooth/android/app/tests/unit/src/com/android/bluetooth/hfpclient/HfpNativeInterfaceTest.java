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

package com.android.bluetooth.hfpclient;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class HfpNativeInterfaceTest {
    private static final byte[] TEST_DEVICE_ADDRESS =
            new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    @Mock
    HeadsetClientService mService;
    @Mock
    AdapterService mAdapterService;

    private NativeInterface mNativeInterface;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mService.isAvailable()).thenReturn(true);
        HeadsetClientService.setHeadsetClientService(mService);
        TestUtils.setAdapterService(mAdapterService);
        mNativeInterface = NativeInterface.getInstance();
    }

    @After
    public void tearDown() throws Exception {
        HeadsetClientService.setHeadsetClientService(null);
        TestUtils.clearAdapterService(mAdapterService);
    }

    @Test
    public void onConnectionStateChanged() {
        int state = HeadsetClientHalConstants.CONNECTION_STATE_CONNECTED;
        int peerFeat = HeadsetClientHalConstants.PEER_FEAT_HF_IND;
        int chldFeat = HeadsetClientHalConstants.PEER_FEAT_ECS;
        mNativeInterface.onConnectionStateChanged(state, peerFeat, chldFeat, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        assertThat(event.getValue().valueInt).isEqualTo(state);
        assertThat(event.getValue().valueInt2).isEqualTo(peerFeat);
        assertThat(event.getValue().valueInt3).isEqualTo(chldFeat);
    }

    @Test
    public void onAudioStateChanged() {
        int state = HeadsetClientHalConstants.AUDIO_STATE_DISCONNECTED;
        mNativeInterface.onAudioStateChanged(state, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED);
        assertThat(event.getValue().valueInt).isEqualTo(state);
    }

    @Test
    public void onVrStateChanged() {
        int state = 1;
        mNativeInterface.onVrStateChanged(state, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_VR_STATE_CHANGED);
        assertThat(event.getValue().valueInt).isEqualTo(state);
    }

    @Test
    public void onNetworkState() {
        int state = HeadsetClientHalConstants.NETWORK_STATE_NOT_AVAILABLE;
        mNativeInterface.onNetworkState(state, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_NETWORK_STATE);
        assertThat(event.getValue().valueInt).isEqualTo(state);
    }

    @Test
    public void onNetworkRoaming() {
        int state = HeadsetClientHalConstants.SERVICE_TYPE_ROAMING;
        mNativeInterface.onNetworkRoaming(state, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_ROAMING_STATE);
        assertThat(event.getValue().valueInt).isEqualTo(state);
    }

    @Test
    public void onNetworkSignal() {
        int signal = 3;
        mNativeInterface.onNetworkSignal(signal, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_NETWORK_SIGNAL);
        assertThat(event.getValue().valueInt).isEqualTo(signal);
    }

    @Test
    public void onBatteryLevel() {
        int level = 15;
        mNativeInterface.onBatteryLevel(level, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_BATTERY_LEVEL);
        assertThat(event.getValue().valueInt).isEqualTo(level);
    }

    @Test
    public void onCurrentOperator() {
        String name = "test";
        mNativeInterface.onCurrentOperator(name, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_OPERATOR_NAME);
        assertThat(event.getValue().valueString).isEqualTo(name);
    }

    @Test
    public void onCall() {
        int call = 1;
        mNativeInterface.onCall(call, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_CALL);
        assertThat(event.getValue().valueInt).isEqualTo(call);
    }

    @Test
    public void onCallSetup() {
        int callsetup = 1;
        mNativeInterface.onCallSetup(callsetup, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_CALLSETUP);
        assertThat(event.getValue().valueInt).isEqualTo(callsetup);
    }

    @Test
    public void onCallHeld() {
        int callheld = 1;
        mNativeInterface.onCallSetup(callheld, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_CALLSETUP);
        assertThat(event.getValue().valueInt).isEqualTo(callheld);
    }

    @Test
    public void onRespAndHold() {
        int respAndHold = 1;
        mNativeInterface.onRespAndHold(respAndHold, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_RESP_AND_HOLD);
        assertThat(event.getValue().valueInt).isEqualTo(respAndHold);
    }

    @Test
    public void onClip() {
        String number = "000-000-0000";
        mNativeInterface.onClip(number, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_CLIP);
        assertThat(event.getValue().valueString).isEqualTo(number);
    }

    @Test
    public void onCallWaiting() {
        String number = "000-000-0000";
        mNativeInterface.onCallWaiting(number, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_CALL_WAITING);
        assertThat(event.getValue().valueString).isEqualTo(number);
    }

    @Test
    public void onCurrentCalls() {
        int index = 2;
        int dir = HeadsetClientHalConstants.CALL_DIRECTION_OUTGOING;
        int state = HfpClientCall.CALL_STATE_WAITING;
        int mparty = HeadsetClientHalConstants.CALL_MPTY_TYPE_MULTI;
        String number = "000-000-0000";
        mNativeInterface.onCurrentCalls(index, dir, state, mparty, number, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_CURRENT_CALLS);
        assertThat(event.getValue().valueInt).isEqualTo(index);
        assertThat(event.getValue().valueInt2).isEqualTo(dir);
        assertThat(event.getValue().valueInt3).isEqualTo(state);
        assertThat(event.getValue().valueInt4).isEqualTo(mparty);
        assertThat(event.getValue().valueString).isEqualTo(number);
    }

    @Test
    public void onVolumeChange() {
        int type = HeadsetClientHalConstants.VOLUME_TYPE_SPK;
        int volume = 10;
        mNativeInterface.onVolumeChange(type, volume, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_VOLUME_CHANGED);
        assertThat(event.getValue().valueInt).isEqualTo(type);
        assertThat(event.getValue().valueInt2).isEqualTo(volume);
    }

    @Test
    public void onCmdResult() {
        int type = HeadsetClientStateMachine.AT_OK;
        int cme = 10;
        mNativeInterface.onCmdResult(type, cme, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_CMD_RESULT);
        assertThat(event.getValue().valueInt).isEqualTo(type);
        assertThat(event.getValue().valueInt2).isEqualTo(cme);
    }

    @Test
    public void onSubscriberInfo() {
        String number = "000-000-0000";
        int type = 5;
        mNativeInterface.onSubscriberInfo(number, type, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_SUBSCRIBER_INFO);
        assertThat(event.getValue().valueString).isEqualTo(number);
    }

    @Test
    public void onInBandRing() {
        int inBand = 1;
        mNativeInterface.onInBandRing(inBand, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_IN_BAND_RINGTONE);
        assertThat(event.getValue().valueInt).isEqualTo(inBand);
    }

    @Test
    // onLastVoiceTagNumber is not supported.
    public void onLastVoiceTagNumber_doesNotCrash() {
        String number = "000-000-0000";
        mNativeInterface.onLastVoiceTagNumber(number, TEST_DEVICE_ADDRESS);
    }

    @Test
    public void onRingIndication() {
        mNativeInterface.onRingIndication(TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_RING_INDICATION);
    }

    @Test
    public void onUnknownEvent() {
        String eventString = "unknown";
        mNativeInterface.onUnknownEvent(eventString, TEST_DEVICE_ADDRESS);

        ArgumentCaptor<StackEvent> event = ArgumentCaptor.forClass(StackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(StackEvent.EVENT_TYPE_UNKNOWN_EVENT);
        assertThat(event.getValue().valueString).isEqualTo(eventString);
    }

    @Test
    public void testSendAndroidAt() {
        // If the device is null, should return False
        assertThat(mNativeInterface.sendAndroidAt(null, anyString())).isEqualTo(false);
    }
}
