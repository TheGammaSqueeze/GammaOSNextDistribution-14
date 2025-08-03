/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.imsserviceentitlement.fcm;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.WorkerThread;

import java.util.concurrent.CountDownLatch;

/** Convenience methods for FCM. */
public final class FcmUtils {
    public static final String LOG_TAG = "IMSSE-FcmUtils";

    private static final long TOKEN_UPDATE_WAITING_SECONDS = 25L;

    private FcmUtils() {}

    /** Fetches FCM token, if it's not available via {@link FcmTokenStore#getToken}. */
    @WorkerThread
    public static void fetchFcmToken(Context context, int subId) {
        if (FcmTokenStore.hasToken(context, subId)) {
            Log.d(LOG_TAG, "FCM token available.");
            return;
        }

        Log.d(LOG_TAG, "FCM token unavailable. Try to update...");
        final CountDownLatch tokenUpdated = new CountDownLatch(1);
        final SharedPreferences.OnSharedPreferenceChangeListener listener =
                (s, k) -> {
                    Log.d(LOG_TAG, "FCM preference changed.");
                    if (FcmTokenStore.hasToken(context, subId)) {
                        tokenUpdated.countDown();
                    }
                };
        FcmTokenStore.registerTokenUpdateListener(context, listener);

        // Starts a JobIntentService to update FCM token by calling FCM API on a worker thread.
        FcmRegistrationService.enqueueJob(context);

        try {
            // Wait for 25s. If FCM token update failed/timeout, we will let user see
            // the error message returned by server. Then user can manually retry.
            if (tokenUpdated.await(TOKEN_UPDATE_WAITING_SECONDS, SECONDS)) {
                Log.d(LOG_TAG, "FCM token updated.");
            } else {
                Log.d(LOG_TAG, "FCM token update failed.");
            }
        } catch (InterruptedException e) {
            // Do nothing
            Log.d(LOG_TAG, "FCM token update interrupted.");
        }
        FcmTokenStore.unregisterTokenUpdateListener(context, listener);
    }
}
