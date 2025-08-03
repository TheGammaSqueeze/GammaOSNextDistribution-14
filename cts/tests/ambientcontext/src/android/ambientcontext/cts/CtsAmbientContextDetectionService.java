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
package android.ambientcontext.cts;

import android.app.ambientcontext.AmbientContextEvent;
import android.app.ambientcontext.AmbientContextEventRequest;
import android.app.ambientcontext.AmbientContextManager;
import android.service.ambientcontext.AmbientContextDetectionResult;
import android.service.ambientcontext.AmbientContextDetectionService;
import android.service.ambientcontext.AmbientContextDetectionServiceStatus;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


public class CtsAmbientContextDetectionService extends AmbientContextDetectionService {
    private static final String TAG = "CtsAmbientContextDetectionService";
    private static final String FAKE_APP_PACKAGE = "foo.bar.baz";

    private static Consumer<AmbientContextDetectionResult> sResultConsumer;
    private static Consumer<AmbientContextDetectionServiceStatus> sQueryConsumer;
    private static CountDownLatch sRespondLatch = new CountDownLatch(1);

    @Override
    public void onStartDetection(AmbientContextEventRequest request, String packageName,
            Consumer<AmbientContextDetectionResult> resultConsumer,
            Consumer<AmbientContextDetectionServiceStatus> statusConsumer) {
        sResultConsumer = resultConsumer;
        sQueryConsumer = statusConsumer;
        sRespondLatch.countDown();
    }

    @Override
    public void onStopDetection(String packageName) {
        reset();
        sRespondLatch.countDown();
    }

    @Override
    public void onQueryServiceStatus(int[] eventTypes, String packageName,
            Consumer<AmbientContextDetectionServiceStatus> consumer) {
        sQueryConsumer = consumer;
        sRespondLatch.countDown();
    }

    public static void reset() {
        Log.d(TAG, "reset");
        sResultConsumer = null;
        sQueryConsumer = null;
    }

    public static void respondSuccess(AmbientContextEvent event) {
        if (sResultConsumer != null) {
            AmbientContextDetectionResult result = new AmbientContextDetectionResult
                    .Builder(FAKE_APP_PACKAGE)
                    .addEvent(event)
                    .build();
            sResultConsumer.accept(result);
        }
        if (sQueryConsumer != null) {
            AmbientContextDetectionServiceStatus serviceStatus =
                    new AmbientContextDetectionServiceStatus.Builder(FAKE_APP_PACKAGE)
                            .setStatusCode(AmbientContextManager.STATUS_SUCCESS)
                            .build();
            sQueryConsumer.accept(serviceStatus);
        }
    }

    public static void respondFailure(int status) {
        if (sQueryConsumer != null) {
            AmbientContextDetectionServiceStatus serviceStatus =
                    new AmbientContextDetectionServiceStatus.Builder(FAKE_APP_PACKAGE)
                            .setStatusCode(status)
                            .build();
            sQueryConsumer.accept(serviceStatus);
        }
    }

    public static boolean hasPendingRequest() {
        return sResultConsumer != null;
    }

    public static boolean hasQueryRequest() {
        return sQueryConsumer != null;
    }

    public static void onReceivedResponse() {
        try {
            if (!sRespondLatch.await(3000, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("CtsTestAmbientContextEventProviderService"
                        + " timed out while expecting a call.");
            }
            // reset for next
            sRespondLatch = new CountDownLatch(1);
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage());
            Thread.currentThread().interrupt();
            throw new AssertionError("Got InterruptedException while waiting for serviceStatus.");
        }
    }
}
