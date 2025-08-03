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

package com.android.cts.install.lib;

import static android.app.PendingIntent.FLAG_MUTABLE;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Helper for making IntentSenders whose results are sent back to the test
 * app.
 */
public class LocalIntentSender extends BroadcastReceiver {
    private static final String TAG = "cts.install.lib";
    private final BlockingQueue<Intent> mResults = new LinkedBlockingQueue<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Received intent " + prettyPrint(intent));
        mResults.add(intent);
    }

    /**
     * Get a LocalIntentSender.
     */
    public IntentSender getIntentSender() {
        Context context = InstrumentationRegistry.getTargetContext();
        // Generate a unique string to ensure each LocalIntentSender gets its own results.
        String action = LocalIntentSender.class.getName() + SystemClock.elapsedRealtime();
        context.registerReceiver(this, new IntentFilter(action),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        Intent intent = new Intent(action).setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent, FLAG_MUTABLE);
        return pending.getIntentSender();
    }

    /**
     * Returns and remove the most early Intent received by this LocalIntentSender.
     */
    public Intent getResult() throws InterruptedException {
        Intent intent = mResults.take();
        Log.i(TAG, "Taking intent " + prettyPrint(intent));
        return intent;
    }

    /**
     * Similar to {@link #getResult()}, but with a timeout.
     */
    public Intent pollResult(long timeout, TimeUnit unit) throws InterruptedException {
        return mResults.poll(timeout, unit);
    }

    private static String prettyPrint(Intent intent) {
        int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        return String.format("%s: {\n"
                + "sessionId = %d\n"
                + "status = %d\n"
                + "message = %s\n"
                + "}", intent, sessionId, status, message);
    }
}
