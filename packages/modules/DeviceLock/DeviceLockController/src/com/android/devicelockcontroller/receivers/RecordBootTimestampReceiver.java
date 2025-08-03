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

package com.android.devicelockcontroller.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.os.UserManager;

import com.android.devicelockcontroller.storage.UserParameters;

import java.time.Clock;
import java.time.Instant;

/**
 * A receiver to record the boot timestamp.
 */
public final class RecordBootTimestampReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals(Intent.ACTION_LOCKED_BOOT_COMPLETED)) {
            // Ignore other intent actions
            return;
        }
        if (context.getSystemService(UserManager.class).isProfile()) {
            // Not needed as the receiver will run in the parent user
            return;
        }
        Instant bootTimeStamp = Instant.now(Clock.systemUTC()).minusMillis(
                SystemClock.elapsedRealtime());
        UserParameters.setBootTimeMillis(context, bootTimeStamp.toEpochMilli());
    }
}
