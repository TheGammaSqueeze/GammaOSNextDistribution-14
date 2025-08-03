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

package com.android.car.carlauncher.homescreen.audio.dialer;

import android.telecom.Call;

import com.android.car.carlauncher.homescreen.audio.InCallModel;
import com.android.car.telephony.common.CallDetail;

import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.util.List;

/** A wrapper around InCallModel to track when an active call is in progress. */
public class DialerCardModel extends InCallModel {

    private boolean mHasActiveCall;
    private List<Integer> mAvailableRoutes;
    private int mActiveRoute;

    public DialerCardModel(Clock elapsedTimeClock) {
        super(elapsedTimeClock);
    }

    /** Indicates whether there is an active call or not. */
    public boolean hasActiveCall() {
        return mHasActiveCall;
    }

    @Override
    public void onCallAdded(Call call) {
        mHasActiveCall = call != null;
        super.onCallAdded(call);
    }

    @Override
    public void onCallRemoved(Call call) {
        mHasActiveCall = false;
        super.onCallRemoved(call);
    }

    @Override
    protected void handleActiveCall(@NotNull Call call) {
        CallDetail callDetails = CallDetail.fromTelecomCallDetail(call.getDetails());
        mAvailableRoutes = sInCallServiceManager.getSupportedAudioRoute(callDetails);
        mActiveRoute = sInCallServiceManager.getAudioRoute(
                CallDetail.fromTelecomCallDetail(call.getDetails()).getScoState());
        super.handleActiveCall(call);
    }

    /**
     * Returns audio routes supported by current call.
     */
    public List<Integer> getAvailableAudioRoutes() {
        return mAvailableRoutes;
    }

    /**
     * Returns current call audio state.
     */
    public int getActiveAudioRoute() {
        return mActiveRoute;
    }

    /**
     * Sets current call audio route.
     */
    public void setActiveAudioRoute(int audioRoute) {
        if (getCurrentCall() == null) {
            // AudioRouteButton is disabled if it is null. Simply ignore it.
            return;
        }
        sInCallServiceManager.setAudioRoute(audioRoute, getCurrentCall());
        mActiveRoute = audioRoute;
    }
}
