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

import android.os.Bundle;
import android.telecom.CallStreamingService;
import android.telecom.StreamingCall;
import android.util.Log;

import java.util.concurrent.CountDownLatch;

public class CtsCallStreamingService extends CallStreamingService {
    private static final String LOG_TAG = "CtsCallStreamingService";
    public static final String EXTRA_CALL_EXTRAS = "android.telecom.cts.extra.CALL_EXTRAS";
    public static final String EXTRA_FAILED = "android.telecom.cts.extra.FAILED";
    public static Bundle sCallBundle = null;
    public static CountDownLatch sCallStreamingStartedLatch = new CountDownLatch(1);

    @Override
    public void onCallStreamingStarted(StreamingCall call) {
        Log.i(LOG_TAG, "onCallStreamingStarted: id=" + call.getExtras());
        sCallBundle = new Bundle();
        sCallBundle.putBundle(EXTRA_CALL_EXTRAS, call.getExtras());
        sCallStreamingStartedLatch.countDown();
    }
}
