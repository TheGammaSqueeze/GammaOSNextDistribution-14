/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package android.attentionservice.cts;

import android.service.attention.AttentionService;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CtsTestAttentionService extends AttentionService {
    private static final String TAG = "CtsTestAttentionService";
    private static AttentionCallback sCurrentAttentionCallback;
    private static ProximityUpdateCallback sCurrentProximityUpdateCallback;
    private static  CountDownLatch sRespondLatch = new CountDownLatch(1);

    @Override
    public void onCheckAttention(AttentionCallback callback) {
        sCurrentAttentionCallback = callback;
        sRespondLatch.countDown();
    }

    @Override
    public void onCancelAttentionCheck(AttentionCallback callback) {
        callback.onFailure(ATTENTION_FAILURE_CANCELLED);
        resetAttentionCheck();
        sRespondLatch.countDown();
    }

    @Override
    public void onStartProximityUpdates(ProximityUpdateCallback callback) {
        sCurrentProximityUpdateCallback = callback;
        sRespondLatch.countDown();
    }

    @Override
    public void onStopProximityUpdates() {
        resetProximity();
        sRespondLatch.countDown();
    }

    public static void reset() {
        resetAttentionCheck();
        resetProximity();
    }

    public static void resetAttentionCheck() {
        sCurrentAttentionCallback = null;
    }

    public static void resetProximity() {
        sCurrentProximityUpdateCallback = null;
    }

    public static void respondSuccess(int code) {
        if (sCurrentAttentionCallback != null) {
            sCurrentAttentionCallback.onSuccess(code, 0);
        }
        resetAttentionCheck();
    }

    public static void respondFailure(int code) {
        if (sCurrentAttentionCallback != null) {
            sCurrentAttentionCallback.onFailure(code);
        }
        resetAttentionCheck();
    }

    public static void respondProximity(double distance) {
        if (sCurrentProximityUpdateCallback != null) {
            sCurrentProximityUpdateCallback.onProximityUpdate(distance);
        }
    }

    public static boolean hasPendingChecks() {
        return sCurrentAttentionCallback != null;
    }

    public static boolean hasCurrentProximityUpdates() {
        return sCurrentProximityUpdateCallback != null;
    }

    public static void onReceivedResponse() {
        try {
            if (!sRespondLatch.await(500, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("CtsTestAttentionService timed out while expecting a call.");
            }
            //reset for next
            sRespondLatch = new CountDownLatch(1);
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage());
            Thread.currentThread().interrupt();
            throw new AssertionError("Got InterruptedException while waiting for response.");
        }
    }
}
