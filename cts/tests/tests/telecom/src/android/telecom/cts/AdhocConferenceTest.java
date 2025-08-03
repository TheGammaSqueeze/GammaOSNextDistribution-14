/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.telecom.cts;

import static android.telecom.Connection.STATE_DISCONNECTED;
import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;

import android.net.Uri;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.util.Log;
import android.util.Pair;

import com.android.server.telecom.flags.Flags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AdhocConferenceTest extends BaseTelecomTestWithMockServices {
    public static final List<Uri> PARTICIPANTS = new ArrayList<>(Arrays.asList(
            Uri.parse("tel:555-TEST"),
            Uri.parse("tel:555-TEST2")));

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mShouldTestTelecom && TestUtils.hasTelephonyFeature(mContext)) {
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
            mTelecomManager.registerPhoneAccount(TestUtils.TEST_PHONE_ACCOUNT);
            setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
            MockInCallService.setCallbacks(mInCallCallbacks);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mShouldTestTelecom && TestUtils.hasTelephonyFeature(mContext)) {
            mTelecomManager.unregisterPhoneAccount(TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
        }
        super.tearDown();
    }

    public void testStartConference() {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        Bundle extra = new Bundle();
        extra.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
        mTelecomManager.startConference(PARTICIPANTS, extra);
        ConnectionRequest request = verifyAdhocConferenceCall().second;
        // Verify that the conference is successfully created when a conference call is created.
        if (Flags.telecomResolveHiddenDependencies()) {
            assertTrue(connectionService.waitForEvent(
                    MockConnectionService.EVENT_CONNECTION_SERVICE_CREATE_CONFERENCE_COMPLETE));
        }
        assertTrue(request.isAdhocConferenceCall());
        assertEquals(2, request.getParticipants().size());
        assertTrue(request.getParticipants().containsAll(PARTICIPANTS));
    }

    public void testStartConferenceFailed() {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            //skip if telephony_feature is missing since we are placing an emergency call
            return;
        }
        List<Uri> participants = new ArrayList<>();
        Bundle extra = new Bundle();
        placeAndVerifyEmergencyCall(true);
        mTelecomManager.startConference(participants, extra);
        Conference conference = verifyAdhocConferenceCall().first;
        assertEquals(DisconnectCause.CANCELED, conference.getDisconnectCause().getCode());
    }

    public void testAddNewIncomingConference_onAnswer() {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        Bundle extra = new Bundle();
        mTelecomManager.addNewIncomingConference(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, extra);
        Pair<Conference, ConnectionRequest>  resultPair = verifyAdhocConferenceCall();
        assertTrue(resultPair.second.isAdhocConferenceCall());
        try {
            if (!mInCallCallbacks.lock.tryAcquire(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No conference call added.");
            }
        } catch (InterruptedException e) {
            Log.i(TestUtils.TAG, "Test interrupted");
        }
        MockConference conference = (MockConference) resultPair.first;
        Call call = mInCallCallbacks.getService().getLastConferenceCall();
        call.answer(VideoProfile.STATE_RX_ENABLED);
        conference.acquireLock(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(VideoProfile.STATE_RX_ENABLED, conference.getVideoState());
    }

    public void testAddNewIncomingConference_onReject() {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        Bundle extra = new Bundle();
        mTelecomManager.addNewIncomingConference(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, extra);
        Pair<Conference, ConnectionRequest>  resultPair = verifyAdhocConferenceCall();
        assertTrue(resultPair.second.isAdhocConferenceCall());
        try {
            if (!mInCallCallbacks.lock.tryAcquire(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No conference call added.");
            }
        } catch (InterruptedException e) {
            Log.i(TestUtils.TAG, "Test interrupted");
        }
        MockConference conference = (MockConference) resultPair.first;
        Call call = mInCallCallbacks.getService().getLastConferenceCall();
        call.reject(false /* rejectWithMessage */, null /* textMessage */);
        conference.acquireLock(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        for (Connection conn : conference.getConnections()) {
            assertEquals(STATE_DISCONNECTED, conn.getState());
            assertEquals(DisconnectCause.REJECTED, conn.getDisconnectCause().getCode());
        }
    }

    public void testAddNewIncomingConferenceFailed() {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        placeAndVerifyEmergencyCall(true);
        Bundle extra = new Bundle();
        mTelecomManager.addNewIncomingConference(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, extra);
        Conference conference = verifyAdhocConferenceCall().first;
        assertEquals(DisconnectCause.CANCELED, conference.getDisconnectCause().getCode());
    }

    public void testConferenceCallAddConferenceParticipants() {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        Bundle extra = new Bundle();
        mTelecomManager.addNewIncomingConference(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, extra);
        Pair<Conference, ConnectionRequest> resultPair = verifyAdhocConferenceCall();
        assertTrue(resultPair.second.isAdhocConferenceCall());
        try {
            if (!mInCallCallbacks.lock.tryAcquire(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No conference call added.");
            }
        } catch (InterruptedException e) {
            Log.i(TestUtils.TAG, "Test interrupted");
        }
        Call call = mInCallCallbacks.getService().getLastConferenceCall();
        call.answer(VideoProfile.STATE_AUDIO_ONLY);
        MockConference conference = (MockConference) resultPair.first;
        conference.setConnectionCapabilities(conference.getConnectionCapabilities()
                | Connection.CAPABILITY_ADD_PARTICIPANT);

        // Wait for the capability change to propagate before adding participants.
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return call.getDetails().can(Call.Details.CAPABILITY_ADD_PARTICIPANT);
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should have capability " + Call.Details.CAPABILITY_ADD_PARTICIPANT
        );

        conference.resetLock();
        call.addConferenceParticipants(PARTICIPANTS);
        assertTrue(conference.acquireLock(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(conference.mParticipants.containsAll(PARTICIPANTS));
    }
}
