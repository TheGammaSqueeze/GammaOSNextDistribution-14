/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.app.fgsstarttesthelper;

import static android.app.fgsstarttesthelper.FgsTestCommon.ACTION_START_NEW_LOGIC_TEST;
import static android.app.fgsstarttesthelper.FgsTestCommon.EXTRA_REPLY_INTENT;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

public class FgsNewLogicTest extends BroadcastReceiver {
    static final String TAG = "FgsNewLogicTest";

    private static final int JOB_ID = 123454;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive: " + intent + " extras=" + intent.getExtras());
        if (ACTION_START_NEW_LOGIC_TEST.equals(intent.getAction())) {
            runTest(context, intent.getParcelableExtra(EXTRA_REPLY_INTENT, Intent.class));
        }
    }

    private void runTest(Context context, Intent replyIntent) {
        Log.i(TAG, "runTest: replyIntent=" + replyIntent);

        try {
            final var extras = new Bundle();
            extras.putParcelable(EXTRA_REPLY_INTENT, replyIntent);

            final var job = new JobInfo.Builder(JOB_ID, new ComponentName(context, Fgs.class))
                    .setExpedited(true)
                    .setTransientExtras(extras)
                    .build();

            final var result = context.getSystemService(JobScheduler.class).schedule(job);
            if (result != JobScheduler.RESULT_SUCCESS) {
                throw new RuntimeException("JobScheduler.schedule() returned " + result);
            }

            Log.i(TAG, "runTest done");
        } catch (Throwable e) {
            Log.i(TAG, "runTest failed", e);
            var reply = new FgsNewLogicMessage();
            reply.setFgsStarted(false);
            reply.setUnexpectedErrorMessage("Caught unexpected exception: " + e);

            Log.w(TAG, "Sending back reply intent with message " + reply);
            reply.setToIntent(replyIntent);
            context.sendBroadcast(replyIntent);
        }
    }

    public static class Fgs extends JobService {
        public static final AtomicInteger sJobStartCount = new AtomicInteger(0);

        @Override
        public boolean onStartJob(JobParameters params) {
            final var replyIntent = params.getTransientExtras().getParcelable(
                    EXTRA_REPLY_INTENT, Intent.class);
            Log.i(TAG, "onStartJob: replyIntent=" + replyIntent);
            sJobStartCount.incrementAndGet();

            var reply = new FgsNewLogicMessage();
            reply.setFgsStarted(false);
            reply.setUnexpectedErrorMessage("Unexpected error");

            try {
                startForeground(1, FgsTestCommon.createNotification(this));

                reply.setFgsStarted(true);
                reply.setUnexpectedErrorMessage(null);

                Log.w(TAG, "FGS started ");
            } catch (ForegroundServiceStartNotAllowedException e) {
                Log.w(TAG, "Received ForegroundServiceStartNotAllowedException", e);
                reply.setFgsStarted(false);
                reply.setUnexpectedErrorMessage(null);
            } catch (Throwable e) {
                Log.w(TAG, "startForeground failed", e);
                reply.setFgsStarted(false);
                reply.setUnexpectedErrorMessage("Caught unexpected exception: " + e);
            }

            reply.setToIntent(replyIntent);

            Log.w(TAG, "Sending back reply intent with message " + reply);
            sendBroadcast(replyIntent);

            return false;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            return false;
        }
    }
}
