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
 * limitations under the License
 */

package com.android.server.telecom;

import static com.android.server.telecom.CallAudioRouteAdapter.PENDING_ROUTE_FAILED;

import android.media.AudioManager;

import java.util.ArrayList;

/**
 * Used to represent the intermediate state during audio route switching.
 * Usually, audio route switching start with a communication device setting request to audio
 * framework and will be completed with corresponding success broadcasts or messages. Instance of
 * this class is responsible for tracking the pending success signals according to the original
 * audio route and the destination audio route of this switching.
 */
public class PendingAudioRoute {
    private CallAudioRouteController mCallAudioRouteController;
    private AudioManager mAudioManager;
    /**
     * The {@link AudioRoute} that this pending audio switching started with
     */
    private AudioRoute mOrigRoute;
    /**
     * The expected destination {@link AudioRoute} of this pending audio switching, can be changed
     * by new switching request during the ongoing switching
     */
    private AudioRoute mDestRoute;
    private ArrayList<Integer> mPendingMessages;
    private boolean mActive;
    PendingAudioRoute(CallAudioRouteController controller, AudioManager audioManager) {
        mCallAudioRouteController = controller;
        mAudioManager = audioManager;
        mPendingMessages = new ArrayList<>();
        mActive = false;
    }

    void setOrigRoute(boolean active, AudioRoute origRoute) {
        origRoute.onOrigRouteAsPendingRoute(active, this, mAudioManager);
        mOrigRoute = origRoute;
    }

    AudioRoute getOrigRoute() {
        return mOrigRoute;
    }

    void setDestRoute(boolean active, AudioRoute destRoute) {
        destRoute.onDestRouteAsPendingRoute(active, this, mAudioManager);
        mActive = active;
        mDestRoute = destRoute;
    }

    AudioRoute getDestRoute() {
        return mDestRoute;
    }

    public void addMessage(int message) {
        mPendingMessages.add(message);
    }

    public void onMessageReceived(int message) {
        if (message == PENDING_ROUTE_FAILED) {
            // Fallback to base route
            mDestRoute = mCallAudioRouteController.getBaseRoute(true);
            mCallAudioRouteController.sendMessageWithSessionInfo(
                    CallAudioRouteAdapter.EXIT_PENDING_ROUTE);
        }

        // Removes the first occurrence of the specified message from this list, if it is present.
        mPendingMessages.remove((Object) message);
        evaluatePendingState();
    }

    public void evaluatePendingState() {
        if (mPendingMessages.isEmpty()) {
            mCallAudioRouteController.sendMessageWithSessionInfo(
                    CallAudioRouteAdapter.EXIT_PENDING_ROUTE);
        }
    }

    public boolean isActive() {
        return mActive;
    }
}
