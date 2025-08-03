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

package android.app.sdksandbox.testutils;

import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class WaitableCountDownLatch {

    private final CountDownLatch mLatch;
    private final int mWaitTimeSec;

    public WaitableCountDownLatch(int waitTimeSec) {
        mLatch = new CountDownLatch(1);
        mWaitTimeSec = waitTimeSec;
    }

    public void countDown() {
        mLatch.countDown();
    }

    public void waitForLatch() {
        waitForLatch(/* message= */ null);
    }

    public void waitForLatch(@Nullable String message) {
        if (message == null) {
            message = "Latch";
        }
        try {
            // Wait for callback to be called
            if (!mLatch.await(mWaitTimeSec, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        message + " timed out after " + mWaitTimeSec + " seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for latch " + e.getMessage());
        }
    }
}
