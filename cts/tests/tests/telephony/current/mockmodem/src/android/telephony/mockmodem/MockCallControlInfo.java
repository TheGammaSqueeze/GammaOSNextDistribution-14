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

import android.hardware.radio.voice.LastCallFailCause;
import android.hardware.radio.voice.LastCallFailCauseInfo;
import android.util.Log;

public class MockCallControlInfo {
    public static final int CALL_NO_FAIL_BITMASK = 0;
    public static final int CALL_DIALING_FAIL_BITMASK = 1;
    public static final int CALL_ALERTING_FAIL_BITMASK = 2;
    public static final int CALL_INCOMING_FAIL_BITMASK = 4;
    public static final int CALL_STATE_DURATION_INFINITY = -1;

    // Default value
    public static final int DEFAULT_DIALING_DURATION_IN_MS = 500;
    // Ringback tone should happen during dialing to active
    // The value should be less than dialing duration + alerting duration
    public static final int DEFAULT_RINGBACK_TONE_IN_MS = 300;
    public static final int DEFAULT_ALERTING_DURATION_IN_MS = 500;
    public static final int DEFAULT_ACTIVE_DURATION_IN_MS = CALL_STATE_DURATION_INFINITY;
    public static final int DEFAULT_DISCONNECTING_DURATION_IN_MS = 10;
    public static final int DEFAULT_INCOMING_DURATION_IN_MS = CALL_STATE_DURATION_INFINITY;
    public static final int DEFAULT_CALL_STATE_FAIL_BITMASK = CALL_NO_FAIL_BITMASK;
    public static final int DEFAULT_DIALING_FAIL_DURATION_IN_MS = 100;
    public static final int DEFAULT_ALERTING_FAIL_DURATION_IN_MS = 100;
    public static final int DEFAULT_INCOMING_FAIL_DURATION_IN_MS = 100;
    public static final int DEFAULT_CALL_END_REASON = LastCallFailCause.NORMAL;
    public static final String DEFAULT_CALL_END_VENDOR_CAUSE = "Mock call end reason - normal";

    private String mTag = "MockCallControlInfo";
    private long mDialingDurationInMs;
    private long mRingbackToneTimeInMs;
    private long mAlertingDurationInMs;
    private long mActiveDurationInMs;
    private long mDisconnectingDurationInMs;
    private long mIncomingDurationInMs;
    private int mCallStateFailBitMask;
    private LastCallFailCauseInfo mCallEndReasonInfo;
    private boolean mRingbackToneState;

    public MockCallControlInfo() {
        mDialingDurationInMs = DEFAULT_DIALING_DURATION_IN_MS;
        mRingbackToneTimeInMs = DEFAULT_RINGBACK_TONE_IN_MS;
        mAlertingDurationInMs = DEFAULT_ALERTING_DURATION_IN_MS;
        mActiveDurationInMs = DEFAULT_ACTIVE_DURATION_IN_MS;
        mDisconnectingDurationInMs = DEFAULT_DISCONNECTING_DURATION_IN_MS;
        mIncomingDurationInMs = DEFAULT_INCOMING_DURATION_IN_MS;
        mCallStateFailBitMask = DEFAULT_CALL_STATE_FAIL_BITMASK;
        mCallEndReasonInfo = new LastCallFailCauseInfo();
        mCallEndReasonInfo.causeCode = DEFAULT_CALL_END_REASON;
        mCallEndReasonInfo.vendorCause = DEFAULT_CALL_END_VENDOR_CAUSE;
        mRingbackToneState = false;
    }

    public void dump() {
        Log.d(
                mTag,
                "mDialingDurationInMs = "
                        + mDialingDurationInMs
                        + ", mRingbackToneTimeInMs = "
                        + mRingbackToneTimeInMs
                        + ", mAlertingDurationInMs = "
                        + mAlertingDurationInMs
                        + ", mActiveDurationInMs = "
                        + mActiveDurationInMs
                        + ", mDisconnectingDurationInMs = "
                        + mDisconnectingDurationInMs
                        + ", mIncomingDurationInMs = "
                        + mIncomingDurationInMs
                        + ", mCallStateFailBitMask = "
                        + mCallStateFailBitMask
                        + ", mCallEndReasonInfo.causeCode = "
                        + mCallEndReasonInfo.causeCode
                        + ", mCallEndReasonInfo.vendorCause = "
                        + mCallEndReasonInfo.vendorCause
                        + ", mRingbackTone = "
                        + mRingbackToneState);
    }

    public long getDialingDurationInMs() {
        return mDialingDurationInMs;
    }

    public void setDialingDurationInMs(long dialingDurationInMs) {
        mDialingDurationInMs = dialingDurationInMs;
    }

    public long getRingbackToneTimeInMs() {
        return mRingbackToneTimeInMs;
    }

    public void setRingbackToneTimeInMs(long ringbackToneTimeInMs) {
        mRingbackToneTimeInMs = ringbackToneTimeInMs;
    }

    public long getAlertingDurationInMs() {
        return mAlertingDurationInMs;
    }

    public void setAlertingDurationInMs(long alertingDurationInMs) {
        mAlertingDurationInMs = alertingDurationInMs;
    }

    public long getActiveDurationInMs() {
        return mActiveDurationInMs;
    }

    public void setActiveDurationInMs(long activeDurationInMs) {
        mActiveDurationInMs = activeDurationInMs;
    }

    public long getDisconnectingDurationInMs() {
        return mDisconnectingDurationInMs;
    }

    public void setDisconnectingDurationInMs(long disconnectingDurationInMs) {
        mDisconnectingDurationInMs = disconnectingDurationInMs;
    }

    public long getIncomingDurationInMs() {
        return mIncomingDurationInMs;
    }

    public void setIncomingDurationInMs(long incomingDurationInMs) {
        mIncomingDurationInMs = incomingDurationInMs;
    }

    public int getCallStateFailBitMask() {
        return mCallStateFailBitMask;
    }

    public void setCallStateFailBitMask(int bitMask) {
        mCallStateFailBitMask = bitMask;
    }

    public LastCallFailCauseInfo getCallEndInfo() {
        return mCallEndReasonInfo;
    }

    public void setCallEndInfo(int reason, String vendorCause) {
        mCallEndReasonInfo.causeCode = reason;
        mCallEndReasonInfo.vendorCause = vendorCause;
    }

    public boolean getRingbackToneState() {
        return mRingbackToneState;
    }

    public void setRingbackToneState(boolean ringbackToneState) {
        mRingbackToneState = ringbackToneState;
    }
}
