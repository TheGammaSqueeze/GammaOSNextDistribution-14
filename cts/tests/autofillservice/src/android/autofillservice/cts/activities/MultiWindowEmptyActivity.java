/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.autofillservice.cts.activities;

import android.autofillservice.cts.testcore.Timeouts;
import android.util.Log;

import com.android.compatibility.common.util.RetryableException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Empty activity that allows to be put in different split window.
 */
public class MultiWindowEmptyActivity extends EmptyActivity {

    private static final String TAG = "MultiWindowEmptyActivity";
    private static MultiWindowEmptyActivity sLastInstance;
    private static CountDownLatch sLastInstanceLatch = new CountDownLatch(1);
    private static CountDownLatch sDestroyLastInstanceLatch;

    @Override
    protected void onStart() {
        super.onStart();
        sLastInstance = this;
        if (sLastInstanceLatch != null) {
            sLastInstanceLatch.countDown();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sLastInstance = null;
        sLastInstanceLatch = new CountDownLatch(1);

        if (sDestroyLastInstanceLatch != null) {
            sDestroyLastInstanceLatch.countDown();
        }
    }

    public static MultiWindowEmptyActivity getInstance() throws InterruptedException {
        if (!sLastInstanceLatch.await(Timeouts.ACTIVITY_RESURRECTION.getMaxValue(),
                TimeUnit.MILLISECONDS)) {
            throw new RetryableException(
                    "New MultiWindowEmptyActivity didn't start", Timeouts.ACTIVITY_RESURRECTION);
        }
        sLastInstanceLatch = null;
        return sLastInstance;
    }

    public static void finishAndWaitDestroy() {
        if (sLastInstance != null) {
            sLastInstance.finish();

            sDestroyLastInstanceLatch = new CountDownLatch(1);
            try {
                sDestroyLastInstanceLatch.await(Timeouts.ACTIVITY_RESURRECTION.getMaxValue(),
                        TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "interrupted waiting for MultiWindowEmptyActivity to be destroyed");
                Thread.currentThread().interrupt();
            }
            sDestroyLastInstanceLatch = null;
            sLastInstance = null;
        }
    }
}
