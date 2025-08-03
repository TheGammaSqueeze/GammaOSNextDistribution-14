/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.accessibilityservice.AccessibilityService;
import android.telecom.Call;
import android.telecom.Connection;

/**
 * Tests that the {@link AccessibilityService#performGlobalAction} for
 * {@link AccessibilityService#GLOBAL_ACTION_KEYCODE_HEADSETHOOK} can control calls.
 */
public class AccessibilitySystemActionTest extends BaseTelecomTestWithMockServices {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mShouldTestTelecom) {
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        }
    }

    public void testIncomingCall_headsetHookAction_acceptsCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        addAndVerifyNewIncomingCall(createTestNumber(), null);
        final MockConnection connection = verifyConnectionForIncomingCall();
        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_RINGING);
        assertConnectionState(connection, Connection.STATE_RINGING);

        performKeycodeHeadsetHookSystemAction();

        assertCallState(call, Call.STATE_ACTIVE);
        assertConnectionState(connection, Connection.STATE_ACTIVE);
    }

    public void testInCall_headsetHookAction_hangupCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        placeAndVerifyCall();
        final MockConnection connection = verifyConnectionForOutgoingCall();
        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_DIALING);
        connection.setActive();
        assertCallState(call, Call.STATE_ACTIVE);

        performKeycodeHeadsetHookSystemAction();

        assertCallState(call, Call.STATE_DISCONNECTED);
        assertConnectionState(connection, Connection.STATE_DISCONNECTED);
    }

    private void performKeycodeHeadsetHookSystemAction() throws Exception {
        TestUtils.executeShellCommand(getInstrumentation(), "cmd accessibility call-system-action "
                + AccessibilityService.GLOBAL_ACTION_KEYCODE_HEADSETHOOK);
    }
}
