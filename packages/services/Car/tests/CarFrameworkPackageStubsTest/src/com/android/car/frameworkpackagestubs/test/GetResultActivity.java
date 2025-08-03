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

package com.android.car.frameworkpackagestubs.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** An helper activity to help test cases to startActivityForResult() and pool the result. */
public final class GetResultActivity extends Activity {
    private static LinkedBlockingQueue<Result> sResult;

    public static class Result {
        public final int requestCode;
        public final int resultCode;
        public final Intent data;

        public Result(int requestCode, int resultCode, Intent data) {
            this.requestCode = requestCode;
            this.resultCode = resultCode;
            this.data = data;
        }
    }

    public static GetResultActivity startActivitySync(
            Context context, Instrumentation instrumentation) {
        Intent getActivityResultIntent = new Intent(context, GetResultActivity.class);
        getActivityResultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sResult = new LinkedBlockingQueue<>();
        return (GetResultActivity) instrumentation.startActivitySync(getActivityResultIntent);
    }

    public int poolResultCode() {
        Result result;
        try {
            result = sResult.poll(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (result == null) {
            throw new IllegalStateException("Activity didn't receive a Result in 30 seconds");
        }
        return result.resultCode;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            sResult.offer(new Result(requestCode, resultCode, data), 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
