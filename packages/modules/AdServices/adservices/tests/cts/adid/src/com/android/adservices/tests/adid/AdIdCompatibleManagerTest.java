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
package com.android.adservices.tests.adid;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdCompatibleManager;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.os.LimitExceededException;

import androidx.test.filters.FlakyTest;

import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.common.RequiresLowRamDevice;
import com.android.adservices.shared.common.ServiceUnavailableException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class AdIdCompatibleManagerTest extends CtsAdIdEndToEndTestCase {

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    @Before
    public void setup() throws Exception {
        // Cool-off rate limiter in case it was initialized by another test
        TimeUnit.SECONDS.sleep(1);
    }

    @Test
    public void testAdIdCompatibleManager() throws Exception {
        AdIdCompatibleManager adIdCompatibleManager = new AdIdCompatibleManager(sContext);
        CompletableFuture<AdId> future = new CompletableFuture<>();
        AdServicesOutcomeReceiver<AdId, Exception> callback =
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(AdId result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(Exception error) {
                        Assert.fail();
                    }
                };
        adIdCompatibleManager.getAdId(CALLBACK_EXECUTOR, callback);
        AdId resultAdId = future.get();
        assertThat(resultAdId.getAdId()).isNotNull();
        assertThat(resultAdId.isLimitAdTrackingEnabled()).isFalse();
    }

    @Test
    @FlakyTest(bugId = 322812739)
    public void testAdIdCompatibleManager_verifyRateLimitReached() throws Exception {
        AdIdCompatibleManager adIdCompatibleManager = new AdIdCompatibleManager(sContext);
        AdServicesOutcomeReceiverForTests<AdId> callback;

        // Rate limit hasn't reached yet
        long nowInMillis = System.currentTimeMillis();
        float requestPerSecond = flags.getAdIdRequestPerSecond();
        for (int i = 0; i < requestPerSecond; i++) {
            callback = new AdServicesOutcomeReceiverForTests<>();
            adIdCompatibleManager.getAdId(CALLBACK_EXECUTOR, callback);
            callback.assertSuccess();
        }

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        callback = new AdServicesOutcomeReceiverForTests<>();
        adIdCompatibleManager.getAdId(CALLBACK_EXECUTOR, callback);

        // Verify limit reached
        // If the test takes less than 1 second / permits per second, this test is reliable due to
        // the rate limiter limits queries per second. If duration is longer than a second, skip it.
        callback = new AdServicesOutcomeReceiverForTests<>();
        adIdCompatibleManager.getAdId(CALLBACK_EXECUTOR, callback);
        boolean executedInLessThanOneSec =
                (System.currentTimeMillis() - nowInMillis) < (1_000 / requestPerSecond);
        if (executedInLessThanOneSec) {
            callback.assertFailure(LimitExceededException.class);
        }
    }

    @Test
    @RequiresLowRamDevice
    public void testAdIdCompatibleManagerTest_whenDeviceNotSupported() throws Exception {
        AdIdCompatibleManager adIdCompatibleManager = new AdIdCompatibleManager(sContext);
        AdServicesOutcomeReceiverForTests<AdId> callback =
                new AdServicesOutcomeReceiverForTests<>();

        adIdCompatibleManager.getAdId(CALLBACK_EXECUTOR, callback);

        callback.assertFailure(ServiceUnavailableException.class);
    }
}
