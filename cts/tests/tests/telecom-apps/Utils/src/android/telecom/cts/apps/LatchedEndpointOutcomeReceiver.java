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

package android.telecom.cts.apps;

import android.os.OutcomeReceiver;
import android.telecom.CallEndpointException;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.CountDownLatch;

public class LatchedEndpointOutcomeReceiver implements
        OutcomeReceiver<Void, CallEndpointException> {
    private static final String TAG = LatchedEndpointOutcomeReceiver.class.getSimpleName();
    CountDownLatch mCountDownLatch;

    public LatchedEndpointOutcomeReceiver(CountDownLatch latch) {
        mCountDownLatch = latch;
    }

    @Override
    public void onResult(Void result) {
        Log.i(TAG, "onResult: latch is counting down");
        mCountDownLatch.countDown();
    }

    @Override
    public void onError(@NonNull CallEndpointException error) {
        Log.i(TAG, String.format("onError: code=[%s]", error));
    }
}
