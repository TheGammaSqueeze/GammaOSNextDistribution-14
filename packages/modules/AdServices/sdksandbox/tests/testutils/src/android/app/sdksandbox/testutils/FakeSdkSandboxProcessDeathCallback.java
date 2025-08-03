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

import android.app.sdksandbox.SdkSandboxManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FakeSdkSandboxProcessDeathCallback
        implements SdkSandboxManager.SdkSandboxProcessDeathCallback {
    private CountDownLatch mLatch = new CountDownLatch(1);

    @Override
    public void onSdkSandboxDied() {
        mLatch.countDown();
    }

    /**
     * Returns {@code true} if the sandbox death callback is invoked within 5 seconds, {@code false}
     * otherwise.
     */
    public boolean waitForSandboxDeath() throws InterruptedException {
        return mLatch.await(5, TimeUnit.SECONDS);
    }

    /**
     * Resets the latch used to wait for death callbacks, to enable testing multiple death events
     * for the same callback object.
     */
    public void resetLatch() {
        mLatch = new CountDownLatch(1);
    }
}
