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

import android.content.Context;
import android.telecom.CallEndpoint;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.VideoProfile;
import android.util.Log;

import java.util.List;

public class VoipConnection extends Connection {
    private static final String TAG = VoipConnection.class.getSimpleName();
    private boolean mIsMuted = false;
    private CallEndpoint mCallEndpoint = null;
    private List<CallEndpoint> mCallEndpoints = null;
    private CallResources mCallResources = null;
    private final Context mContext;
    private final boolean mIsOutgoingCall;
    private boolean mHasUpdatedCallStyleNotification = false;

    public VoipConnection(Context context, boolean isOutgoingCall) {
        mContext = context;
        mIsOutgoingCall = isOutgoingCall;
    }

    public boolean isMuted() {
        return mIsMuted;
    }

    public List<CallEndpoint> getCallEndpoints() {
        return mCallEndpoints;
    }

    public CallEndpoint getCurrentCallEndpointFromCallback() {
        return mCallEndpoint;
    }

    public void setIdAndResources(String id, CallResources callResources) {
        mCallResources = callResources;
        mCallResources.setCallId(id);
    }

    public CallResources getCallResources() {
        return mCallResources;
    }

    public void setCallToDialing() {
        this.setDialing();
    }

    public void setCallToRinging() {
        this.setRinging();
    }

    public void setCallToActive() {
        this.setActive();
        if (!mIsOutgoingCall && !mHasUpdatedCallStyleNotification) {
            mCallResources.updateNotificationToOngoing(mContext);
            mHasUpdatedCallStyleNotification = true;
        }
    }

    public void setCallToInactive() {
        this.setOnHold();
    }

    public void setCallToDisconnected(Context context) {
        this.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        mCallResources.destroyResources(context);
    }

    public void setCallToDisconnected(Context context, DisconnectCause cause) {
        this.setDisconnected(cause);
        mCallResources.destroyResources(context);
    }

    @Override
    public void onStateChanged(int callState) {

    }

    @Override
    public void onCallEndpointChanged(CallEndpoint callEndpoint) {
        mCallEndpoint = callEndpoint;
    }


    @Override
    public void onAvailableCallEndpointsChanged(List<CallEndpoint> endpoints) {
        mCallEndpoints = endpoints;
    }

    @Override
    public void onHold() {
        setOnHold();
        super.onHold();
    }

    @Override
    public void onUnhold() {
        setActive();
        super.onUnhold();
    }

    @Override
    public void onAnswer(int videoState) {
        setVideoState(videoState);
        setActive();
        mCallResources.updateNotificationToOngoing(mContext);
        mHasUpdatedCallStyleNotification = true;
        super.onAnswer(videoState);
    }

    @Override
    public void onAnswer() {
        mCallResources.updateNotificationToOngoing(mContext);
        mHasUpdatedCallStyleNotification = true;
        onAnswer(VideoProfile.STATE_AUDIO_ONLY);
    }

    @Override
    public void onDisconnect() {
        mCallResources.destroyResources(mContext);
        super.onDisconnect();
    }

    @Override
    public void onMuteStateChanged(boolean isMuted) {
        super.onMuteStateChanged(isMuted);
        mIsMuted = isMuted;
    }

    /**
     * Helper that removes the Connection.CAPABILITY_HOLD && Connection.CAPABILITY_SUPPORT_HOLD
     * capabilities from a given Connection.
     */
    public void clearHoldCapabilities() {
        Log.i(TAG, String.format("Current capabilities as list=[%s]",
                Connection.capabilitiesToString(this.getConnectionCapabilities())));
        int mask = (1 << 31) - 1;
        int holdCapabilities = Connection.CAPABILITY_HOLD | Connection.CAPABILITY_SUPPORT_HOLD;
        int clearHold = (~holdCapabilities) & mask;
        int finalCaps = this.getConnectionCapabilities() & clearHold;
        this.setConnectionCapabilities(finalCaps);
        Log.i(TAG, String.format("Final capabilities as list=[%s]",
                Connection.capabilitiesToString(this.getConnectionCapabilities())));
    }
}
