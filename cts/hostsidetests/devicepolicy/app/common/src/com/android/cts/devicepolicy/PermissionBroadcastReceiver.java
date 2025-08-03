/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.devicepolicy;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public final class PermissionBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "PermissionBroadcastReceiver";

    private static final String EXTRA_GRANT_STATE
            = "com.android.cts.permission.extra.GRANT_STATE";
    private static final int PERMISSION_ERROR = -2;

    private static final long TIMEOUT_SEC = 30;

    private final BlockingQueue<Integer> mResultsQueue = new ArrayBlockingQueue<>(1);

    @Override
    public void onReceive(Context context, Intent intent) {
        Integer result = intent.getIntExtra(EXTRA_GRANT_STATE, PERMISSION_ERROR);
        Log.d(TAG, "Grant state received " + result + " ("
                + PermissionUtils.permissionToString(result) + ")");
        assertWithMessage("added %s to queue", result).that(mResultsQueue.add(result)).isTrue();
    }

    public int waitForBroadcast() throws Exception {
        Integer result = mResultsQueue.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        mResultsQueue.clear();
        assertWithMessage("Expected broadcast to be received within %s seconds but did not get it",
                TIMEOUT_SEC).that(result).isNotNull();
        Log.d(TAG, "Grant state retrieved " + result + " ("
                + PermissionUtils.permissionToString(result) + ")");
        return result;
    }
}