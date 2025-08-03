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
package com.android.cts.verifier.managedprovisioning;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

/**
 * A {@link BroadcastReceiver} used to receive just one intent - it just logs the intent and
 * unregisters itself.
 *
 * <p>Useful for debugging purposes.
 */
public final class LogAndSelfUnregisterBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = LogAndSelfUnregisterBroadcastReceiver.class.getSimpleName();

    /**
     * Registers the broadcast to receive the given intent.
     */
    public static void register(Context context, String action) {
        context.getApplicationContext().registerReceiver(
                new LogAndSelfUnregisterBroadcastReceiver(),
                new IntentFilter(action),
                Context.RECEIVER_EXPORTED);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive(): " + intent);
        context.getApplicationContext().unregisterReceiver(this);
    }
}
