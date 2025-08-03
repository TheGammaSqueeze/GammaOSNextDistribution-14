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

package android.telecom.cts.streamingtestapp;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CtsCallStreamingServiceControl extends Service {
    public static final String CONTROL_INTERFACE_ACTION =
            "android.telecom.cts.streamingtestapp.ACTION_CONTROL";

    private static final String TAG = "CtsCallStreamingServiceControl";
    private static final long TIMEOUT_MILLIS = 5000L;

    private final IBinder mCtsCallStreamingServiceControl =
            new ICtsCallStreamingServiceControl.Stub() {

        @Override
        public Bundle waitForCallAdded() {
            Log.i(TAG, "waitForCallAdded: enter");
            try {
                CtsCallStreamingService.sCallStreamingStartedLatch.await(TIMEOUT_MILLIS,
                        TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Bundle bundle = new Bundle();
                bundle.putString(CtsCallStreamingService.EXTRA_FAILED, "interrupted");
                return bundle;
            }
            Log.i(TAG, "waitForCallAdded: exit");
            return CtsCallStreamingService.sCallBundle;
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (CONTROL_INTERFACE_ACTION.equals(intent.getAction())) {
            Log.i(TAG, "onBind: return control interface.");
            CtsCallStreamingService.sCallStreamingStartedLatch = new CountDownLatch(1);
            return mCtsCallStreamingServiceControl;
        }
        Log.w(TAG, "onBind: invalid intent.");
        return null;
    }
}
