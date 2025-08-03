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

package com.android.server.telecom.callfiltering;

import android.telecom.Log;

import com.android.internal.annotations.VisibleForTesting;

import com.android.server.telecom.Call;
import com.android.server.telecom.LogUtils;
import com.android.server.telecom.Ringer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * DndCallFilter is a incoming call filter that adds the
 * {@link android.telecom.Call.EXTRA_IS_SUPPRESSED_BY_DO_NOT_DISTURB } early in the call processing.
 * Adding {@link android.telecom.Call.EXTRA_IS_SUPPRESSED_BY_DO_NOT_DISTURB } before the Call object
 * is passed to all InCallServices is crucial for InCallServices that may disrupt the user and
 * potentially bypass the current Do Not Disturb settings.
 */
public class DndCallFilter extends CallFilter {

    private final Call mCall;
    private final Ringer mRinger;

    public DndCallFilter(Call call, Ringer ringer) {
        mCall = call;
        mRinger = ringer;
    }

    @VisibleForTesting
    @Override
    public CompletionStage<CallFilteringResult> startFilterLookup(CallFilteringResult result) {
        CompletableFuture<CallFilteringResult> resultFuture = new CompletableFuture<>();

        // start timer for query to NotificationManager
        Log.addEvent(mCall, LogUtils.Events.DND_PRE_CHECK_INITIATED);

        // query NotificationManager to determine if the call should ring or be suppressed
        boolean shouldSuppress = !mRinger.shouldRingForContact(mCall);

        // end timer
        Log.addEvent(mCall, LogUtils.Events.DND_PRE_CHECK_COMPLETED, shouldSuppress);

        // complete the resultFuture object
        resultFuture.complete(new CallFilteringResult.Builder()
                .setShouldAllowCall(true)
                .setShouldAddToCallLog(true)
                .setShouldShowNotification(true)
                .setDndSuppressed(shouldSuppress)
                .build());

        return resultFuture;
    }

}
