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

package com.android.safetycenter.pendingintents;

import static android.Manifest.permission.START_TASKS_FROM_RECENTS;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import com.android.modules.utils.build.SdkLevel;

/** A class to facilitate sending {@link PendingIntent}s associated with Safety Center. */
@RequiresApi(TIRAMISU)
public final class PendingIntentSender {

    private PendingIntentSender() {}

    private static final String TAG = "PendingIntentSender";

    /**
     * Sends a {@link PendingIntent}.
     *
     * <p>On U+, launching activities in the background is opt-in by using {@link
     * ActivityOptions#MODE_BACKGROUND_ACTIVITY_START_ALLOWED}. This call opts-in this behavior as
     * the Safety Center intents come from trusted sources and are used for navigation purposes.
     */
    public static void send(PendingIntent pendingIntent) throws PendingIntent.CanceledException {
        send(pendingIntent, createActivityOptions(pendingIntent, /* launchTaskId= */ null));
    }

    /** Same as {@link #send(PendingIntent)} but with the given optional {@code launchTaskId}. */
    @RequiresPermission(START_TASKS_FROM_RECENTS)
    public static void send(PendingIntent pendingIntent, @Nullable Integer launchTaskId)
            throws PendingIntent.CanceledException {
        send(pendingIntent, createActivityOptions(pendingIntent, launchTaskId));
    }

    private static void send(PendingIntent pendingIntent, @Nullable ActivityOptions activityOptions)
            throws PendingIntent.CanceledException {
        if (activityOptions == null) {
            pendingIntent.send();
            return;
        }
        pendingIntent.send(
                /* context= */ null,
                /* code= */ 0,
                /* intent= */ null,
                /* onFinished= */ null,
                /* handler= */ null,
                /* requiredPermission= */ null,
                activityOptions.toBundle());
    }

    /**
     * Same as {@link #send(PendingIntent)} but returns whether the call was successful instead of
     * throwing a {@link PendingIntent#CanceledException}.
     */
    public static boolean trySend(PendingIntent pendingIntent) {
        return trySend(
                pendingIntent, createActivityOptions(pendingIntent, /* launchTaskId= */ null));
    }

    /**
     * Same as {@link #send(PendingIntent, Integer)} but returns whether the call was successful
     * instead of throwing a {@link PendingIntent#CanceledException}.
     */
    @RequiresPermission(START_TASKS_FROM_RECENTS)
    public static boolean trySend(PendingIntent pendingIntent, @Nullable Integer launchTaskId) {
        return trySend(pendingIntent, createActivityOptions(pendingIntent, launchTaskId));
    }

    private static boolean trySend(
            PendingIntent pendingIntent, @Nullable ActivityOptions activityOptions) {
        try {
            send(pendingIntent, activityOptions);
            return true;
        } catch (PendingIntent.CanceledException ex) {
            Log.e(
                    TAG,
                    "Couldn't send PendingIntent: "
                            + pendingIntent
                            + " with ActivityOptions:"
                            + activityOptions,
                    ex);
            return false;
        }
    }

    @Nullable
    private static ActivityOptions createActivityOptions(
            PendingIntent pendingIntent, @Nullable Integer launchTaskId) {
        if (!pendingIntent.isActivity()) {
            return null;
        }
        if (launchTaskId == null && !SdkLevel.isAtLeastU()) {
            return null;
        }
        ActivityOptions activityOptions = ActivityOptions.makeBasic();
        if (launchTaskId != null) {
            activityOptions.setLaunchTaskId(launchTaskId);
        }
        if (SdkLevel.isAtLeastU()) {
            setBackgroundActivityStartModeAllowed(activityOptions);
        }
        return activityOptions;
    }

    @RequiresApi(UPSIDE_DOWN_CAKE)
    private static void setBackgroundActivityStartModeAllowed(ActivityOptions activityOptions) {
        activityOptions.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
    }
}
