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

package android.adservices.utils;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/** A simple activity to launch to make the CTS be considered a foreground app by PPAPI. */
public class SimpleActivityBase extends Activity {
    private static final String EXTRA_FINISH_FLAG = "finish";
    private static final String ACTION_SIMPLE_ACTIVITY_START_RESULT =
            "android.adservices.utils.SimpleActivity.RESULT";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        Intent reply = new Intent(ACTION_SIMPLE_ACTIVITY_START_RESULT);
        reply.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        sendBroadcast(reply);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getExtras().getBoolean(EXTRA_FINISH_FLAG)) {
            finish();
        }
    }

    /** @return an intent targeting a subclass of {@link SimpleActivityBase} */
    protected static <T extends SimpleActivityBase> Intent getSimpleActivityIntent(
            Class<T> activityClass) {
        return new Intent(Intent.ACTION_MAIN)
                .setClassName(activityClass.getPackageName(), activityClass.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /**
     * Stops a subclass of {@link SimpleActivityBase} single activity, doesn't wait for the activity
     * to stop or check if the activity was actually running.
     */
    protected static <T extends SimpleActivityBase> void stop(
            Class<T> activityClass, Context targetContext) {
        targetContext.startActivity(
                getSimpleActivityIntent(activityClass)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(EXTRA_FINISH_FLAG, true));
    }

    /**
     * Starts a subclass of {@link SimpleActivityBase} and wait for the activity to be started the
     * specified max waiting time.
     *
     * @param targetContext the context to start the activity in
     * @param maxWaitTime the max waiting time
     * @throws TimeoutException if the activity didn't start within timeout
     */
    protected static <T extends SimpleActivityBase> void startAndWait(
            Class<T> activityClass, Context targetContext, Duration maxWaitTime)
            throws TimeoutException {
        try (WaitForBroadcast waiter = new WaitForBroadcast(targetContext)) {
            waiter.prepare(ACTION_SIMPLE_ACTIVITY_START_RESULT);
            targetContext.startActivity(getSimpleActivityIntent(activityClass));
            waiter.doWait(maxWaitTime.toMillis());
        }
    }

    /** See {@code android.app.cts.android.app.cts.tools.WaitForBroadcast} */
    private static class WaitForBroadcast implements AutoCloseable {
        @NonNull private final Context mContext;

        String mWaitingAction;
        boolean mHasResult;
        Intent mReceivedIntent;
        private final Object mWaitMonitor = new Object();

        final BroadcastReceiver mReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        synchronized (mWaitMonitor) {
                            mReceivedIntent = intent;
                            mHasResult = true;
                            mWaitMonitor.notifyAll();
                        }
                    }
                };

        WaitForBroadcast(@NonNull Context context) {
            mContext = context;
        }

        public void prepare(String action) {
            if (mWaitingAction != null) {
                throw new IllegalStateException("Already prepared");
            }
            mWaitingAction = action;
            IntentFilter filter = new IntentFilter();
            filter.addAction(action);
            mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
        }

        public Intent doWait(long timeoutMillis) throws TimeoutException {
            final long endTime = SystemClock.uptimeMillis() + timeoutMillis;

            synchronized (this) {
                while (!mHasResult) {
                    final long now = SystemClock.uptimeMillis();
                    if (now >= endTime) {
                        String action = mWaitingAction;
                        throw new TimeoutException("Timed out waiting for broadcast " + action);
                    }
                    try {
                        wait(endTime - now);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return mReceivedIntent;
            }
        }

        @Override
        public void close() {
            if (mWaitingAction != null) {
                mContext.unregisterReceiver(mReceiver);
                mWaitingAction = null;
            }
        }
    }
}
