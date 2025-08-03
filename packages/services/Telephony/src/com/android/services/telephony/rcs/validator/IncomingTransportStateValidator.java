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

package com.android.services.telephony.rcs.validator;

import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipMessage;
import android.util.ArrayMap;

/**
 * Tracks the incoming SIP message transport state from the ImsService to the remote IMS
 * application. Validates incoming SIP messages based on this state.
 */
public class IncomingTransportStateValidator implements SipMessageValidator {

    /**
     * The message transport is closed, meaning there can be no more incoming messages
     */
    private static final int STATE_CLOSED = 0;

    /**
     * The message transport is open and incoming traffic is not restricted.
     */
    private static final int STATE_OPEN = 1;

    private static final ArrayMap<Integer, String> ENUM_TO_STRING_MAP  = new ArrayMap<>(2);
    static {
        ENUM_TO_STRING_MAP.append(STATE_CLOSED, "CLOSED");
        ENUM_TO_STRING_MAP.append(STATE_OPEN, "OPEN");
    }

    private int mState = STATE_CLOSED;
    private int mReason = SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED;

    /**
     * The SIP message transport is open and will successfully validate SIP messages.
     */
    public void open() {
        mState = STATE_OPEN;
        mReason = -1;
    }

    /**
     * The SIP message transport is closed for incoming SIP messages.
     * @param reason The error reason sent in response to any incoming SIP messages requests.
     */
    public void close(int reason) {
        mState = STATE_CLOSED;
        mReason = reason;
    }

    @Override
    public ValidationResult validate(SipMessage message) {
        if (mState != STATE_OPEN) {
            return new ValidationResult(mReason,
                    "incoming transport closed");
        }
        return ValidationResult.SUCCESS;
    }

    @Override
    public String toString() {
        return "Incoming Transport State: " + ENUM_TO_STRING_MAP.getOrDefault(mState,
                String.valueOf(mState)) + ", reason: "
                + SipDelegateManager.MESSAGE_FAILURE_REASON_STRING_MAP.getOrDefault(mReason,
                String.valueOf(mReason));
    }
}
