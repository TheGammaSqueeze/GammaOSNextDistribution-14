/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.telecom.Call;
import android.telecom.Connection;

/**
 * Verifies Telecom behavior with regards to interactions with a wired headset. These tests
 * validate behavior that occurs as a result of short pressing or long pressing a wired headset's
 * media button.
 */
public class WiredHeadsetTest extends BaseTelecomTestWithMockServices {

    // TODO(b/272362532): Figure out a way to mock out audio routing in CTS; this class depends on a
    // wired headset being attached to operate as expected.
    // We do this here instead of renaming the tests because the CTS test runner complains that the
    // class has no tests.
    private static final boolean DISABLE_TESTS = true;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mShouldTestTelecom && !DISABLE_TESTS) {
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testIncomingCallShortPress_acceptsCall() throws Exception {
        if (!mShouldTestTelecom || DISABLE_TESTS) {
            return;
        }

        addAndVerifyNewIncomingCall(createTestNumber(), null);
        final MockConnection connection = verifyConnectionForIncomingCall();

        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_RINGING);
        assertConnectionState(connection, Connection.STATE_RINGING);

        sendMediaButtonShortPress();
        assertCallState(call,  Call.STATE_ACTIVE);
        assertConnectionState(connection, Connection.STATE_ACTIVE);
    }

    public void testIncomingCallLongPress_rejectsCall() throws Exception {
        if (!mShouldTestTelecom || DISABLE_TESTS) {
            return;
        }

        addAndVerifyNewIncomingCall(createTestNumber(), null);
        final MockConnection connection = verifyConnectionForIncomingCall();

        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_RINGING);
        assertConnectionState(connection, Connection.STATE_RINGING);

        sendMediaButtonLongPress();
        assertCallState(call, Call.STATE_DISCONNECTED);
        assertConnectionState(connection, Connection.STATE_DISCONNECTED);
    }

    public void testInCallLongPress_togglesMute() throws Exception {
        if (!mShouldTestTelecom || DISABLE_TESTS) {
            return;
        }

        placeAndVerifyCall();
        final MockConnection connection = verifyConnectionForOutgoingCall();
        final MockInCallService incallService = mInCallCallbacks.getService();

        // Verify that sending short presses in succession toggles the mute state of the
        // connection.
        // Before the audio state is changed for the first time, the connection might not
        // know about its audio state yet.
        assertMuteState(incallService, false);
        sendMediaButtonLongPress();
        assertMuteState(connection, true);
        assertMuteState(incallService, true);
        sendMediaButtonLongPress();
        assertMuteState(connection, false);
        assertMuteState(incallService, false);
    }

    public void testInCallShortPress_hangupCall() throws Exception {
        if (!mShouldTestTelecom || DISABLE_TESTS) {
            return;
        }

        placeAndVerifyCall();
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_DIALING);

        connection.setActive();
        assertCallState(call, Call.STATE_ACTIVE);

        sendMediaButtonShortPress();
        assertCallState(call, Call.STATE_DISCONNECTED);
        assertConnectionState(connection, Connection.STATE_DISCONNECTED);
    }

    private void sendMediaButtonShortPress() throws Exception {
        sendMediaButtonPress(false /* longPress */);
    }

    private void sendMediaButtonLongPress() throws Exception {
        sendMediaButtonPress(true /* longPress */);
    }

    private void sendMediaButtonPress(boolean longPress) throws Exception {
        // request 3 seconds press when long press needed for stability
        final String command = "input keyevent " + (longPress ? "--longpress 3" : "--shortpress")
                + " KEYCODE_HEADSETHOOK";
        TestUtils.executeShellCommand(getInstrumentation(), command);
    }
}
