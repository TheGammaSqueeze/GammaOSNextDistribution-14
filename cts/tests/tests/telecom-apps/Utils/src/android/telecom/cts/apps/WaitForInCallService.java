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

package android.telecom.cts.apps;

import static android.telecom.Call.STATE_ACTIVE;
import static android.telecom.Call.STATE_AUDIO_PROCESSING;
import static android.telecom.Call.STATE_CONNECTING;
import static android.telecom.Call.STATE_DIALING;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_DISCONNECTING;
import static android.telecom.Call.STATE_HOLDING;
import static android.telecom.Call.STATE_NEW;
import static android.telecom.Call.STATE_RINGING;
import static android.telecom.Call.STATE_SELECT_PHONE_ACCOUNT;
import static android.telecom.Call.STATE_SIMULATED_RINGING;

import static org.junit.Assert.fail;

import android.telecom.Call;
import android.util.Log;

import java.util.List;

public class WaitForInCallService {
    private static final String DID_NOT_BIND_IN_TIME_ERR_MSG =
            "InCallServiceVerifier did NOT bind in time";
    private static final String CALL_COUNT_WAS_NOT_INCREMENTED_ERR_MSG =
            "Call Count was not incremented in time";

    public static void verifyCallState(InCallServiceMethods verifierMethods,
            String id, int targetCallState) {
        List<Call> mCalls = verifierMethods.getOngoingCalls();
        Call targetCall = getCallWithId(mCalls, id);
        boolean containsCall = targetCall != null;

        if ((targetCallState == STATE_DISCONNECTED
                || targetCallState == STATE_DISCONNECTING) && !containsCall) {
            return;
        }
        if (!containsCall) {
            fail("call is not in map");
        }
        assertCallState(targetCall, targetCallState);
    }


    public static void waitForInCallServiceBinding(InCallServiceMethods verifierMethods) {
        WaitUntil.waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return verifierMethods.isBound();
                    }
                },
                WaitUntil.DEFAULT_TIMEOUT_MS,
                DID_NOT_BIND_IN_TIME_ERR_MSG
        );
    }

    public static void waitUntilExpectCallCount(InCallServiceMethods verifierMethods,
            int expectedCallCount) {
        WaitUntil.waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expectedCallCount;
                    }

                    @Override
                    public Object actual() {
                        return verifierMethods.getCurrentCallCount();
                    }
                },
                WaitUntil.DEFAULT_TIMEOUT_MS,
                CALL_COUNT_WAS_NOT_INCREMENTED_ERR_MSG
        );
    }

    /***********************************************************
     /                 private methods
     /***********************************************************/
    private static Call getCallWithId(List<Call> calls, String id) {
        for (Call call : calls) {
            if (call.getDetails().getId().equals(id)) {
                return call;
            }
        }
        return null;
    }

    private static void assertCallState(final Call call, final int targetState) {
        WaitUntil.waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return targetState;
                    }

                    @Override
                    public Object actual() {
                        Log.i("tomsDebug", String.format("checking call=[%s]", call));
                        return call.getState();
                    }
                }, WaitUntil.DEFAULT_TIMEOUT_MS,
                "Expected CallState=[" + stateToString(targetState) + "];"
                        + " actual CallState[" + stateToString(call.getState()) + "]"
        );
    }

    private static String stateToString(int state) {
        switch (state) {
            case STATE_NEW:
                return "NEW";
            case STATE_RINGING:
                return "RINGING";
            case STATE_DIALING:
                return "DIALING";
            case STATE_ACTIVE:
                return "ACTIVE";
            case STATE_HOLDING:
                return "HOLDING";
            case STATE_DISCONNECTED:
                return "DISCONNECTED";
            case STATE_CONNECTING:
                return "CONNECTING";
            case STATE_DISCONNECTING:
                return "DISCONNECTING";
            case STATE_SELECT_PHONE_ACCOUNT:
                return "SELECT_PHONE_ACCOUNT";
            case STATE_SIMULATED_RINGING:
                return "SIMULATED_RINGING";
            case STATE_AUDIO_PROCESSING:
                return "AUDIO_PROCESSING";
            default:
                Log.i("tomsLog", String.format("Unknown state %d", state));
                return "UNKNOWN";
        }
    }
}
