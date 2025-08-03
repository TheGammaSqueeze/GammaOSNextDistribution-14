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

package com.android.imsserviceentitlement.job;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.SubscriptionManager;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.GuardedBy;

import com.android.imsserviceentitlement.utils.TelephonyUtils;

import java.time.Duration;

/** Manages all scheduled jobs and provides common job scheduler. */
public class JobManager {
    private static final String TAG = "IMSSE-JobManager";

    private static final int JOB_ID_BASE_INDEX = 1000;

    // Query entitlement status
    public static final int QUERY_ENTITLEMENT_STATUS_JOB_ID = 1;
    // Register FCM to listen push notification, this job not associated with subscription id.
    public static final int REGISTER_FCM_JOB_ID = 2;

    public static final String EXTRA_SLOT_ID = "SLOT_ID";
    public static final String EXTRA_RETRY_COUNT = "RETRY_COUNT";

    private final Context mContext;
    private final int mSubId;
    private final JobScheduler mJobScheduler;
    private final ComponentName mComponentName;

    // Cache subscription id associated {@link JobManager} objects for reusing.
    @GuardedBy("JobManager.class")
    private static final ArrayMap<String, JobManager> sInstances = new ArrayMap<>();

    private JobManager(Context context, ComponentName componentName, int subId) {
        this.mContext = context;
        this.mComponentName = componentName;
        this.mJobScheduler = context.getSystemService(JobScheduler.class);
        this.mSubId = subId;
    }

    /** Returns {@link JobManager} instance. */
    public static synchronized JobManager getInstance(
            Context context, ComponentName componentName, int subId) {
        String key = componentName.flattenToShortString() + "." + subId;
        JobManager instance = sInstances.get(key);
        if (instance != null) {
            return instance;
        }

        instance = new JobManager(context, componentName, subId);
        sInstances.put(key, instance);
        return instance;
    }

    private JobInfo.Builder newJobInfoBuilder(int jobId) {
        return newJobInfoBuilder(jobId, 0 /* retryCount */);
    }

    private JobInfo.Builder newJobInfoBuilder(int jobId, int retryCount) {
        JobInfo.Builder builder = new JobInfo.Builder(getJobIdWithSubId(jobId), mComponentName);
        putSubIdAndRetryExtra(builder, retryCount);
        return builder;
    }

    /**
     * Returns a new job id with {@code JOB_ID_BASE_INDEX} for separating job for different
     * subscription id, in order to avoid job be overrided for different SIM on multi SIM device.
     * Returns original {@code jobId} if the subscription id invalid. For example, if subscription
     * id be 8, the job id would be 8001, 8002, etc; if subscription id be -1, the job id would be
     * 1, 2, etc.
     */
    private int getJobIdWithSubId(int jobId) {
        if (SubscriptionManager.isValidSubscriptionId(mSubId)) {
            return JOB_ID_BASE_INDEX * mSubId + jobId;
        }
        return jobId;
    }

    /** Returns job id which remove {@code JOB_ID_BASE_INDEX}. */
    public static int getPureJobId(int jobId) {
        return jobId % JOB_ID_BASE_INDEX;
    }

    private void putSubIdAndRetryExtra(JobInfo.Builder builder, int retryCount) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, mSubId);
        bundle.putInt(EXTRA_SLOT_ID, TelephonyUtils.getSlotId(mContext, mSubId));
        bundle.putInt(EXTRA_RETRY_COUNT, retryCount);
        builder.setExtras(bundle);
    }

    /** Checks Entitlement Status once has network connection without retry and delay. */
    public void queryEntitlementStatusOnceNetworkReady() {
        queryEntitlementStatusOnceNetworkReady(/* retryCount= */ 0, Duration.ofSeconds(0));
    }

    /** Checks Entitlement Status once has network connection with retry count. */
    public void queryEntitlementStatusOnceNetworkReady(int retryCount) {
        queryEntitlementStatusOnceNetworkReady(retryCount, Duration.ofSeconds(0));
    }

    /** Checks Entitlement Status once has network connection with retry count and delay. */
    public void queryEntitlementStatusOnceNetworkReady(int retryCount, Duration delay) {
        Log.d(
                TAG,
                "schedule QUERY_ENTITLEMENT_STATUS_JOB_ID once has network connection, "
                        + "retryCount="
                        + retryCount
                        + ", delay="
                        + delay);
        JobInfo job =
                newJobInfoBuilder(QUERY_ENTITLEMENT_STATUS_JOB_ID, retryCount)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setMinimumLatency(delay.toMillis())
                        .build();
        mJobScheduler.schedule(job);
    }

    /** Registers FCM service to listen push notification once has network connection. */
    public void registerFcmOnceNetworkReady() {
        Log.d(TAG, "Schedule REGISTER_FCM_JOB_ID once has network connection.");
        JobInfo job =
                newJobInfoBuilder(REGISTER_FCM_JOB_ID)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build();
        mJobScheduler.schedule(job);
    }

    /**
     * Returns {@code true} if this job's subscription id still actived and still on same slot.
     * Returns {@code false} otherwise.
     */
    public static boolean isValidJob(Context context, final JobParameters params) {
        PersistableBundle bundle = params.getExtras();
        int subId =
                bundle.getInt(
                        SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        int slotId = bundle.getInt(EXTRA_SLOT_ID, SubscriptionManager.INVALID_SIM_SLOT_INDEX);

        // Avoids to do anything after user removed or swapped SIM
        if (!TelephonyUtils.isActivedSubId(context, subId)) {
            Log.d(TAG, "Stop reason: SUBID(" + subId + ") not point to active SIM.");
            return false;
        }

        // For example, the job scheduled for slot 1 then SIM been swapped to slot 2 and then start
        // this job. So, let's ignore this case.
        if (TelephonyUtils.getSlotId(context, subId) != slotId) {
            Log.d(TAG, "Stop reason: SLOTID(" + slotId + ") not matched.");
            return false;
        }

        return true;
    }
}
