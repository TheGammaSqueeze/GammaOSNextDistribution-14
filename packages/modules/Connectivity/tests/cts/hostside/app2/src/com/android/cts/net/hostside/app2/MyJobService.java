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
package com.android.cts.net.hostside.app2;

import static com.android.cts.net.hostside.app2.Common.ACTION_FINISH_JOB;
import static com.android.cts.net.hostside.app2.Common.TAG;
import static com.android.cts.net.hostside.app2.Common.TYPE_COMPONENT_EXPEDITED_JOB;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class MyJobService extends JobService {

    private BroadcastReceiver mFinishCommandReceiver = null;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "MyJobService.onCreate()");
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.v(TAG, "MyJobService.onStartJob()");
        Common.notifyNetworkStateObserver(this, params.getTransientExtras(),
                TYPE_COMPONENT_EXPEDITED_JOB);
        mFinishCommandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.v(TAG, "Finishing MyJobService");
                try {
                    jobFinished(params, /*wantsReschedule=*/ false);
                } finally {
                    if (mFinishCommandReceiver != null) {
                        unregisterReceiver(mFinishCommandReceiver);
                        mFinishCommandReceiver = null;
                    }
                }
            }
        };
        registerReceiver(mFinishCommandReceiver, new IntentFilter(ACTION_FINISH_JOB),
                Context.RECEIVER_EXPORTED);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // If this job is stopped before it had a chance to send network status via
        // INetworkStateObserver, the test will fail. It could happen either due to test timing out
        // or this app moving to a lower proc_state and losing network access.
        Log.v(TAG, "MyJobService.onStopJob()");
        if (mFinishCommandReceiver != null) {
            unregisterReceiver(mFinishCommandReceiver);
            mFinishCommandReceiver = null;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "MyJobService.onDestroy()");
    }
}
