/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.verifier.telecom;

import android.telecom.Call;

import com.android.compatibility.common.util.ApiTest;

@ApiTest(apis={"android.telecom.ConnectionService"})
public class CtsIncomingCall {
    private static final CtsIncomingCall INSTANCE = new CtsIncomingCall();
    private Call mCall;
    private boolean mAcceptWhenLocked;

    public static CtsIncomingCall getInstance() {
        return INSTANCE;
    }

    public void setCall(Call call) {
        mCall = call;
        mAcceptWhenLocked = false;
    }

    public void disconnectCall() {
        if (mCall != null) {
            mCall.disconnect();
            mCall = null;
        }
    }

    public void setAcceptWhenLocked(boolean acceptWhenLocked) {
        if (mCall != null) {
            mAcceptWhenLocked = acceptWhenLocked;
        }
    }

    public boolean isAcceptWhenLocked() {
        return mAcceptWhenLocked;
    }
}
