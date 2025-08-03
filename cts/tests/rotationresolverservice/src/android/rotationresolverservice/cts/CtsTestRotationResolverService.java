/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.rotationresolverservice.cts;

import android.os.CancellationSignal;
import android.os.CancellationSignal.OnCancelListener;
import android.service.rotationresolver.RotationResolutionRequest;
import android.service.rotationresolver.RotationResolverService;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class CtsTestRotationResolverService extends RotationResolverService {
    private static final String TAG = "CtsTestRotationResolverService";

    private static RotationResolverCallback sCurrentCallback;
    private static CountDownLatch sRespondLatch = new CountDownLatch(1);

    @Override
    public void onResolveRotation(RotationResolutionRequest request,
            CancellationSignal cancellationSignal,
            RotationResolverCallback callback) {
        sCurrentCallback = callback;
        sRespondLatch.countDown();

        cancellationSignal.setOnCancelListener(new OnCancelListener() {
            public void onCancel() {
                callback.onFailure(RotationResolverService.ROTATION_RESULT_FAILURE_CANCELLED);
                reset();
                sRespondLatch.countDown();
            }
        });
    }

    public static void reset() {
        sCurrentCallback = null;
    }

    public static void respondSuccess(int code) {
        if (sCurrentCallback != null) {
            sCurrentCallback.onSuccess(code);
        }
        reset();
    }

    public static void respondFailure(int code) {
        if (sCurrentCallback != null) {
            sCurrentCallback.onFailure(code);
        }
        reset();
    }

    public static boolean hasPendingChecks() {
        return sCurrentCallback != null;
    }

    public static void onReceivedResponse() {
        try {
            if (!sRespondLatch.await(3000, TimeUnit.MILLISECONDS)) {
                throw new AssertionError(
                        "CtsTestRotationResolverService timed out while expecting a call.");
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