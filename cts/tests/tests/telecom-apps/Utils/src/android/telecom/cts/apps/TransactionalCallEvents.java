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

import android.os.Bundle;
import android.telecom.CallEndpoint;
import android.telecom.CallEventCallback;

import java.util.List;

public class TransactionalCallEvents implements CallEventCallback {
    private boolean mIsMuted = false;
    private CallEndpoint mCurrentCallEndpoint;
    private List<CallEndpoint> mCallEndpoints;

    public CallEndpoint getCurrentCallEndpoint() {
        return mCurrentCallEndpoint;
    }

    public List<CallEndpoint> getCallEndpoints() {
        return mCallEndpoints;
    }

    public boolean getIsMuted() {
        return mIsMuted;
    }

    @Override
    public void onCallEndpointChanged(CallEndpoint newCallEndpoint) {
        mCurrentCallEndpoint = newCallEndpoint;
    }

    @Override
    public void onAvailableCallEndpointsChanged(List<CallEndpoint> availableEndpoints) {
        mCallEndpoints = availableEndpoints;
    }

    @Override
    public void onMuteStateChanged(boolean isMuted) {
        mIsMuted = isMuted;
    }

    @Override
    public void onCallStreamingFailed(int reason) {

    }

    @Override
    public void onEvent(String event, Bundle extras) {

    }
}
