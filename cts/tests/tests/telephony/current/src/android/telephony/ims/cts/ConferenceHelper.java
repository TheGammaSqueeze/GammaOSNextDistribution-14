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

package android.telephony.ims.cts;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConferenceHelper {

    private static final String TAG = "ConferenceHelper";

    private TestImsCallSessionImpl mConfSession = null;
    private TestImsCallSessionImpl mForeGroundSession = null;
    private TestImsCallSessionImpl mBackGroundSession = null;

    private final ConcurrentHashMap<String, TestImsCallSessionImpl> mSessions =
            new ConcurrentHashMap<String, TestImsCallSessionImpl>();

    public void addSession(TestImsCallSessionImpl session) {
        synchronized (mSessions) {
            mSessions.put(session.getCallId(), session);
        }
    }

    // delete the call session used for merge to connect the conference call.
    public void removeSession(TestImsCallSessionImpl session) {
        synchronized (mSessions) {
            mSessions.remove(session.getCallId(), session);
        }
    }

    public TestImsCallSessionImpl getActiveSession(String callId) {
        synchronized (mSessions) {
            if (mSessions.isEmpty()) {
                return null;
            }

            for (Map.Entry<String, TestImsCallSessionImpl> entry : mSessions.entrySet()) {
                if (entry.getKey().equals(callId)) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    public TestImsCallSessionImpl getHoldSession() {
        synchronized (mSessions) {
            if (mSessions.isEmpty()) {
                return null;
            }

            for (Map.Entry<String, TestImsCallSessionImpl> entry : mSessions.entrySet()) {
                TestImsCallSessionImpl callSession = entry.getValue();
                boolean isOnHold = callSession.isSessionOnHold();
                String foreGroundSessionCallId = mForeGroundSession.getCallId();

                if (isOnHold && !callSession.getCallId().equals(foreGroundSessionCallId)) {
                    return callSession;
                }
            }
        }

        return null;
    }

    public void setConferenceSession(TestImsCallSessionImpl session) {
        mConfSession = session;
    }

    public void setForeGroundSession(TestImsCallSessionImpl session) {
        mForeGroundSession = session;
    }

    public void setBackGroundSession(TestImsCallSessionImpl session) {
        mBackGroundSession = session;
    }

    public TestImsCallSessionImpl getConferenceSession() {
        return mConfSession;
    }

    public TestImsCallSessionImpl getForeGroundSession() {
        return mForeGroundSession;
    }

    public TestImsCallSessionImpl getBackGroundSession() {
        return mBackGroundSession;
    }

    public void clearSessions() {
        mConfSession  = null;
        mForeGroundSession = null;
        mBackGroundSession = null;
        mSessions.clear();
    }
}
