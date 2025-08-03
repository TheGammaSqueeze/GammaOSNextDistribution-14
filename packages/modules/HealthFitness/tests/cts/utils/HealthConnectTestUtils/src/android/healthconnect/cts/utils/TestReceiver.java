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

package android.healthconnect.cts.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Receives responses from test apps, e.g. from {@link
 * android.healthconnect.test.app.TestAppReceiver}.
 */
public class TestReceiver extends BroadcastReceiver {

    private static final String ACTION_SUCCESS = "action.SUCCESS";
    private static final String ACTION_ERROR = "action.ERROR";
    private static final String EXTRA_ERROR_CODE = "extra.ERROR_CODE";
    private static final String EXTRA_ERROR_MESSAGE = "extra.ERROR_MESSAGE";

    private static CountDownLatch sLatch = new CountDownLatch(1);
    private static Bundle sResult = null;
    private static Integer sErrorCode = null;
    private static String sErrorMessage = null;

    public static Bundle getResult() {
        await();
        return sResult;
    }

    public static Integer getErrorCode() {
        await();
        return sErrorCode;
    }

    public static String getErrorMessage() {
        await();
        return sErrorMessage;
    }

    private static void await() {
        try {
            if (!sLatch.await(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timeout waiting for response");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void reset() {
        sResult = null;
        sErrorCode = null;
        sErrorMessage = null;
        sLatch = new CountDownLatch(1);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case ACTION_SUCCESS:
                sResult = intent.getExtras();
                sLatch.countDown();
                break;
            case ACTION_ERROR:
                sErrorCode = intent.getIntExtra(EXTRA_ERROR_CODE, 0);
                sErrorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE);
                sLatch.countDown();
                break;
            default:
                throw new IllegalStateException("Unsupported action: " + intent.getAction());
        }
    }
}
