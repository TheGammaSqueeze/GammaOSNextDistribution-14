/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.networkstack.ipmemorystore;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.net.ipmemorystore.IOnStatusListener;
import android.net.ipmemorystore.Status;
import android.net.ipmemorystore.StatusParcelable;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Regular maintenance job service.
 * @hide
 */
public class RegularMaintenanceJobService extends JobService {
    // Must be unique within the system server uid.
    public static final int REGULAR_MAINTENANCE_ID = 3345678;

    /**
     * Class for interrupt check of maintenance job.
     */
    public static final class InterruptMaintenance {
        private volatile boolean mIsInterrupted;
        private final int mJobId;

        public InterruptMaintenance(int jobId) {
            mJobId = jobId;
            mIsInterrupted = false;
        }

        public int getJobId() {
            return mJobId;
        }

        public void setInterrupted(boolean interrupt) {
            mIsInterrupted = interrupt;
        }

        public boolean isInterrupted() {
            return mIsInterrupted;
        }
    }

    private static final CopyOnWriteArrayList<InterruptMaintenance> sInterruptList =
            new CopyOnWriteArrayList<>();
    private static IpMemoryStoreService sIpMemoryStoreService;

    @Override
    public boolean onStartJob(JobParameters params) {
        if (sIpMemoryStoreService == null) {
            Log.wtf("RegularMaintenanceJobService",
                    "Can not start job because sIpMemoryStoreService is null.");
            return false;
        }
        final InterruptMaintenance im = new InterruptMaintenance(params.getJobId());
        sInterruptList.add(im);

        sIpMemoryStoreService.fullMaintenance(new IOnStatusListener() {
            @Override
            public void onComplete(final StatusParcelable statusParcelable) throws RemoteException {
                final Status result = new Status(statusParcelable);
                if (!result.isSuccess()) {
                    Log.e("RegularMaintenanceJobService", "Regular maintenance failed."
                            + " Error is " + result.resultCode);
                }
                sInterruptList.remove(im);
                callJobFinished(params, !result.isSuccess());
            }

            @Override
            public int getInterfaceVersion() {
                return this.VERSION;
            }

            @Override
            public String getInterfaceHash() {
                return this.HASH;
            }

            @Override
            public IBinder asBinder() {
                return null;
            }
        }, im);
        return true;
    }

    // Unfortunately jobFinished is final in JobService and sends a message through a maze
    // of messengers and callbacks, all of which are private and/or final. Although this method
    // represents a small change to the production code, it looks like a reasonably safe way
    // to write some of the tests for this service.
    @VisibleForTesting
    protected void callJobFinished(@NonNull final JobParameters params, final boolean reschedule) {
        jobFinished(params, reschedule);
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        final int jobId = params.getJobId();
        for (InterruptMaintenance im : sInterruptList) {
            if (im.getJobId() == jobId) {
                im.setInterrupted(true);
            }
        }
        return true;
    }

    /** Schedule regular maintenance job */
    static void schedule(Context context, IpMemoryStoreService ipMemoryStoreService) {
        final JobScheduler jobScheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        final ComponentName maintenanceJobName =
                new ComponentName(context, RegularMaintenanceJobService.class);

        // Regular maintenance is scheduled for when the device is idle with access power and a
        // minimum interval of one day.
        final JobInfo regularMaintenanceJob =
                new JobInfo.Builder(REGULAR_MAINTENANCE_ID, maintenanceJobName)
                        .setRequiresDeviceIdle(true)
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .setPeriodic(TimeUnit.HOURS.toMillis(24)).build();

        jobScheduler.schedule(regularMaintenanceJob);
        sIpMemoryStoreService = ipMemoryStoreService;
    }

    /** Unschedule regular maintenance job */
    static void unschedule(Context context) {
        final JobScheduler jobScheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(REGULAR_MAINTENANCE_ID);
        sIpMemoryStoreService = null;
    }
}
