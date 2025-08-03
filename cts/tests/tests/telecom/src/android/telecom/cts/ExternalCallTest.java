/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package android.telecom.cts;

import static android.media.AudioManager.MODE_IN_CALL;
import static android.media.AudioManager.MODE_NORMAL;
import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;

import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.PhoneAccountHandle;

/**
 * Tests which verify functionality related to {@link android.telecom.Connection}s and
 * {@link android.telecom.Call}s with the
 * {@link android.telecom.Connection#PROPERTY_IS_EXTERNAL_CALL} and
 * {@link android.telecom.Call.Details#PROPERTY_IS_EXTERNAL_CALL} properties, respectively, set.
 */
public class ExternalCallTest extends BaseTelecomTestWithMockServices {
    public static final int CONNECTION_PROPERTIES = Connection.PROPERTY_IS_EXTERNAL_CALL;
    public static final int CONNECTION_CAPABILITIES = Connection.CAPABILITY_CAN_PULL_CALL;

    private MockConnection mExternalConnection;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!mShouldTestTelecom) return;
        setupConnectionService(
                new MockConnectionService() {
                    @Override
                    public Connection onCreateOutgoingConnection(
                            PhoneAccountHandle connectionManagerPhoneAccount,
                            ConnectionRequest request) {
                        Connection connection = super.onCreateOutgoingConnection(
                                connectionManagerPhoneAccount,
                                request);
                        if (!request.getAddress().equals(TEST_EMERGENCY_URI)) {
                            // Only get a reference to the non-emergency connection in relation to
                            // pulling.
                            mExternalConnection = (MockConnection) connection;
                            // Modify the connection object created with local values.
                            connection.setConnectionCapabilities(CONNECTION_CAPABILITIES);
                            connection.setConnectionProperties(CONNECTION_PROPERTIES);
                        }

                        lock.release();
                        return connection;
                    }
                }, FLAG_REGISTER | FLAG_ENABLE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Tests that a request to pull an external call via {@link Call#pullExternalCall()} is
     * communicated to the {@link Connection} via {@link Connection#onPullExternalCall()}.
     */
    public void testPullExternalCall() {
        if (!mShouldTestTelecom) {
            return;
        }
        Call call = placeExternalCall();

        final TestUtils.InvokeCounter counter = mExternalConnection.getInvokeCounter(
                MockConnection.ON_PULL_EXTERNAL_CALL);
        call.pullExternalCall();
        counter.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
    }

    /**
     * Tests that a request to pull an external call via {@link Call#pullExternalCall()} is
     * not completed when {@link Call.Details#CAPABILITY_CAN_PULL_CALL} is not on the call.
     */
    public void testNonPullableExternalCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        Call call = placeExternalCall();

        // Remove the pullable attribute of the connection.
        mExternalConnection.setConnectionCapabilities(0);
        assertCallCapabilities(call, 0);
        TestUtils.waitOnAllHandlers(getInstrumentation());

        final TestUtils.InvokeCounter counter = mExternalConnection.getInvokeCounter(
                MockConnection.ON_PULL_EXTERNAL_CALL);
        // Try to pull -- we expect Telecom to absorb the request since the call is not pullable.
        call.pullExternalCall();
        counter.waitForCount(5000L/*mS*/);
        assertEquals(0, counter.getInvokeCount());
    }


    /**
     * Tests that when there is an external call and an emergency call is placed, the
     * CAPABILITY_CAN_PLACE_CALL capability is removed.
     */
    public void testPullCallCapabilityRemovedInEmergencyCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
        Call call = placeExternalCall();

        placeAndVerifyEmergencyCall(true /*supportsHold*/);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);
        assertIsInCall(true);
        assertIsInManagedCall(true);
        TestUtils.waitOnAllHandlers(getInstrumentation());

        // Ensure that an emergency call removes the ability to pull the call.
        assertDoesNotHaveCallCapabilities(call, Call.Details.CAPABILITY_CAN_PULL_CALL);
    }

    /**
     * Tests that when an InCallService tries to pull an external call anyway while we are in an
     * ongoing emergency call, the request is not completed.
     */
    public void testTryToPullCallWhileInEmergencyCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
        Call call = placeExternalCall();

        placeAndVerifyEmergencyCall(true /*supportsHold*/);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);
        assertIsInCall(true);
        assertIsInManagedCall(true);
        TestUtils.waitOnAllHandlers(getInstrumentation());

        final TestUtils.InvokeCounter counter = mExternalConnection.getInvokeCounter(
                MockConnection.ON_PULL_EXTERNAL_CALL);
        // Try to pull -- we expect Telecom to absorb the request since we are on an emergency
        // call.
        call.pullExternalCall();
        counter.waitForCount(5000L /*mS*/);
        assertEquals(0, counter.getInvokeCount());
    }

    /**
     * test to check external call and pull external call with call and connection states check
     */
    public void testExternalCallAndPullCall() throws Exception {
        if (!mShouldTestTelecom  || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }

        Call call = placeExternalCall();

        // Set the connection active.
        mExternalConnection.setActive();

        // Check with Telecom if we're in a call. Should not be in call.
        assertIsInCall(false);
        assertIsInManagedCall(false);

        // Ensure AudioManager has Normal audio mode: not ringing and no call established.
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertAudioMode(audioManager, MODE_NORMAL);

        // Call and Connection state should be active
        assertCallState(call, Call.STATE_ACTIVE);
        assertConnectionState(mExternalConnection, Connection.STATE_ACTIVE);

        // Call properties should have PROPERTY_IS_EXTERNAL_CALL
        assertTrue(call.getDetails().hasProperty(Call.Details.PROPERTY_IS_EXTERNAL_CALL));

        // pull the external call
        final TestUtils.InvokeCounter counter = mExternalConnection.getInvokeCounter(
                MockConnection.ON_PULL_EXTERNAL_CALL);
        call.pullExternalCall();
        counter.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);

        mExternalConnection.setConnectionProperties(0);
        // Set the connection active.
        //mExternalConnection.setActive();

        // Call and Connection state should be active
        assertCallState(call, Call.STATE_ACTIVE);
        assertConnectionState(mExternalConnection, Connection.STATE_ACTIVE);

        // audio mode should be In call audio mode. A telephony call is established.
        assertAudioMode(audioManager, MODE_IN_CALL);

        // Call properties should have PROPERTY_IS_EXTERNAL_CALL
        assertFalse(call.getDetails().hasProperty(Call.Details.PROPERTY_IS_EXTERNAL_CALL));

        // Check with Telecom if we're in a call. should be in call.
        assertIsInCall(true);
        assertIsInManagedCall(true);

        call.disconnect();

        assertIsInCall(false);
        assertIsInManagedCall(false);
    }

    private Call placeExternalCall() {
        Uri testNumber = createRandomTestNumber();
        Bundle extras = new Bundle();
        extras.putParcelable(TestUtils.EXTRA_PHONE_NUMBER, testNumber);
        placeAndVerifyCall(extras);
        verifyConnectionForOutgoingCall(testNumber);

        Call call = getInCallService().getLastCall();

        assertCallState(call, Call.STATE_DIALING);
        assertCallProperties(call, Call.Details.PROPERTY_IS_EXTERNAL_CALL);
        assertCallCapabilities(call, Call.Details.CAPABILITY_CAN_PULL_CALL);
        return call;
    }
}
