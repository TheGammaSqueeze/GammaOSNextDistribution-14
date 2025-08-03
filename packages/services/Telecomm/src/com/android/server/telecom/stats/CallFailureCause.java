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
 * limitations under the License
 */

package com.android.server.telecom.stats;

/**
 * Indicating the failure reason why a new call cannot be made.
 * The codes are synced with CallFailureCauseEnum defined in enums.proto.
 */
public enum CallFailureCause {
    /** The call is normally started. */
    NONE(0),
    /** Necessary parameters are invalid or null. */
    INVALID_USE(1),
    /** There is an emergency call ongoing. */
    IN_EMERGENCY_CALL(2),
    /** There is an live call that cannot be held. */
    CANNOT_HOLD_CALL(3),
    /** There are maximum number of outgoing calls already. */
    MAX_OUTGOING_CALLS(4),
    /** There are maximum number of ringing calls already. */
    MAX_RINGING_CALLS(5),
    /** There are maximum number of calls in hold already. */
    MAX_HOLD_CALLS(6),
    /* There are maximum number of self-managed calls already. */
    MAX_SELF_MANAGED_CALLS(7);

    private final int mCode;

    /**
     * Creates a new CallFailureCause.
     *
     * @param code The code for the failure cause.
     */
    CallFailureCause(int code) {
        mCode = code;
    }

    /**
     * Returns the code for the failure.
     *
     * @return The code for the failure cause.
     */
    public int getCode() {
        return mCode;
    }

    /**
     * Check if this enum represents a non-failure case.
     *
     * @return True if success.
     */
    public boolean isSuccess() {
        return this == NONE;
    }
}
