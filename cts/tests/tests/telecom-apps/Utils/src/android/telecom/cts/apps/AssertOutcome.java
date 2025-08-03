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

import android.util.Log;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This class should contain all CountDown latch helpers.
 */
public class AssertOutcome {
    private static final String TAG = AssertOutcome.class.getSimpleName();
    private static final int DEFAULT_TIMEOUT = 5000;

    public static void assertCountDownLatchWasCalled(String packageName,
            List<String> stackTrace,
            String message,
            CountDownLatch latch) throws TestAppException {
        boolean success = assertCountDownWasCalled(latch);
        if (!success) {
            throw new TestAppException(packageName, stackTrace, message);
        }
    }

    private static boolean assertCountDownWasCalled(CountDownLatch latch) {
        Log.i(TAG, "assertOnResultWasReceived: waiting for latch");
        try {
            return latch.await(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            return false;
        }
    }
}
