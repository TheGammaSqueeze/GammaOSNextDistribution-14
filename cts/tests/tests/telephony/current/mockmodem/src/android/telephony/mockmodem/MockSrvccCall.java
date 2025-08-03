/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.telephony.mockmodem;

import static android.hardware.radio.ims.SrvccCall.CallSubState.PREALERTING;
import static android.hardware.radio.voice.Call.STATE_ACTIVE;
import static android.hardware.radio.voice.Call.STATE_ALERTING;
import static android.hardware.radio.voice.Call.STATE_DIALING;
import static android.hardware.radio.voice.Call.STATE_HOLDING;
import static android.hardware.radio.voice.Call.STATE_INCOMING;
import static android.hardware.radio.voice.Call.STATE_WAITING;
import static android.telephony.PreciseCallState.PRECISE_CALL_STATE_ACTIVE;
import static android.telephony.PreciseCallState.PRECISE_CALL_STATE_ALERTING;
import static android.telephony.PreciseCallState.PRECISE_CALL_STATE_DIALING;
import static android.telephony.PreciseCallState.PRECISE_CALL_STATE_DISCONNECTED;
import static android.telephony.PreciseCallState.PRECISE_CALL_STATE_HOLDING;
import static android.telephony.PreciseCallState.PRECISE_CALL_STATE_INCOMING;
import static android.telephony.PreciseCallState.PRECISE_CALL_STATE_WAITING;

import android.hardware.radio.ims.SrvccCall;

/**
 * Call information for SRVCC
 */
public class MockSrvccCall {
    private static final String TAG = "MockSrvccCall";

    /** Remote party nummber */
    private final String mAddress;
    /** Remote party name */
    private final String mName;
    /** Values are android.telephony.PreciseCallState_PRECISE_CALL_STATE_* constants */
    private final int mState;
    /** true if call is mobile terminated */
    private final boolean mIncoming;
    /** true if call is in pre-alerting state */
    private final boolean mPreAlerting;
    /** true if is multi-party call */
    private final boolean mIsMpty;

    public MockSrvccCall(SrvccCall call) {
        mAddress = call.number;
        mName = call.name;
        mState = convertCallState(call.callState);
        mIncoming = call.isMT;
        mPreAlerting = (call.callState == PREALERTING);
        mIsMpty = call.isMpty;
    }

    private int convertCallState(int state) {
        switch(state) {
            case STATE_ACTIVE: return PRECISE_CALL_STATE_ACTIVE;
            case STATE_HOLDING: return PRECISE_CALL_STATE_HOLDING;
            case STATE_DIALING: return PRECISE_CALL_STATE_DIALING;
            case STATE_ALERTING: return PRECISE_CALL_STATE_ALERTING;
            case STATE_INCOMING: return PRECISE_CALL_STATE_INCOMING;
            case STATE_WAITING: return PRECISE_CALL_STATE_WAITING;
            default: break;
        }
        return PRECISE_CALL_STATE_DISCONNECTED;
    }

    /** @return remote party nummber */
    public String getAddress() {
        return mAddress;
    }

    /** @return remote party name */
    public String getName() {
        return mName;
    }

    /** @return the call state */
    public int getState() {
        return mState;
    }

    /** @return true if call is mobile terminated */
    public boolean isIncoming() {
        return mIncoming;
    }

    /** @return true if call is in pre-alerting state */
    public boolean isPreAlerting() {
        return mPreAlerting;
    }

    /** @return true if is multi-party call */
    public boolean isMultiparty() {
        return mIsMpty;
    }
}
