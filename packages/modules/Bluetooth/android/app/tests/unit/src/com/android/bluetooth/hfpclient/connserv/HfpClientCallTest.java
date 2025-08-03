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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Parcel;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HfpClientCallTest {
    private static final String TEST_DEVICE_ADDRESS = "00:11:22:33:44:55";
    private static final BluetoothDevice TEST_DEVICE =
            ((BluetoothManager) InstrumentationRegistry.getTargetContext()
                    .getSystemService(Context.BLUETOOTH_SERVICE))
            .getAdapter().getRemoteDevice(TEST_DEVICE_ADDRESS);
    private static final int TEST_ID = 0;
    private static final String TEST_NUMBER = "000-111-2222";
    private static final String TEST_NUMBER_2 = "111-222-3333";

    private void assertCall(BluetoothDevice device, int id, int state, String number,
            boolean isMultiParty, boolean isOutgoing, boolean isInBandRing, HfpClientCall call) {
        assertThat(call).isNotNull();
        assertThat(call.getDevice()).isEqualTo(device);
        assertThat(call.getId()).isEqualTo(id);
        assertThat(call.getUUID()).isNotNull();
        assertThat(call.getState()).isEqualTo(state);
        assertThat(call.getNumber()).isEqualTo(number);
        assertThat(call.isMultiParty()).isEqualTo(isMultiParty);
        assertThat(call.isOutgoing()).isEqualTo(isOutgoing);
        assertThat(call.getCreationElapsedMilli()).isGreaterThan(0);
        assertThat(call.isInBandRing()).isEqualTo(isInBandRing);
        assertThat(call.toString()).isNotNull();
        assertThat(call.describeContents()).isEqualTo(0);
    }

    @Test
    public void testCreateActiveCall() {
        HfpClientCall call = new HfpClientCall(
                /* device= */ TEST_DEVICE,
                /* call id= */ TEST_ID,
                /* call state= */ HfpClientCall.CALL_STATE_ACTIVE,
                /* phone number= */ TEST_NUMBER,
                /* multiParty= */ false,
                /* outgoing= */ true,
                /* inBandRing= */ false);
        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_ACTIVE, TEST_NUMBER, false, true,
                false, call);
    }

    @Test
    public void testCreateHeldCall() {
        HfpClientCall call = new HfpClientCall(
                /* device= */ TEST_DEVICE,
                /* call id= */ TEST_ID,
                /* call state= */ HfpClientCall.CALL_STATE_HELD,
                /* phone number= */ TEST_NUMBER,
                /* multiParty= */ false,
                /* outgoing= */ true,
                /* inBandRing= */ false);
        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_HELD, TEST_NUMBER, false, true,
                false, call);
    }

    @Test
    public void testCreateDialingCall() {
        HfpClientCall call = new HfpClientCall(
                /* device= */ TEST_DEVICE,
                /* call id= */ TEST_ID,
                /* call state= */ HfpClientCall.CALL_STATE_DIALING,
                /* phone number= */ TEST_NUMBER,
                /* multiParty= */ false,
                /* outgoing= */ true,
                /* inBandRing= */ false);
        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_DIALING, TEST_NUMBER, false, true,
                false, call);
    }

    @Test
    public void testCreateAlertingCall() {
        HfpClientCall call = new HfpClientCall(
                /* device= */ TEST_DEVICE,
                /* call id= */ TEST_ID,
                /* call state= */ HfpClientCall.CALL_STATE_ALERTING,
                /* phone number= */ TEST_NUMBER,
                /* multiParty= */ false,
                /* outgoing= */ true,
                /* inBandRing= */ false);
        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_ALERTING, TEST_NUMBER, false,
                true, false, call);
    }

    @Test
    public void testCreateIncomingCall() {
        HfpClientCall call = new HfpClientCall(
                /* device= */ TEST_DEVICE,
                /* call id= */ TEST_ID,
                /* call state= */ HfpClientCall.CALL_STATE_INCOMING,
                /* phone number= */ TEST_NUMBER,
                /* multiParty= */ false,
                /* outgoing= */ false,
                /* inBandRing= */ false);
        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_INCOMING, TEST_NUMBER, false,
                false, false, call);
    }

    @Test
    public void testCreateWaitingCall() {
        HfpClientCall call = new HfpClientCall(
                /* device= */ TEST_DEVICE,
                /* call id= */ TEST_ID,
                /* call state= */ HfpClientCall.CALL_STATE_WAITING,
                /* phone number= */ TEST_NUMBER,
                /* multiParty= */ false,
                /* outgoing= */ false,
                /* inBandRing= */ false);
        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_WAITING, TEST_NUMBER, false,
                false, false, call);
    }

    @Test
    public void testCreateHeldByResponseAndHoldCall() {
        HfpClientCall call = new HfpClientCall(
                /* device= */ TEST_DEVICE,
                /* call id= */ TEST_ID,
                /* call state= */ HfpClientCall.CALL_STATE_HELD_BY_RESPONSE_AND_HOLD,
                /* phone number= */ TEST_NUMBER,
                /* multiParty= */ false,
                /* outgoing= */ false,
                /* inBandRing= */ false);
        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_HELD_BY_RESPONSE_AND_HOLD,
                TEST_NUMBER, false, false, false, call);
    }

    @Test
    public void testCreateTerminatedCall() {
        HfpClientCall call = new HfpClientCall(
                /* device= */ TEST_DEVICE,
                /* call id= */ TEST_ID,
                /* call state= */ HfpClientCall.CALL_STATE_TERMINATED,
                /* phone number= */ TEST_NUMBER,
                /* multiParty= */ false,
                /* outgoing= */ false,
                /* inBandRing= */ false);
        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_TERMINATED, TEST_NUMBER, false,
                false, false, call);
    }

    @Test
    public void testSetState() {
        HfpClientCall call = new HfpClientCall(
                /* device= */ TEST_DEVICE,
                /* call id= */ TEST_ID,
                /* call state= */ HfpClientCall.CALL_STATE_ACTIVE,
                /* phone number= */ TEST_NUMBER,
                /* multiParty= */ false,
                /* outgoing= */ true,
                /* inBandRing= */ false);

        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_ACTIVE, TEST_NUMBER, false, true,
                false, call);

        call.setState(HfpClientCall.CALL_STATE_HELD);

        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_HELD, TEST_NUMBER, false, true,
                false, call);
    }

    @Test
    public void testSetNumber() {
        HfpClientCall call = new HfpClientCall(
                /* device= */ TEST_DEVICE,
                /* call id= */ TEST_ID,
                /* call state= */ HfpClientCall.CALL_STATE_ACTIVE,
                /* phone number= */ TEST_NUMBER,
                /* multiParty= */ false,
                /* outgoing= */ true,
                /* inBandRing= */ false);

        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_ACTIVE, TEST_NUMBER, false, true,
                false, call);

        call.setNumber(TEST_NUMBER_2);

        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_ACTIVE, TEST_NUMBER_2, false,
                true, false, call);
    }

    @Test
    public void testSetMultiParty() {
        HfpClientCall call = new HfpClientCall(
                /* device= */ TEST_DEVICE,
                /* call id= */ TEST_ID,
                /* call state= */ HfpClientCall.CALL_STATE_ACTIVE,
                /* phone number= */ TEST_NUMBER,
                /* multiParty= */ false,
                /* outgoing= */ true,
                /* inBandRing= */ false);

        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_ACTIVE, TEST_NUMBER, false, true,
                false, call);

        call.setMultiParty(true);

        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_ACTIVE, TEST_NUMBER, true, true,
                false, call);
    }

    @Test
    public void testParcelable() {
        HfpClientCall call = new HfpClientCall(
                /* device= */ TEST_DEVICE,
                /* call id= */ TEST_ID,
                /* call state= */ HfpClientCall.CALL_STATE_ACTIVE,
                /* phone number= */ TEST_NUMBER,
                /* multiParty= */ false,
                /* outgoing= */ true,
                /* inBandRing= */ false);

        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_ACTIVE, TEST_NUMBER, false, true,
                false, call);

        Parcel parcel = Parcel.obtain();
        call.writeToParcel(parcel, 0 /* flags */);
        parcel.setDataPosition(0);
        HfpClientCall callOut = HfpClientCall.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertCall(TEST_DEVICE, TEST_ID, HfpClientCall.CALL_STATE_ACTIVE, TEST_NUMBER, false, true,
                false, callOut);

        assertThat(HfpClientCall.CREATOR.newArray(5).length).isEqualTo(5);
    }
}
