/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.wearable.cts;

import android.app.wearable.WearableSensingDataRequest;
import android.app.wearable.WearableSensingManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** A broadcast receiver to receive wearable sensing data requests in CTS. */
public final class CtsWearableSensingDataRequestBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "CtsWearableSensingDataRequestBroadcastReceiver";
    private static WearableSensingDataRequest sLatestDataRequest;
    private static CountDownLatch sRespondLatch = new CountDownLatch(1);

    @Override
    public void onReceive(Context context, Intent intent) {
        sLatestDataRequest = WearableSensingManager.getDataRequestFromIntent(intent);
        sRespondLatch.countDown();
    }

    /** Resets all states. */
    public static void reset() {
        sLatestDataRequest = null;
        sRespondLatch = new CountDownLatch(1);
    }

    /** Gets the last data request received. */
    public static WearableSensingDataRequest getLatestDataRequest() {
        return sLatestDataRequest;
    }

    /**
     * Sets the number of results {@link #awaitResult()} will wait before returning. If not set, it
     * defaults to 1.
     */
    public static void setResultCountToAwait(int count) {
        sRespondLatch = new CountDownLatch(count);
    }

    /** Waits for a result to arrive. */
    public static void awaitResult() {
        try {
            if (!sRespondLatch.await(3, TimeUnit.SECONDS)) {
                throw new AssertionError(
                        "CtsWearableSensingDataRequestBroadcastReceiver"
                                + " timed out while expecting a call.");
            }
        } catch (InterruptedException ex) {
            Log.e(TAG, "Interrupted in test.", ex);
            Thread.currentThread().interrupt();
            throw new AssertionError("Got InterruptedException while waiting for result.");
        }
        // reset for next
        sRespondLatch = new CountDownLatch(1);
    }
}
