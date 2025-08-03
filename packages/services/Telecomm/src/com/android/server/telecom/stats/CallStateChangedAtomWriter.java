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

import android.content.pm.PackageManager;
import android.telecom.DisconnectCause;
import android.telecom.Log;

import com.android.server.telecom.CallState;
import com.android.server.telecom.TelecomStatsLog;

/**
 * Collects and stores data for CallStateChanged atom for each call, and provide a
 * method to write the data to statsd whenever the call state changes.
 */
public class CallStateChangedAtomWriter {
    private boolean mIsSelfManaged = false;
    private boolean mIsExternalCall = false;
    private boolean mIsEmergencyCall = false;
    private int mUid = -1;
    private int mDurationSeconds = 0;
    private int mExistingCallCount = 0;
    private int mHeldCallCount = 0;
    private CallFailureCause mStartFailCause = CallFailureCause.NONE;
    private DisconnectCause mDisconnectCause = new DisconnectCause(DisconnectCause.UNKNOWN);

    /**
     * Write collected data and current call state to statsd.
     *
     * @param state Current call state.
     */
    public void write(int state) {
        TelecomStatsLog.write(TelecomStatsLog.CALL_STATE_CHANGED,
                state,
                state == CallState.DISCONNECTED ?
                    mDisconnectCause.getCode() : DisconnectCause.UNKNOWN,
                mIsSelfManaged,
                mIsExternalCall,
                mIsEmergencyCall,
                mUid,
                state == CallState.DISCONNECTED ? mDurationSeconds : 0,
                mExistingCallCount,
                mHeldCallCount,
                state == CallState.DISCONNECTED ?
                    mStartFailCause.getCode() : CallFailureCause.NONE.getCode());
    }

    public CallStateChangedAtomWriter setSelfManaged(boolean isSelfManaged) {
        mIsSelfManaged = isSelfManaged;
        return this;
    }

    public CallStateChangedAtomWriter setExternalCall(boolean isExternalCall) {
        mIsExternalCall = isExternalCall;
        return this;
    }

    public CallStateChangedAtomWriter setEmergencyCall(boolean isEmergencyCall) {
        mIsEmergencyCall = isEmergencyCall;
        return this;
    }

    public CallStateChangedAtomWriter setUid(int uid) {
        mUid = uid;
        return this;
    }

    public CallStateChangedAtomWriter setUid(String packageName, PackageManager pm) {
        try {
            final int uid = pm.getPackageUid(packageName, PackageManager.PackageInfoFlags.of(0));
            return setUid(uid);

        } catch (PackageManager.NameNotFoundException e) {
            Log.e(this, e, "Could not find the package");
        }
        return setUid(-1);
    }

    public CallStateChangedAtomWriter setDurationSeconds(int duration) {
        if (duration >= 0) {
            mDurationSeconds = duration;
        }
        return this;
    }

    public CallStateChangedAtomWriter setExistingCallCount(int count) {
        mExistingCallCount = count;
        return this;
    }

    public CallStateChangedAtomWriter increaseHeldCallCount() {
        mHeldCallCount++;
        return this;
    }

    public CallStateChangedAtomWriter setDisconnectCause(DisconnectCause cause) {
        mDisconnectCause = cause;
        return this;
    }

    public CallStateChangedAtomWriter setStartFailCause(CallFailureCause cause) {
        mStartFailCause = cause;
        return this;
    }
}
