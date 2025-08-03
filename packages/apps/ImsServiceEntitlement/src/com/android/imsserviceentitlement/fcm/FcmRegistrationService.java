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

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.AsyncTask;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.imsserviceentitlement.R;
import com.android.imsserviceentitlement.job.JobManager;
import com.android.imsserviceentitlement.utils.TelephonyUtils;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.IOException;

/** A {@link JobService} that gets a FCM tokens for all active SIMs. */
public class FcmRegistrationService extends JobService {
    private static final String TAG = "IMSSE-FcmRegistrationService";

    private FirebaseInstanceId mFakeInstanceID = null;
    private FirebaseApp mApp = null;

    @VisibleForTesting AsyncTask<JobParameters, Void, Void> mOngoingTask;

    /** Enqueues a job for FCM registration. */
    public static void enqueueJob(Context context) {
        ComponentName componentName = new ComponentName(context, FcmRegistrationService.class);
        // No subscription id associated job, use {@link
        // SubscriptionManager#INVALID_SUBSCRIPTION_ID}.
        JobManager jobManager =
                JobManager.getInstance(
                        context, componentName, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        jobManager.registerFcmOnceNetworkReady();
    }

    @VisibleForTesting
    void setFakeInstanceID(FirebaseInstanceId instanceID) {
        mFakeInstanceID = instanceID;
    }

    @Override
    @VisibleForTesting
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    /** Returns a {@link FirebaseApp} instance, lazily initialized. */
    private FirebaseApp getFirebaseApp() {
        if (mApp == null) {
            try {
                mApp = FirebaseApp.getInstance();
            } catch (IllegalStateException e) {
                Log.d(TAG, "initialize FirebaseApp");
                mApp = FirebaseApp.initializeApp(
                        this,
                        new FirebaseOptions.Builder()
                                .setApplicationId(getResources().getString(R.string.fcm_app_id))
                                .setProjectId(getResources().getString(R.string.fcm_project_id))
                                .setApiKey(getResources().getString(R.string.fcm_api_key))
                                .build());
            }
        }
        return mApp;
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        mOngoingTask = new AsyncTask<JobParameters, Void, Void>() {
            @Override
            protected Void doInBackground(JobParameters... params) {
                onHandleWork(params[0]);
                return null;
            }
        };
        mOngoingTask.execute(params);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // Always re-run if job stopped.
    }

    /**
     * Registers to receive FCM messages published to subscribe topics under the retrieved token.
     * The token changes when the InstanceID becomes invalid (e.g. app data is deleted).
     */
    protected void onHandleWork(JobParameters params) {
        boolean wantsReschedule = false;
        for (int subId : TelephonyUtils.getSubIdsWithFcmSupported(this)) {
            if (!updateFcmToken(getFirebaseInstanceId(), subId)) {
                wantsReschedule = true;
            }
        }

        jobFinished(params, wantsReschedule);
    }

    /** Returns {@code false} if failed to get token. */
    private boolean updateFcmToken(FirebaseInstanceId instanceID, int subId) {
        Log.d(TAG, "FcmRegistrationService.updateFcmToken: subId=" + subId);
        String token = getTokenForSubId(instanceID, subId);
        if (token == null) {
            Log.d(TAG, "getToken null");
            return false;
        }
        Log.d(TAG, "FCM token: " + token + " subId: " + subId);
        FcmTokenStore.setToken(this, subId, token);
        return true;
    }

    private FirebaseInstanceId getFirebaseInstanceId() {
        return (mFakeInstanceID != null)
                ? mFakeInstanceID
                : FirebaseInstanceId.getInstance(getFirebaseApp());
    }

    private String getTokenForSubId(FirebaseInstanceId instanceID, int subId) {
        String token = null;
        try {
            token = instanceID.getToken(
                    TelephonyUtils.getFcmSenderId(this, subId),
                    FirebaseMessaging.INSTANCE_ID_SCOPE);
        } catch (IOException e) {
            Log.e(TAG, "Failed to get a new FCM token: " + e);
        }
        return token;
    }
}
