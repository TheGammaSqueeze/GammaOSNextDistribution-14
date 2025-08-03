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

package android.app.cts;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;

import java.util.concurrent.CountDownLatch;

/**
 * The helper class to start an instrumentation from a different process.
 */
public class InstrumentationHelperService extends Service {
    private static final String ACTION_START_INSTRUMENTATION =
            "android.app.cts.ACTION_START_INSTRUMENTATION";
    private static final String EXTRA_INSTRUMENTATIION_NAME = "instrumentation_name";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent.getAction();
        if (ACTION_START_INSTRUMENTATION.equals(action)) {
            final String instrumentationName = intent.getStringExtra(EXTRA_INSTRUMENTATIION_NAME);
            final ResultReceiver r = intent.getParcelableExtra(Intent.EXTRA_RESULT_RECEIVER,
                    ResultReceiver.class);

            boolean result = false;
            try {
                startInstrumentation(
                        ComponentName.unflattenFromString(instrumentationName), null, null);
                result = true;
            } catch (SecurityException e) {
            }
            r.send(result ? 1 : 0, null);
        }
        return START_NOT_STICKY;
    }

    /**
     * Start the given instrumentation from this service and return result.
     */
    static boolean startInstrumentation(Context context, String instrumentationName)
            throws InterruptedException {
        final Intent intent = new Intent(ACTION_START_INSTRUMENTATION);
        final boolean[] resultHolder = new boolean[1];
        final CountDownLatch latch = new CountDownLatch(1);
        final ResultReceiver r = new ResultReceiver(null) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                resultHolder[0] = resultCode == 1;
                latch.countDown();
            }
        };
        intent.putExtra(EXTRA_INSTRUMENTATIION_NAME, instrumentationName);
        intent.putExtra(Intent.EXTRA_RESULT_RECEIVER, r);
        intent.setClassName("android.app.cts", "android.app.cts.InstrumentationHelperService");
        context.startService(intent);
        latch.await();
        return resultHolder[0];
    }
}
