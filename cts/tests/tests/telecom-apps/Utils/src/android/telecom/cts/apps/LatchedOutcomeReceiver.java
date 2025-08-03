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
import android.telecom.CallException;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.CountDownLatch;

/**
 * LatchedOutcomeReceiver is an implementation of {@link OutcomeReceiver}.  It allows a client to
 * wrap an OutcomeReceiver with a CountDownLatch so that the client can ensure the outcome was
 * completed. Be aware that the client should define which outcome they're expecting and assert
 * appropriately via the wasSuccessful method.
 */
public class LatchedOutcomeReceiver implements OutcomeReceiver<Void, CallException> {
    private static final String TAG = LatchedOutcomeReceiver.class.getSimpleName();
    private final CountDownLatch mCountDownLatch;
    private boolean mWasSuccessful = false;
    private CallException mCallException = null;

    public boolean wasSuccessful() {
        return mWasSuccessful;
    }

    public CallException getmCallException() {
        return mCallException;
    }

    public LatchedOutcomeReceiver(CountDownLatch latch) {
        mCountDownLatch = latch;
    }

    @Override
    public void onResult(Void result) {
        Log.i(TAG, "onResult: latch is counting down");
        mWasSuccessful = true;
        mCountDownLatch.countDown();
    }

    @Override
    public void onError(@NonNull CallException error) {
        Log.i(TAG, String.format("onError: code=[%s]", error));
        mCallException = error;
        mCountDownLatch.countDown();
    }
}
